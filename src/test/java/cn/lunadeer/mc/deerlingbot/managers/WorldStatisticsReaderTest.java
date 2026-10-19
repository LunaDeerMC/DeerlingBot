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
        assertEquals(new WorldStatisticsReader.Statistics(72000, 12345, 3000000000L, 20),
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
        assertEquals(new WorldStatisticsReader.Statistics(0, 0, 0, 0), WorldStatisticsReader.read(directory, uuid));
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
}
