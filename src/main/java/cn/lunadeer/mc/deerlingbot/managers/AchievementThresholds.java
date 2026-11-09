package cn.lunadeer.mc.deerlingbot.managers;

import java.util.Objects;

/** Immutable thresholds in the same original units as the saved vanilla statistics. */
public record AchievementThresholds(Tiers mining, Tiers crafting, Tiers elytra,
                              Tiers fishing, Tiers trading, Tiers walking) {
    public static final long CENTIMETERS_PER_KILOMETER = 100_000;
    public static final long MAX_DISTANCE_KILOMETERS = Integer.MAX_VALUE / CENTIMETERS_PER_KILOMETER;

    // Calibrated from 1,809 saved player records on 2026-11-09 (Asia/Shanghai).
    // Wood retains the previously tuned entry floor; higher tiers use playtime cohorts.
    // The offline method and cohort checks are documented in docs/achievement-calibration-status.md.
    private static final AchievementThresholds DEFAULTS = new AchievementThresholds(
            new Tiers(550, 10_000, 36_000, 190_000, 1_300_000),
            new Tiers(340, 8_300, 31_000, 260_000, 2_100_000),
            Tiers.kilometers(10, 110, 370, 1_700, 6_900),
            new Tiers(5, 19, 98, 280, 1_800),
            new Tiers(27, 150, 390, 5_100, 31_000),
            Tiers.kilometers(5, 44, 120, 480, 2_000));

    public AchievementThresholds {
        Objects.requireNonNull(mining, "mining");
        Objects.requireNonNull(crafting, "crafting");
        requireVanillaRange(elytra, "elytra");
        requireVanillaRange(fishing, "fishing");
        requireVanillaRange(trading, "trading");
        requireVanillaRange(walking, "walking");
    }

    public static AchievementThresholds defaults() { return DEFAULTS; }

    private static void requireVanillaRange(Tiers tiers, String category) {
        Objects.requireNonNull(tiers, category);
        if (tiers.gold() > Integer.MAX_VALUE)
            throw new IllegalArgumentException(category + " thresholds exceed the vanilla statistic limit");
    }

    public record Tiers(long wood, long iron, long copper, long diamond, long gold) {
        public Tiers {
            if (wood <= 0 || iron <= wood || copper <= iron || diamond <= copper || gold <= diamond)
                throw new IllegalArgumentException("Thresholds must be positive integers with wood < iron < copper < diamond < gold");
        }

        public static Tiers kilometers(long wood, long iron, long copper, long diamond, long gold) {
            if (gold > MAX_DISTANCE_KILOMETERS)
                throw new IllegalArgumentException("Distance thresholds cannot exceed " + MAX_DISTANCE_KILOMETERS + " km");
            return new Tiers(Math.multiplyExact(wood, CENTIMETERS_PER_KILOMETER),
                    Math.multiplyExact(iron, CENTIMETERS_PER_KILOMETER),
                    Math.multiplyExact(copper, CENTIMETERS_PER_KILOMETER),
                    Math.multiplyExact(diamond, CENTIMETERS_PER_KILOMETER),
                    Math.multiplyExact(gold, CENTIMETERS_PER_KILOMETER));
        }

        public long[] values() { return new long[]{wood, iron, copper, diamond, gold}; }
    }
}
