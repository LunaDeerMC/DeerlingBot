package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.utils.Misc;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceFormattingTest {
    @Test
    void centimetersRemainCorrectAcrossKilometerAndTenThousandKilometerBoundary() {
        // Compare with the default locale used by the existing text-info formatter.
        assertEquals(String.format(Locale.getDefault(), "%.2f km", 2043.17867), Misc.distanceConverter(204_317_867));
        assertEquals(String.format(Locale.getDefault(), "%.2f km", 9999.99999), Misc.distanceConverter(999_999_999));
        assertEquals(String.format(Locale.getDefault(), "%.2f 万km", 1.0), Misc.distanceConverter(1_000_000_000));
        assertEquals(String.format(Locale.getDefault(), "%.2f 万km", 2.0), Misc.distanceConverter(2_000_000_000));
    }
}
