package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerSnapshotServiceTest {
    @TempDir Path directory;

    private JavaPlugin plugin(Path worldFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        World overworld = mock(World.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(server.getOnlinePlayers()).thenReturn(List.of());
        when(overworld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(overworld.getWorldFolder()).thenReturn(worldFolder.toFile());
        when(server.getWorlds()).thenReturn(List.of(overworld));
        return plugin;
    }

    private void stubScheduler(MockedStatic<Scheduler> scheduler) {
        scheduler.when(() -> Scheduler.callTaskAsync(any(Supplier.class))).thenAnswer(call ->
                CompletableFuture.supplyAsync((Supplier<?>) call.getArgument(0)));
        scheduler.when(() -> Scheduler.callTask(any(Supplier.class))).thenAnswer(call ->
                CompletableFuture.completedFuture(((Supplier<?>) call.getArgument(0)).get()));
    }

    private Path save(UUID uuid, String json, boolean deerFoliaLayout) throws Exception {
        Path stats = directory.resolve("world").resolve(deerFoliaLayout ? "players/stats" : "stats");
        Files.createDirectories(stats);
        Path file = stats.resolve(uuid + ".json");
        Files.writeString(file, json);
        return file;
    }

    private static final String JSON = """
            {"stats": {
                "minecraft:custom": {"minecraft:play_time": 2400, "minecraft:walk_one_cm": 500},
                "minecraft:mined": {"minecraft:stone": 12, "minecraft:dirt": 8},
                "minecraft:crafted": {"minecraft:stick": 4, "minecraft:torch": 16}
            }, "DataVersion": 3465}
            """;

    @Test
    void offlineReadsAllFourStatisticsFromWorldJsonAndRefreshesForNextRequest() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path file = save(uuid, JSON, false);
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<PlaceHolderApiManager> papi = mockStatic(PlaceHolderApiManager.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             PlayerSnapshotService service = create(plugin(directory.resolve("world")), scheduler)) {
            var snapshot = service.captureInfo(uuid, "Alex", "yesterday").get(5, TimeUnit.SECONDS);
            assertFalse(snapshot.online());
            assertEquals("2分钟 5.00 m 20 20", snapshot.summary());

            Files.writeString(file, "{\"stats\":{\"minecraft:mined\":{\"minecraft:stone\":42},\"minecraft:crafted\":{\"minecraft:torch\":7}}}");
            assertEquals("0分钟 0cm 42 7",
                    service.captureInfo(uuid, "Alex", "yesterday").get(5, TimeUnit.SECONDS).summary());
            assertFalse(Files.exists(directory.resolve("player-statistics-cache.json")));
            papi.verifyNoInteractions();
            bukkit.verifyNoInteractions();
        }
    }

    @Test
    void onlineReadsSameJsonOnAsyncThreadWithoutInvokingBukkitStatisticsOrPapi() throws Exception {
        UUID uuid = UUID.randomUUID();
        save(uuid, JSON, true);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alex");
        Thread caller = Thread.currentThread();
        AtomicBoolean offThread = new AtomicBoolean();
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<PlaceHolderApiManager> papi = mockStatic(PlaceHolderApiManager.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            scheduler.when(() -> Scheduler.callTaskAsync(any(Supplier.class))).thenAnswer(call ->
                    CompletableFuture.supplyAsync(() -> {
                        offThread.set(Thread.currentThread() != caller);
                        return ((Supplier<?>) call.getArgument(0)).get();
                    }));
            scheduler.when(() -> Scheduler.callTask(any(Supplier.class))).thenAnswer(call ->
                    CompletableFuture.completedFuture(((Supplier<?>) call.getArgument(0)).get()));
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin(directory.resolve("world")))) {
                service.onJoin(new PlayerJoinEvent(player, ""));
                assertTrue(service.isOnline(uuid));
                assertEquals(List.of(new PlayerSnapshotService.OnlinePlayer(uuid, "Alex")), service.onlinePlayers());

                var snapshot = service.captureInfo(uuid, "Alex", "yesterday").get(5, TimeUnit.SECONDS);
                assertTrue(snapshot.online());
                assertTrue(offThread.get(), "World JSON must be loaded off the game thread");
                assertEquals("2分钟 5.00 m 20 20", snapshot.summary());
                verify(player, never()).getStatistic(any(Statistic.class));
                papi.verifyNoInteractions();
                bukkit.verifyNoInteractions();

                service.onQuit(new PlayerQuitEvent(player, ""));
                assertFalse(service.isOnline(uuid));
                assertEquals("2分钟 5.00 m 20 20",
                        service.captureInfo(uuid, "Alex", "yesterday").get(5, TimeUnit.SECONDS).summary());
                verify(player, never()).getStatistic(any(Statistic.class));
                assertFalse(Files.exists(directory.resolve("player-statistics-cache.json")));
            }
        }
    }

    @Test
    void prefersOverworldJsonWhenNetherListedFirst() throws Exception {
        UUID uuid = UUID.randomUUID();
        save(uuid, JSON, true);
        JavaPlugin plugin = plugin(directory.resolve("world"));
        World nether = mock(World.class);
        when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);
        World overworld = plugin.getServer().getWorlds().get(0);
        when(plugin.getServer().getWorlds()).thenReturn(List.of(nether, overworld));
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class)) {
            stubScheduler(scheduler);
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin)) {
                assertEquals("2分钟 5.00 m 20 20",
                        service.captureInfo(uuid, "Alex", "yesterday").get(5, TimeUnit.SECONDS).summary());
            }
        }
    }

    @Test
    void readsConfiguredSaveRootWhenBukkitReportsIncorrectDimensionFolder() throws Exception {
        UUID uuid = UUID.randomUUID();
        save(uuid, JSON, true);
        Files.writeString(directory.resolve("server.properties"), "level-name=world\n");
        JavaPlugin plugin = plugin(directory.resolve("unrelated-dimension"));
        when(plugin.getDataFolder()).thenReturn(directory.resolve("plugins/DeerlingBot").toFile());
        when(plugin.getServer().getWorlds().get(0).getName()).thenReturn("wrong-world-name");
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<PlaceHolderApiManager> papi = mockStatic(PlaceHolderApiManager.class)) {
            stubScheduler(scheduler);
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin)) {
                assertEquals("2分钟 5.00 m 20 20",
                        service.captureInfo(uuid, "Alex", "yesterday").get(5, TimeUnit.SECONDS).summary());
            }
            papi.verifyNoInteractions();
        }
    }

    @Test
    void readsConfiguredSaveRootWhenWorldListIsNotYetPopulated() throws Exception {
        UUID uuid = UUID.randomUUID();
        save(uuid, JSON, true);
        Files.writeString(directory.resolve("server.properties"), "level-name=world\n");
        JavaPlugin plugin = plugin(directory.resolve("bad"));
        when(plugin.getDataFolder()).thenReturn(directory.resolve("plugins/DeerlingBot").toFile());
        when(plugin.getServer().getWorlds()).thenReturn(List.of());
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class)) {
            stubScheduler(scheduler);
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin)) {
                assertEquals("2分钟 5.00 m 20 20",
                        service.captureInfo(uuid, "Alex", "yesterday").get(5, TimeUnit.SECONDS).summary());
            }
        }
    }

    @Test
    void absentJsonDoesNotUseOldCacheAndReportsAllStatisticsUnavailable() throws Exception {
        UUID uuid = UUID.randomUUID();
        Files.writeString(directory.resolve("player-statistics-cache.json"),
                "{\"" + uuid + "\":{\"playedTicks\":2400,\"walkedCentimeters\":99999}}");
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class)) {
            stubScheduler(scheduler);
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin(directory.resolve("world")))) {
                assertEquals("暂不可用 暂不可用 暂不可用 暂不可用",
                        service.captureInfo(uuid, "Alex", "yesterday").get(5, TimeUnit.SECONDS).summary());
            }
        }
        assertTrue(Files.readString(directory.resolve("player-statistics-cache.json")).contains("99999"),
                "Legacy cache is left untouched, not read and not deleted");
    }

    @Test
    void validJsonWithoutCategoriesMeansZeroRatherThanUnavailable() throws Exception {
        UUID uuid = UUID.randomUUID();
        save(uuid, "{\"stats\":{}}", true);
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class)) {
            stubScheduler(scheduler);
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin(directory.resolve("world")))) {
                assertEquals("0分钟 0cm 0 0",
                        service.captureInfo(uuid, "Alex", "yesterday").get(5, TimeUnit.SECONDS).summary());
            }
        }
    }

    @Test
    void damagedJsonDoesNotCrashAndDoesNotFallBackToPapi() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path file = save(uuid, "{truncated", true);
        JavaPlugin plugin = plugin(directory.resolve("world"));
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<PlaceHolderApiManager> papi = mockStatic(PlaceHolderApiManager.class)) {
            stubScheduler(scheduler);
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin)) {
                assertEquals("暂不可用 暂不可用 暂不可用 暂不可用",
                        service.captureInfo(uuid, "Alex", "yesterday").get(5, TimeUnit.SECONDS).summary());
                verify(logger).warning(contains("读取统计文件失败"));
                papi.verifyNoInteractions();
                assertEquals("{truncated", Files.readString(file));
            }
        }
    }

    @Test
    void shutdownCancelsPendingJsonQueries() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class)) {
            scheduler.when(() -> Scheduler.callTask(any(Supplier.class))).thenAnswer(call ->
                    CompletableFuture.completedFuture(((Supplier<?>) call.getArgument(0)).get()));
            CompletableFuture<PlayerSnapshotService.InfoSnapshot> stalled = new CompletableFuture<>();
            scheduler.when(() -> Scheduler.callTaskAsync(any(Supplier.class))).thenReturn(stalled);
            PlayerSnapshotService service = new PlayerSnapshotService(plugin(directory.resolve("world")));
            CompletableFuture<PlayerSnapshotService.InfoSnapshot> pending = service.captureInfo(uuid, "Alex", "yesterday");
            service.close();
            assertTrue(pending.isCancelled());
            assertTrue(service.captureInfo(uuid, "Alex", "yesterday").isCompletedExceptionally());
        }
    }

    private PlayerSnapshotService create(JavaPlugin plugin, MockedStatic<Scheduler> scheduler) {
        stubScheduler(scheduler);
        return new PlayerSnapshotService(plugin);
    }
}
