package cn.lunadeer.mc.deerlingbot.managers;

import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure data derived from the last saved vanilla statistics. No player, network or world access. */
public record PlayerCardProfile(long playedTicks, long walkedCentimeters, long minedBlocks, long craftedItems,
                                long fishCaught, long tradedWithVillager, long aviatedCentimeters,
                                List<Preference> preferences, List<Achievement> achievements, Goal goal, String quip) {
    public record Preference(String label, String value, String detail, String icon) { }
    /** Counts and thresholds share the same original unit; distances remain in centimetres. */
    public record Achievement(String title, String level, String detail, String icon,
                        long current, long lowerThreshold, long nextThreshold) {
        public Achievement {
            if (!List.of("未解锁", "铜", "银", "金").contains(level))
                throw new IllegalArgumentException("Unknown achievement level: " + level);
            if (current < 0 || lowerThreshold < 0 || nextThreshold < lowerThreshold
                    || (!"金".equals(level) && nextThreshold == lowerThreshold))
                throw new IllegalArgumentException("Invalid achievement progress thresholds");
        }

        public boolean locked() { return "未解锁".equals(level); }
        public boolean isMaxLevel() { return "金".equals(level); }

        /** Only complete ten-percent steps light a segment; never round up a partial step. */
        public int litSegments() {
            if (isMaxLevel()) return 10;
            return BigInteger.valueOf(progressWithinLevel()).multiply(BigInteger.TEN)
                    .divide(BigInteger.valueOf(nextThreshold - lowerThreshold)).intValueExact();
        }

        /** Truncate to two decimals, so a value below a boundary never displays the next boundary. */
        public String progressPercentText() {
            if (isMaxLevel()) return "100%";
            return BigDecimal.valueOf(progressWithinLevel()).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(nextThreshold - lowerThreshold), 2, RoundingMode.DOWN)
                    .stripTrailingZeros().toPlainString() + "%";
        }

        private long progressWithinLevel() {
            return Math.min(Math.max(0, current - lowerThreshold), nextThreshold - lowerThreshold);
        }
    }
    public record Goal(String title, long current, long target, String unit, String icon) { }

    public PlayerCardProfile {
        preferences = List.copyOf(preferences);
        achievements = List.copyOf(achievements);
    }

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("stone", "石头"), Map.entry("cobblestone", "圆石"), Map.entry("deepslate", "深板岩"),
            Map.entry("cobbled_deepslate", "深板岩圆石"), Map.entry("dirt", "泥土"), Map.entry("grass_block", "草方块"),
            Map.entry("sand", "沙子"), Map.entry("gravel", "沙砾"), Map.entry("netherrack", "下界岩"),
            Map.entry("end_stone", "末地石"), Map.entry("obsidian", "黑曜石"), Map.entry("andesite", "安山岩"),
            Map.entry("diorite", "闪长岩"), Map.entry("granite", "花岗岩"), Map.entry("tuff", "凝灰岩"),
            Map.entry("oak_log", "橡木原木"), Map.entry("spruce_log", "云杉原木"), Map.entry("birch_log", "白桦原木"),
            Map.entry("cherry_log", "樱花原木"), Map.entry("oak_planks", "橡木木板"), Map.entry("spruce_planks", "云杉木板"),
            Map.entry("bamboo_planks", "竹板"), Map.entry("stick", "木棍"), Map.entry("torch", "火把"),
            Map.entry("firework_rocket", "烟花火箭"), Map.entry("paper", "纸"), Map.entry("bone_meal", "骨粉"),
            Map.entry("iron_ingot", "铁锭"), Map.entry("gold_ingot", "金锭"), Map.entry("gold_nugget", "金粒"),
            Map.entry("iron_nugget", "铁粒"), Map.entry("iron_block", "铁块"), Map.entry("gold_block", "金块"),
            Map.entry("diamond", "钻石"), Map.entry("diamond_ore", "钻石矿石"), Map.entry("deepslate_diamond_ore", "深层钻石矿石"),
            Map.entry("coal_ore", "煤矿石"), Map.entry("iron_ore", "铁矿石"), Map.entry("deepslate_iron_ore", "深层铁矿石"),
            Map.entry("redstone", "红石粉"), Map.entry("redstone_block", "红石块"), Map.entry("chest", "箱子"),
            Map.entry("hopper", "漏斗"), Map.entry("stone_bricks", "石砖"), Map.entry("glass", "玻璃"),
            Map.entry("wheat", "小麦"), Map.entry("bread", "面包"), Map.entry("dried_kelp_block", "干海带块"),
            Map.entry("bamboo_block", "竹块"), Map.entry("sugar", "糖"), Map.entry("snow_block", "雪块"),
            Map.entry("white_concrete", "白色混凝土"), Map.entry("white_concrete_powder", "白色混凝土粉末"),
            Map.entry("terracotta", "陶瓦"), Map.entry("smooth_stone_slab", "平滑石台阶"),
            Map.entry("snow", "雪"), Map.entry("stripped_birch_wood", "去皮白桦木"),
            Map.entry("polished_diorite", "磨制闪长岩"), Map.entry("polished_andesite", "磨制安山岩")
    );

    private record Movement(String key, String name, String icon) { }
    private static final List<Movement> MOVEMENT = List.of(
            new Movement("walk_one_cm", "步行", "route"), new Movement("sprint_one_cm", "疾跑", "route"),
            new Movement("crouch_one_cm", "潜行", "route"), new Movement("aviate_one_cm", "鞘翅飞行", "wing"),
            new Movement("swim_one_cm", "游泳", "route"), new Movement("boat_one_cm", "划船", "route"),
            new Movement("minecart_one_cm", "矿车", "route"), new Movement("horse_one_cm", "骑马", "route"),
            new Movement("pig_one_cm", "骑猪", "route"), new Movement("strider_one_cm", "炽足兽", "route"),
            new Movement("happy_ghast_one_cm", "浮空恶魂", "wing"),
            new Movement("walk_under_water_one_cm", "水下行走", "route"),
            new Movement("walk_on_water_one_cm", "水面移动", "route"),
            new Movement("climb_one_cm", "攀爬", "route"), new Movement("fly_one_cm", "腾空移动", "wing")
    );

    /** @param root the entire vanilla document containing a top-level {@code stats} object. */
    public static PlayerCardProfile fromJson(JSONObject root) {
        return fromJson(root, AchievementThresholds.defaults());
    }

    /** Uses one immutable threshold snapshot for all six categories and their progress. */
    public static PlayerCardProfile fromJson(JSONObject root, AchievementThresholds thresholds) {
        java.util.Objects.requireNonNull(thresholds, "thresholds");
        JSONObject stats = root == null ? null : root.getJSONObject("stats");
        if (stats == null) throw new IllegalArgumentException("缺少 stats 对象");
        JSONObject custom = stats.getJSONObject("minecraft:custom");
        JSONObject mined = stats.getJSONObject("minecraft:mined");
        JSONObject crafted = stats.getJSONObject("minecraft:crafted");
        String timeKey = custom != null && custom.containsKey("minecraft:play_time") ? "play_time" : "play_one_minute";
        long ticks = custom(custom, timeKey), walked = custom(custom, "walk_one_cm");
        long minedTotal = total(mined), craftedTotal = total(crafted);
        long fishCaught = custom(custom, "fish_caught"), traded = custom(custom, "traded_with_villager");
        long aviated = custom(custom, "aviate_one_cm");
        List<Preference> preferences = new ArrayList<>();
        addFavorite(preferences, mined, "最常挖掘", "块", "pickaxe");
        addFavorite(preferences, crafted, "最常合成", "件", "cube");
        Movement favorite = null;
        long greatestDistance = 0;
        for (Movement movement : MOVEMENT) {
            long distance = custom(custom, movement.key());
            if (distance > greatestDistance) { favorite = movement; greatestDistance = distance; }
        }
        if (favorite != null) preferences.add(new Preference("偏爱出行", favorite.name(),
                formatDistance(greatestDistance), favorite.icon()));

        List<Milestone> milestones = List.of(
                new Milestone("地下常住人口", minedTotal, "块", "pickaxe", 1, thresholds.mining().values()),
                new Milestone("方块批发商", craftedTotal, "件", "cube", 1, thresholds.crafting().values()),
                new Milestone("空中通勤族", aviated, "km", "wing", 100_000, thresholds.elytra().values()),
                new Milestone("资深钓客", fishCaught, "次", "fish", 1, thresholds.fishing().values()),
                new Milestone("贸易伙伴", traded, "次", "trade", 1, thresholds.trading().values()),
                new Milestone("远行者", walked, "km", "route", 100_000, thresholds.walking().values())
        );
        List<Milestone> earned = milestones.stream().filter(m -> m.level() > 0).toList();
        // Stable positions are important for a collection grid, including not-yet-earned achievements.
        List<Achievement> achievements = milestones.stream().map(Milestone::achievement).toList();
        Goal goal = earned.stream().filter(m -> m.level() < m.thresholds().length)
                .max(Comparator.comparingDouble(Milestone::nextProgress))
                .map(m -> new Goal(m.title() + " · " + levelName(m.level() + 1), m.count() / m.unitScale(),
                        m.thresholds()[m.level()] / m.unitScale(), m.unit(), m.icon())).orElse(null);

        String quip;
        if (craftedTotal >= 1_000_000) quip = "你的工作台，应该有自己的工号。";
        else if (custom(custom, "aviate_one_cm") >= 100_000_000) quip = "地面上的路，已经留不住你了。";
        else if (minedTotal >= 100_000) quip = "矿镐还没下班，你已经准备好下一趟了。";
        else if (custom(custom, "fish_caught") >= 100) quip = "再钓一竿，就真的回家。";
        else if (custom(custom, "traded_with_villager") >= 100) quip = "村民看到你，已经开始准备找零了。";
        else if (walked >= 10_000_000) quip = "沿途的风景，都是你的收藏。";
        else quip = "故事刚刚开始，下一页由你来写。";
        return new PlayerCardProfile(ticks, walked, minedTotal, craftedTotal, fishCaught, traded, aviated,
                preferences, achievements, goal, quip);
    }

    private record Milestone(String title, long count, String unit, String icon, long unitScale, long[] thresholds) {
        int level() { int result = 0; for (long threshold : thresholds) if (count >= threshold) result++; return result; }
        double nextProgress() {
            int level = level();
            long lower = level == 0 ? 0 : thresholds[level - 1];
            return (count - lower) / (double) (thresholds[level] - lower);
        }
        Achievement achievement() {
            int level = level();
            long lower = level == 0 ? 0 : thresholds[level - 1];
            long next = level == thresholds.length ? lower : thresholds[level];
            String detail = "累计 " + (unitScale == 100_000 ? formatDistance(count) : formatCount(count) + " " + unit);
            return new Achievement(title, levelName(level), detail, icon, count, lower, next);
        }
    }

    private static String levelName(int level) { return switch (level) { case 0 -> "未解锁"; case 1 -> "铜"; case 2 -> "银"; default -> "金"; }; }

    private static void addFavorite(List<Preference> out, JSONObject category, String label, String unit, String icon) {
        if (category == null) return;
        String favorite = category.keySet().stream().filter(key -> value(category, key) > 0)
                .sorted(Comparator.<String>comparingLong(key -> value(category, key)).reversed().thenComparing(key -> key))
                .findFirst().orElse(null);
        if (favorite == null) return;
        String item = favorite.startsWith("minecraft:") ? favorite.substring(10) : favorite;
        out.add(new Preference(label, favorite.startsWith("minecraft:") ? NAMES.getOrDefault(item, item) : item,
                formatCount(value(category, favorite)) + " " + unit, icon));
    }

    private static long custom(JSONObject stats, String key) { return value(stats, "minecraft:" + key); }

    private static long value(JSONObject object, String key) {
        if (object == null || !object.containsKey(key)) return 0;
        Object raw = object.get(key);
        if (!(raw instanceof Integer || raw instanceof Long)) throw new IllegalArgumentException("统计值必须是整数: " + key);
        long value = ((Number) raw).longValue();
        if (value < 0 || value > Integer.MAX_VALUE) throw new IllegalArgumentException("统计值超出原版范围: " + key);
        return value;
    }

    private static long total(JSONObject category) {
        if (category == null) return 0;
        long sum = 0;
        for (String key : category.keySet()) sum = Math.addExact(sum, value(category, key));
        return sum;
    }

    public static String formatCount(long value) {
        if (value >= 100_000_000) return shortDecimal(value / 100_000_000d) + "亿";
        if (value >= 10_000) return shortDecimal(value / 10_000d) + "万";
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static String formatDistance(long centimeters) {
        if (centimeters < 100) return centimeters + " cm";
        if (centimeters < 100_000) return shortDecimal(centimeters / 100d) + " m";
        return shortDecimal(centimeters / 100_000d) + " km";
    }

    private static String shortDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }
}
