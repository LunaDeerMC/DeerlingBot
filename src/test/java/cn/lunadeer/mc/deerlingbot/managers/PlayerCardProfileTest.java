package cn.lunadeer.mc.deerlingbot.managers;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerCardProfileTest {
    // Fixed test rules keep arithmetic examples independent of future server balancing changes.
    private static final AchievementThresholds TEST_THRESHOLDS = new AchievementThresholds(
            new AchievementThresholds.Tiers(10_000, 100_000, 1_000_000),
            new AchievementThresholds.Tiers(10_000, 100_000, 1_000_000),
            AchievementThresholds.Tiers.kilometers(100, 1_000, 10_000),
            new AchievementThresholds.Tiers(100, 1_000, 10_000),
            new AchievementThresholds.Tiers(100, 1_000, 10_000),
            AchievementThresholds.Tiers.kilometers(100, 1_000, 10_000));

    private static PlayerCardProfile profile(String json) {
        return PlayerCardProfile.fromJson(JSONObject.parseObject(json), TEST_THRESHOLDS);
    }

    @Test
    void noCountersShowsSixLockedPositionsWithoutInventingEarnedAchievements() {
        PlayerCardProfile result = profile("{\"stats\":{}}");
        assertTrue(result.preferences().isEmpty());
        assertEquals(List.of("pickaxe", "cube", "wing", "fish", "trade", "route"),
                result.achievements().stream().map(PlayerCardProfile.Achievement::icon).toList());
        for (PlayerCardProfile.Achievement achievement : result.achievements()) {
            assertTrue(achievement.locked());
            assertFalse(achievement.isMaxLevel());
            assertEquals("未解锁", achievement.level());
            assertEquals("0%", achievement.progressPercentText());
            assertEquals(0, achievement.litSegments());
        }
        assertNull(result.goal());
        assertThrows(IllegalArgumentException.class, () -> profile("{}"));
    }

    @Test
    void actualCategoriesDrivePreferencesAndMovementDoesNotChangeWalkingCounter() {
        PlayerCardProfile result = profile("""
                {"stats":{
                  "minecraft:custom":{"minecraft:walk_one_cm":204317867,"minecraft:aviate_one_cm":703763504,
                    "minecraft:fish_caught":38,"minecraft:traded_with_villager":8479},
                  "minecraft:mined":{"minecraft:terracotta":51354,"minecraft:stone":40000},
                  "minecraft:crafted":{"minecraft:smooth_stone_slab":256801,"minecraft:stick":2}
                }}
                """);
        assertEquals(204317867L, result.walkedCentimeters());
        assertEquals(703763504L, result.aviatedCentimeters());
        assertEquals(38, result.fishCaught());
        assertEquals(8479, result.tradedWithVillager());
        assertEquals("陶瓦", result.preferences().get(0).value());
        assertEquals("平滑石台阶", result.preferences().get(1).value());
        assertEquals("鞘翅飞行", result.preferences().get(2).value());
        assertEquals("7037.64 km", result.preferences().get(2).detail());
        assertEquals("2043.18 km", PlayerCardProfile.formatDistance(result.walkedCentimeters()));
        // Every achievement describes the same original counters displayed in the seven statistics.
        assertEquals(result.walkedCentimeters(), achievement(result, "route").current());
        assertEquals(result.aviatedCentimeters(), achievement(result, "wing").current());
        assertEquals(result.fishCaught(), achievement(result, "fish").current());
        assertEquals(result.tradedWithVillager(), achievement(result, "trade").current());
        assertEquals("67.08%", achievement(result, "wing").progressPercentText());
        assertEquals(6, achievement(result, "wing").litSegments());
        assertEquals("83.1%", achievement(result, "trade").progressPercentText());
        assertEquals(8, achievement(result, "trade").litSegments());
    }

    @Test
    void earningATierResetsItsProgressAndGoldIsComplete() {
        PlayerCardProfile below = custom("fish_caught", 99);
        assertTrue(achievement(below, "fish").locked());
        assertEquals("99%", achievement(below, "fish").progressPercentText());
        assertEquals(9, achievement(below, "fish").litSegments());
        assertNull(below.goal());
        PlayerCardProfile first = custom("fish_caught", 100);
        PlayerCardProfile.Achievement bronze = achievement(first, "fish");
        assertEquals("铜", bronze.level());
        assertFalse(bronze.locked());
        assertEquals("0%", bronze.progressPercentText());
        assertEquals(0, bronze.litSegments());
        assertEquals(new PlayerCardProfile.Goal("资深钓客 · 银", 100, 1000, "次", "fish"), first.goal());
        PlayerCardProfile.Achievement bronzeProgress = achievement(custom("fish_caught", 190), "fish");
        assertEquals("10%", bronzeProgress.progressPercentText());
        assertEquals(1, bronzeProgress.litSegments());
        PlayerCardProfile.Achievement silver = achievement(custom("fish_caught", 1000), "fish");
        assertEquals("银", silver.level());
        assertEquals("0%", silver.progressPercentText());
        PlayerCardProfile.Achievement gold = achievement(custom("fish_caught", 10000), "fish");
        assertEquals("金", gold.level());
        assertTrue(gold.isMaxLevel());
        assertEquals("100%", gold.progressPercentText());
        assertEquals(10, gold.litSegments());
        assertEquals("100%", achievement(custom("fish_caught", 12000), "fish").progressPercentText());
        assertNull(custom("fish_caught", 12000).goal());
    }

    @Test
    void tenSegmentBarFloorsAtExactBoundariesWithoutRoundingPercentageUp() {
        long[] centimeters = {0, 999_000, 1_000_000, 9_999_000, 9_999_999};
        String[] percentages = {"0%", "9.99%", "10%", "99.99%", "99.99%"};
        int[] segments = {0, 0, 1, 9, 9};
        for (int i = 0; i < centimeters.length; i++) {
            PlayerCardProfile.Achievement current = achievement(custom("walk_one_cm", centimeters[i]), "route");
            assertEquals(percentages[i], current.progressPercentText());
            assertEquals(segments[i], current.litSegments());
        }
        assertEquals("100%", achievement(custom("walk_one_cm", 1_000_000_000), "route").progressPercentText());
        assertEquals(10, achievement(custom("walk_one_cm", 1_000_000_000), "route").litSegments());
    }

    @Test
    void progressWithinDistanceTierKeepsCentimeterPrecision() {
        PlayerCardProfile.Achievement bronze = achievement(custom("walk_one_cm", 10_000_000), "route");
        assertEquals("铜", bronze.level());
        assertEquals(10_000_000, bronze.lowerThreshold());
        assertEquals(100_000_000, bronze.nextThreshold());
        assertEquals("0%", bronze.progressPercentText());
        PlayerCardProfile.Achievement justBelowTenth = achievement(custom("walk_one_cm", 18_999_999), "route");
        assertEquals("9.99%", justBelowTenth.progressPercentText());
        assertEquals(0, justBelowTenth.litSegments());
        PlayerCardProfile.Achievement tenth = achievement(custom("walk_one_cm", 19_000_000), "route");
        assertEquals("10%", tenth.progressPercentText());
        assertEquals(1, tenth.litSegments());
    }

    @Test
    void compatibilityGoalStillPointsToAnEarnedCategoryNextTier() {
        PlayerCardProfile next = profile("""
                {"stats": {"minecraft:custom":{"minecraft:fish_caught":500,"minecraft:traded_with_villager":8479}}}
                """);
        assertEquals("贸易伙伴 · 金", next.goal().title());
        assertEquals(8479, next.goal().current());
        assertEquals(10000, next.goal().target());
        assertTrue(next.goal().current() < next.goal().target());
    }

    @Test
    void totalsUseLongAndUnknownItemsRemainIdentifiable() {
        PlayerCardProfile result = profile("""
                {"stats": {"minecraft:mined":{"minecraft:future_block":2000000000,"minecraft:stone":1000000000}}}
                """);
        assertEquals(3_000_000_000L, result.minedBlocks());
        assertTrue(achievement(result, "pickaxe").isMaxLevel());
        assertEquals("100%", achievement(result, "pickaxe").progressPercentText());
        assertEquals("future_block", result.preferences().get(0).value());
        assertEquals("30亿", PlayerCardProfile.formatCount(result.minedBlocks()));
        assertThrows(IllegalArgumentException.class, () -> profile("{\"stats\":{\"minecraft:mined\":{\"minecraft:stone\":-1}}}"));
        assertThrows(IllegalArgumentException.class, () -> profile("{\"stats\":{\"minecraft:custom\":{\"minecraft:fish_caught\":1.5}}}"));
    }

    private static PlayerCardProfile custom(String key, long value) {
        return profile("{\"stats\":{\"minecraft:custom\":{\"minecraft:" + key + "\":" + value + "}}}");
    }

    private static PlayerCardProfile.Achievement achievement(PlayerCardProfile profile, String icon) {
        return profile.achievements().stream().filter(b -> b.icon().equals(icon)).findFirst().orElseThrow();
    }
}
