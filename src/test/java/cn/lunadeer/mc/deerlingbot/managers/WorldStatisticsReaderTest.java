package cn.lunadeer.mc.deerlingbot.managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldStatisticsReaderTest {
    @TempDir Path directory;
    private final UUID uuid = UUID.randomUUID();

    private void write(String json) throws IOException {
        Files.writeString(directory.resolve(uuid + ".json"), json);
    }

    @Test
    void resolvesDeerFoliaPlayersStatsAndPrefersNormalWorldStats() throws Exception {
        Path world = directory.resolve("world");
        Path deerFoliaStats = world.resolve("players").resolve("stats");
        Files.createDirectories(deerFoliaStats);
        Files.writeString(deerFoliaStats.resolve(uuid + ".json"),
                "{\"stats\":{\"minecraft:custom\":{\"minecraft:play_time\":2400},\"minecraft:mined\":{\"minecraft:stone\":42},\"minecraft:crafted\":{\"minecraft:stick\":7}}}");
        assertTotals(new WorldStatisticsReader.Statistics(2400, 0, 42, 7),
                WorldStatisticsReader.readFromWorld(world, uuid));

        Path normalStats = world.resolve("stats");
        Files.createDirectories(normalStats);
        Files.writeString(normalStats.resolve(uuid + ".json"),
                "{\"stats\":{\"minecraft:custom\":{\"minecraft:play_time\":1200}}}");
        assertTotals(new WorldStatisticsReader.Statistics(1200, 0, 0, 0),
                WorldStatisticsReader.readFromWorld(world, uuid));
        assertNull(WorldStatisticsReader.readFromWorld(world, UUID.randomUUID()));
    }

    @Test
    void readsModernStatisticsAndSumsAllBlockAndItemTypesWithoutIntegerOverflow() throws Exception {
        write("""
                {"stats": {
                  "minecraft:custom": {"minecraft:play_time": 72000, "minecraft:walk_one_cm": 12345,
                                       "minecraft:jump": 999},
                  "minecraft:mined": {"minecraft:stone": 2000000000, "minecraft:dirt": 1000000000},
                  "minecraft:crafted": {"minecraft:stick": 4, "minecraft:torch": 16},
                  "minecraft:used": {"minecraft:stone": 100}
                }, "DataVersion": 3465}
                """);
        assertTotals(new WorldStatisticsReader.Statistics(72000, 12345, 3000000000L, 20),
                WorldStatisticsReader.read(directory, uuid));
        assertEquals("30.00 亿", cn.lunadeer.mc.deerlingbot.utils.Misc.decimalConverter(3000000000L));
    }

    @Test
    void supportsLegacyPlayTimeKeyAndPrefersModernKeyWhenBothExist() throws Exception {
        write("""
                {"stats":{"minecraft:custom":{"minecraft:play_one_minute":1200}}}
                """);
        assertEquals(1200, WorldStatisticsReader.read(directory, uuid).playedTicks());
        write("""
                {"stats":{"minecraft:custom":{"minecraft:play_one_minute":1200,"minecraft:play_time":2400}}}
                """);
        assertEquals(2400, WorldStatisticsReader.read(directory, uuid).playedTicks());
    }

    @Test
    void absentStatisticsAreZeroButMissingPlayerFileIsUnavailable() throws Exception {
        assertNull(WorldStatisticsReader.read(directory, uuid));
        write("{\"stats\":{}}");
        assertTotals(new WorldStatisticsReader.Statistics(0, 0, 0, 0), WorldStatisticsReader.read(directory, uuid));
        assertNull(WorldStatisticsReader.read(null, uuid));
    }

    @Test
    void rejectsIncompleteDocumentsAndInvalidCountersInsteadOfInventingZeros() throws Exception {
        for (String json : new String[]{"{", "null", "{}", "{\"stats\":null}",
                "{\"stats\":{\"minecraft:mined\":{\"minecraft:stone\":-1}}}",
                "{\"stats\":{\"minecraft:crafted\":{\"minecraft:stick\":1.5}}}",
                "{\"stats\":{\"minecraft:custom\":{\"minecraft:play_time\":2147483648}}}"}) {
            write(json);
            assertThrows(IOException.class, () -> WorldStatisticsReader.read(directory, uuid), json);
        }
    }

    @Test
    void rejectsOversizedFiles() throws Exception {
        write(" ".repeat(4 * 1024 * 1024 + 1));
        assertThrows(IOException.class, () -> WorldStatisticsReader.read(directory, uuid));
    }

    @Test
    void explicitThresholdSnapshotIsUsedWhenReadingTheActualStatsFile() throws Exception {
        write("{\"stats\":{\"minecraft:custom\":{\"minecraft:traded_with_villager\":15}}}");
        AchievementThresholds defaults = AchievementThresholds.defaults();
        AchievementThresholds custom = new AchievementThresholds(defaults.mining(), defaults.crafting(), defaults.elytra(),
                defaults.fishing(), new AchievementThresholds.Tiers(5, 10, 20), defaults.walking());
        PlayerCardProfile profile = WorldStatisticsReader.read(directory, uuid, custom).profile();
        PlayerCardProfile.Achievement trade = profile.achievements().stream().filter(b -> b.icon().equals("trade")).findFirst().orElseThrow();
        assertEquals("银", trade.level());
        assertEquals("50%", trade.progressPercentText());
        assertEquals(5, trade.litSegments());
    }

    private static void assertTotals(WorldStatisticsReader.Statistics expected, WorldStatisticsReader.Statistics actual) {
        assertNotNull(actual);
        assertAll(() -> assertEquals(expected.playedTicks(), actual.playedTicks()),
                () -> assertEquals(expected.walkedCentimeters(), actual.walkedCentimeters()),
                () -> assertEquals(expected.minedBlocks(), actual.minedBlocks()),
                () -> assertEquals(expected.craftedItems(), actual.craftedItems()),
                () -> assertNotNull(actual.profile()),
                () -> assertEquals(actual.minedBlocks(), actual.profile().minedBlocks()));
    }
}
