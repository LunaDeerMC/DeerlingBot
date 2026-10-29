package cn.lunadeer.mc.deerlingbot.configuration;

import cn.lunadeer.mc.deerlingbot.managers.AchievementThresholds;
import cn.lunadeer.mc.deerlingbot.managers.AchievementThresholds.Tiers;
import cn.lunadeer.mc.deerlingbot.utils.configuration.Comment;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationPart;
import org.bukkit.configuration.ConfigurationSection;

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
        public long bronze;
        public long silver;
        public long gold;

        private Levels(Tiers tiers, long unitScale) {
            bronze = tiers.bronze() / unitScale;
            silver = tiers.silver() / unitScale;
            gold = tiers.gold() / unitScale;
        }
    }

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
        long bronze = integer(yaml, key + ".bronze", defaults.bronze() / scale);
        long silver = integer(yaml, key + ".silver", defaults.silver() / scale);
        long gold = integer(yaml, key + ".gold", defaults.gold() / scale);
        if (bronze <= 0 || silver <= bronze || gold <= silver)
            throw new InvalidThresholds(key + " 必须是正整数且满足 bronze < silver < gold");
        if (distance && gold > AchievementThresholds.MAX_DISTANCE_KILOMETERS)
            throw new InvalidThresholds(key + ".gold 不能超过原版统计可达到的 21474 km");
        if (singleVanillaCounter && !distance && gold > Integer.MAX_VALUE)
            throw new InvalidThresholds(key + ".gold 不能超过原版统计上限 " + Integer.MAX_VALUE);
        return distance ? Tiers.kilometers(bronze, silver, gold) : new Tiers(bronze, silver, gold);
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
