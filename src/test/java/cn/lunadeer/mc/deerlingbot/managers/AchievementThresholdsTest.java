package cn.lunadeer.mc.deerlingbot.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AchievementThresholdsTest {
    @Test
    void everyTierMustBePositiveAndStrictlyIncreasing() {
        long[][] invalid = {{0, 2, 3, 4, 5}, {2, 2, 3, 4, 5}, {1, 3, 3, 4, 5},
                {1, 2, 4, 4, 5}, {1, 2, 3, 5, 5}, {1, 2, 4, 3, 5}};
        for (long[] values : invalid) {
            assertThrows(IllegalArgumentException.class, () -> new AchievementThresholds.Tiers(
                    values[0], values[1], values[2], values[3], values[4]));
        }
    }

    @Test
    void distanceConversionPreservesAllFiveLevelsAndVanillaLimit() {
        AchievementThresholds.Tiers tiers = AchievementThresholds.Tiers.kilometers(1, 2, 3, 4, 21_474);
        assertArrayEquals(new long[]{100_000, 200_000, 300_000, 400_000, 2_147_400_000L}, tiers.values());
        assertThrows(IllegalArgumentException.class,
                () -> AchievementThresholds.Tiers.kilometers(1, 2, 3, 4, 21_475));
        long[] exported = tiers.values();
        exported[0] = 0;
        assertEquals(100_000, tiers.wood());
    }
}
