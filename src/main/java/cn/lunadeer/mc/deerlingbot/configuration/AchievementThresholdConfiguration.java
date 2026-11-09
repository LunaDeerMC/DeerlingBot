package cn.lunadeer.mc.deerlingbot.configuration;

import cn.lunadeer.mc.deerlingbot.managers.AchievementThresholds;
import cn.lunadeer.mc.deerlingbot.managers.AchievementThresholds.Tiers;
import cn.lunadeer.mc.deerlingbot.utils.configuration.Comment;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationPart;
import org.bukkit.configuration.ConfigurationSection;

import java.math.BigInteger;
import java.util.List;

/** Editable YAML values. Runtime readers only use the separately published immutable snapshot. */
public final class AchievementThresholdConfiguration extends ConfigurationPart {
    @Comment("累计挖掘方块数")
    public Levels mining = new Levels(AchievementThresholds.defaults().mining(), 1);
    @Comment("累计合成产出数量")
    public Levels crafting = new Levels(AchievementThresholds.defaults().crafting(), 1);
    @Comment("鞘翅飞行距离，单位为整数公里 km，上限 21474")
    public Levels elytra = new Levels(AchievementThresholds.defaults().elytra(), AchievementThresholds.CENTIMETERS_PER_KILOMETER);
    @Comment("钓到鱼的累计次数，不含垃圾或宝藏")
    public Levels fishing = new Levels(AchievementThresholds.defaults().fishing(), 1);
    @Comment("村民交易累计次数")
    public Levels trading = new Levels(AchievementThresholds.defaults().trading(), 1);
    @Comment("普通步行距离，单位为整数公里 km，上限 21474；不含疾跑或其他移动方式")
    public Levels walking = new Levels(AchievementThresholds.defaults().walking(), AchievementThresholds.CENTIMETERS_PER_KILOMETER);

    public static final class Levels extends ConfigurationPart {
        public long wood;
        public long iron;
        public long copper;
        public long diamond;
        public long gold;

        private Levels(Tiers tiers, long unitScale) {
            wood = tiers.wood() / unitScale;
            iron = tiers.iron() / unitScale;
            copper = tiers.copper() / unitScale;
            diamond = tiers.diamond() / unitScale;
            gold = tiers.gold() / unitScale;
        }
    }

    private static final List<String> TIER_KEYS = List.of("wood", "iron", "copper", "diamond", "gold");

    /** Validate before the reflection loader can mutate any live configuration fields. */
    static AchievementThresholds read(ConfigurationSection yaml) {
        requireSection(yaml, "achievement-thresholds");
        AchievementThresholds defaults = AchievementThresholds.defaults();
        return new AchievementThresholds(
                readTiers(yaml, "mining", defaults.mining(), false, false),
                readTiers(yaml, "crafting", defaults.crafting(), false, false),
                readTiers(yaml, "elytra", defaults.elytra(), true, true),
                readTiers(yaml, "fishing", defaults.fishing(), false, true),
                readTiers(yaml, "trading", defaults.trading(), false, true),
                readTiers(yaml, "walking", defaults.walking(), true, true));
    }

    private static Tiers readTiers(ConfigurationSection yaml, String name, Tiers defaults,
                                    boolean distance, boolean singleVanillaCounter) {
        String key = "achievement-thresholds." + name;
        requireSection(yaml, key);
        long scale = distance ? AchievementThresholds.CENTIMETERS_PER_KILOMETER : 1;
        long[] values = defaults.values();
        for (int i = 0; i < values.length; i++) values[i] /= scale;
        if (isLegacy(yaml, key)) {
            for (String tier : TIER_KEYS.subList(0, 4)) {
                if (yaml.contains(key + "." + tier))
                    throw new InvalidThresholds(key + " 不能混用旧版 bronze/silver 和新版 wood/iron/copper/diamond；请完整填写五级配置");
            }
            Legacy defaultsBeforeFiveTiers = legacyDefaults(name);
            Legacy legacy = new Legacy(
                    integer(yaml, key + ".bronze", defaultsBeforeFiveTiers.bronze()),
                    integer(yaml, key + ".silver", defaultsBeforeFiveTiers.silver()),
                    integer(yaml, key + ".gold", defaultsBeforeFiveTiers.gold()));
            if (legacy.bronze() <= 0 || legacy.silver() <= legacy.bronze() || legacy.gold() <= legacy.silver())
                throw new InvalidThresholds(key + " 旧版门槛必须是正整数且满足 bronze < silver < gold");
            validateMaximum(key, legacy.gold(), distance, singleVanillaCounter);
            // Only known shipped defaults are replaced by newly calibrated defaults.
            // Preserve custom milestones as wood/copper/gold; insert the two extra tiers.
            if (!legacy.equals(defaultsBeforeFiveTiers)) {
                values = new long[]{legacy.bronze(), between(key, legacy.bronze(), legacy.silver()),
                        legacy.silver(), between(key, legacy.silver(), legacy.gold()), legacy.gold()};
            }
        } else {
            for (int i = 0; i < TIER_KEYS.size(); i++)
                values[i] = integer(yaml, key + "." + TIER_KEYS.get(i), values[i]);
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0 || (i > 0 && values[i] <= values[i - 1]))
                throw new InvalidThresholds(key + " 必须是正整数且满足 wood < iron < copper < diamond < gold");
        }
        validateMaximum(key, values[4], distance, singleVanillaCounter);
        return distance ? Tiers.kilometers(values[0], values[1], values[2], values[3], values[4])
                : new Tiers(values[0], values[1], values[2], values[3], values[4]);
    }

    private static void validateMaximum(String key, long gold, boolean distance, boolean singleVanillaCounter) {
        if (distance && gold > AchievementThresholds.MAX_DISTANCE_KILOMETERS)
            throw new InvalidThresholds(key + ".gold 不能超过原版统计可达到的 21474 km");
        if (singleVanillaCounter && !distance && gold > Integer.MAX_VALUE)
            throw new InvalidThresholds(key + ".gold 不能超过原版统计上限 " + Integer.MAX_VALUE);
    }

    private static boolean isLegacy(ConfigurationSection yaml, String key) {
        return yaml.contains(key + ".bronze") || yaml.contains(key + ".silver");
    }

    private record Legacy(long bronze, long silver, long gold) {}

    /** Published three-tier values, in YAML units, must stay fixed for upgrade detection. */
    private static Legacy legacyDefaults(String name) {
        return switch (name) {
            case "mining" -> new Legacy(500, 36_000, 680_000);
            case "crafting" -> new Legacy(300, 31_000, 1_200_000);
            case "elytra" -> new Legacy(8, 360, 5_100);
            case "fishing" -> new Legacy(5, 98, 1_100);
            case "trading" -> new Legacy(25, 390, 18_000);
            case "walking" -> new Legacy(5, 120, 1_500);
            default -> throw new IllegalArgumentException("Unknown achievement category: " + name);
        };
    }

    private static long between(String key, long lower, long upper) {
        if (upper - lower < 2)
            throw new InvalidThresholds(key + " 旧版相邻门槛间距不足，无法保留旧值并插入等级；请完整填写 wood/iron/copper/diamond/gold 五级配置");
        long geometricMean = BigInteger.valueOf(lower).multiply(BigInteger.valueOf(upper)).sqrt().longValueExact();
        return Math.max(lower + 1, Math.min(upper - 1, geometricMean));
    }

    /** Called only after all six categories validate; failed pre-validation never modifies YAML. */
    static void normalizeLegacyYaml(ConfigurationSection yaml, AchievementThresholds validated) {
        normalizeLegacyCategory(yaml, "mining", validated.mining(), 1);
        normalizeLegacyCategory(yaml, "crafting", validated.crafting(), 1);
        normalizeLegacyCategory(yaml, "elytra", validated.elytra(), AchievementThresholds.CENTIMETERS_PER_KILOMETER);
        normalizeLegacyCategory(yaml, "fishing", validated.fishing(), 1);
        normalizeLegacyCategory(yaml, "trading", validated.trading(), 1);
        normalizeLegacyCategory(yaml, "walking", validated.walking(), AchievementThresholds.CENTIMETERS_PER_KILOMETER);
    }

    private static void normalizeLegacyCategory(ConfigurationSection yaml, String name, Tiers tiers, long scale) {
        String key = "achievement-thresholds." + name;
        if (!isLegacy(yaml, key)) return;
        yaml.set(key + ".bronze", null);
        yaml.set(key + ".silver", null);
        yaml.set(key + ".gold", null);
        long[] values = tiers.values();
        for (int i = 0; i < TIER_KEYS.size(); i++) yaml.set(key + "." + TIER_KEYS.get(i), values[i] / scale);
        yaml.setComments("achievement-thresholds", List.of(
                "玩家信息图片的六类成就累计门槛；必须满足 0 < wood < iron < copper < diamond < gold（木、铁、铜、钻、金）",
                "elytra、walking 使用整数公里 km；其余使用累计数量或次数",
                "旧版默认值升级为五级默认值；旧版自定义 bronze/silver/gold 保留为 wood/copper/gold，另两级插入几何均值",
                "修改后使用 /deerling reload 生效；门槛调整会重新计算已有玩家的等级"));
    }

    private static void requireSection(ConfigurationSection yaml, String key) {
        Object raw = yaml.get(key);
        if (raw != null && !(raw instanceof ConfigurationSection))
            throw new InvalidThresholds(key + " 必须是配置段");
    }

    private static long integer(ConfigurationSection yaml, String key, long fallback) {
        Object raw = yaml.get(key);
        if (raw == null) return fallback;
        if (!(raw instanceof Integer || raw instanceof Long))
            throw new InvalidThresholds(key + " 必须是整数，不能使用小数或字符串");
        return ((Number) raw).longValue();
    }

    /** A pre-validation failure does not mutate live settings, so reload can safely be rejected. */
    public static final class InvalidThresholds extends IllegalArgumentException {
        public InvalidThresholds(String message) { super(message); }
    }
}
