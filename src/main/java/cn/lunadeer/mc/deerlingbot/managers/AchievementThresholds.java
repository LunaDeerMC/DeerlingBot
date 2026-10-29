package cn.lunadeer.mc.deerlingbot.managers;

import java.util.Objects;

/** Immutable thresholds in the same original units as the saved vanilla statistics. */
public record AchievementThresholds(Tiers mining, Tiers crafting, Tiers elytra,
                              Tiers fishing, Tiers trading, Tiers walking) {
    public static final long CENTIMETERS_PER_KILOMETER = 100_000;
    public static final long MAX_DISTANCE_KILOMETERS = Integer.MAX_VALUE / CENTIMETERS_PER_KILOMETER;

    // Calibrated from 1,796 saved player records on 2026-10-30 (Asia/Shanghai),
    // then moderately raised bronze thresholds following the owner's feedback.
    // The offline method and cohort checks are documented in docs/achievement-calibration-status.md.
    private static final AchievementThresholds DEFAULTS = new AchievementThresholds(
            new Tiers(500, 36_000, 680_000),
            new Tiers(300, 31_000, 1_200_000),
            Tiers.kilometers(8, 360, 5_100),
            new Tiers(5, 98, 1_100),
            new Tiers(25, 390, 18_000),
            Tiers.kilometers(5, 120, 1_500));

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

    public record Tiers(long bronze, long silver, long gold) {
        public Tiers {
            if (bronze <= 0 || silver <= bronze || gold <= silver)
                throw new IllegalArgumentException("Thresholds must be positive integers with bronze < silver < gold");
        }

        public static Tiers kilometers(long bronze, long silver, long gold) {
            if (gold > MAX_DISTANCE_KILOMETERS)
                throw new IllegalArgumentException("Distance thresholds cannot exceed " + MAX_DISTANCE_KILOMETERS + " km");
            return new Tiers(Math.multiplyExact(bronze, CENTIMETERS_PER_KILOMETER),
                    Math.multiplyExact(silver, CENTIMETERS_PER_KILOMETER),
                    Math.multiplyExact(gold, CENTIMETERS_PER_KILOMETER));
        }

        public long[] values() { return new long[]{bronze, silver, gold}; }
    }
}
