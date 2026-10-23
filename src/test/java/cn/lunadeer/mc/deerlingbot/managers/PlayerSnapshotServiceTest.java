package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerSnapshotServiceTest {
    @TempDir Path directory;

    private JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(server.getOnlinePlayers()).thenReturn(List.of());
        return plugin;
    }

    private static void directGlobalTasks(MockedStatic<Scheduler> scheduler, AtomicBoolean inGlobal) {
        scheduler.when(() -> Scheduler.callTaskAsync(any(Supplier.class))).thenAnswer(call ->
                CompletableFuture.supplyAsync((Supplier<?>) call.getArgument(0)));
        scheduler.when(() -> Scheduler.callTask(any(Supplier.class))).thenAnswer(call -> {
            inGlobal.set(true);
            try {
                return CompletableFuture.completedFuture(((Supplier<?>) call.getArgument(0)).get());
            } finally {
                inGlobal.set(false);
            }
        });
    }

    @Test
    void foliaOfflineReadsMainWorldFileOnBackgroundThreadWithoutPapiOrCachedPlayer() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path mainWorld = directory.resolve("custom-main-world");
        Files.createDirectories(mainWorld.resolve("stats"));
        Files.writeString(mainWorld.resolve("stats").resolve(uuid + ".json"), """
                {"stats": {
                  "minecraft:custom": {"minecraft:play_time": 2400, "minecraft:walk_one_cm": 500},
                  "minecraft:mined": {"minecraft:stone": 12, "minecraft:dirt": 8},
                  "minecraft:crafted": {"minecraft:stick": 4, "minecraft:torch": 16}
                }, "DataVersion": 3465}
                """);
        JavaPlugin plugin = plugin();
        World world = mock(World.class);
        when(world.getWorldFolder()).thenReturn(mainWorld.toFile());
        World nether = mock(World.class);
        when(plugin.getServer().getWorlds()).thenReturn(List.of(world, nether));
        Thread callingThread = Thread.currentThread();
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<PlaceHolderApiManager> papi = mockStatic(PlaceHolderApiManager.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            directGlobalTasks(scheduler, new AtomicBoolean());
            scheduler.when(() -> Scheduler.callTaskAsync(any(Supplier.class))).thenAnswer(call -> {
                Supplier<?> work = call.getArgument(0);
                return CompletableFuture.supplyAsync(() -> {
                    assertNotSame(callingThread, Thread.currentThread());
                    return work.get();
                });
            });
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin, true)) {
                var snapshot = service.captureInfo(uuid, "Alex", "yesterday",
                        "{{time}} {{walk}} {{break}} {{craft}} %custom_world%")
                        .get(5, TimeUnit.SECONDS);
                assertFalse(snapshot.online());
                assertEquals("2分钟 5.00 m 20 20 暂不可用", snapshot.html());
                // 每次离线查询重新读取存档，而不是继续受 512 条插件缓存限制。
                Files.writeString(mainWorld.resolve("stats").resolve(uuid + ".json"), "{\"stats\":{}}");
                assertEquals("0分钟 0cm 0 0", service.captureInfo(uuid, "Alex", "yesterday",
                        "{{time}} {{walk}} {{break}} {{craft}}").get(5, TimeUnit.SECONDS).html());
                bukkit.verifyNoInteractions();
                papi.verifyNoInteractions();
                verifyNoInteractions(nether);
            }
        }
    }

    @Test
    void foliaOfflineInfoReadsActualDeerFoliaPlayersStatsLayout() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path mainWorld = directory.resolve("world");
        Path stats = mainWorld.resolve("players").resolve("stats");
        Files.createDirectories(stats);
        Files.writeString(stats.resolve(uuid + ".json"),
                "{\"stats\":{\"minecraft:custom\":{\"minecraft:play_time\":2400,\"minecraft:walk_one_cm\":500},\"minecraft:mined\":{\"minecraft:stone\":42},\"minecraft:crafted\":{\"minecraft:stick\":7}}}");
        JavaPlugin plugin = plugin();
        World world = mock(World.class);
        when(world.getWorldFolder()).thenReturn(mainWorld.toFile());
        when(plugin.getServer().getWorlds()).thenReturn(List.of(world));
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<PlaceHolderApiManager> papi = mockStatic(PlaceHolderApiManager.class)) {
            directGlobalTasks(scheduler, new AtomicBoolean());
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin, true)) {
                var snapshot = service.captureInfo(uuid, "Alex", "yesterday",
                        "{{time}} {{walk}} {{break}} {{craft}}").get(5, TimeUnit.SECONDS);
                assertFalse(snapshot.online());
                assertEquals("2分钟 5.00 m 42 7", snapshot.html());
                papi.verifyNoInteractions();
            }
        }
    }

    @Test
    void onlineStatisticsAndCustomTemplatePapiRunOnEntityScheduler() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alex");
        when(player.isOnline()).thenReturn(true);
        AtomicBoolean inEntity = new AtomicBoolean();
        when(player.getStatistic(any(Statistic.class))).thenAnswer(call -> {
            assertTrue(inEntity.get(), "统计只能在实体线程读取");
            return call.getArgument(0) == Statistic.PLAY_ONE_MINUTE ? 1200 : 250;
        });
        List<Runnable> entityTasks = new ArrayList<>();
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<PlaceHolderApiManager> papi = mockStatic(PlaceHolderApiManager.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            directGlobalTasks(scheduler, new AtomicBoolean());
            scheduler.when(() -> Scheduler.callTask(eq(player), any(Supplier.class))).thenAnswer(call -> {
                CompletableFuture<Object> result = new CompletableFuture<>();
                Supplier<?> supplier = call.getArgument(1);
                entityTasks.add(() -> {
                    inEntity.set(true);
                    try { result.complete(supplier.get()); }
                    finally { inEntity.set(false); }
                });
                return result;
            });
            papi.when(() -> PlaceHolderApiManager.setPlaceholders(eq(player), anyString())).thenAnswer(call -> {
                assertTrue(inEntity.get(), "完整模板的PAPI也必须在实体线程解析");
                return ((String) call.getArgument(1)).replace("%statistic_mine_block%", "12")
                        .replace("%statistic_craft_item%", "3").replace("%custom_world%", "world");
            });
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin(), true)) {
                service.onJoin(new PlayerJoinEvent(player, ""));
                assertTrue(service.isOnline(uuid));
                assertEquals("Alex", service.onlinePlayers().get(0).name());
                assertThrows(UnsupportedOperationException.class, () -> service.onlinePlayers().clear());
                CompletableFuture<PlayerSnapshotService.InfoSnapshot> capture = service.captureInfo(uuid, "Alex", "yesterday",
                        "{{time}} {{walk}} {{break}} %custom_world% {{status}}");
                assertFalse(capture.isDone());
                entityTasks.remove(0).run();
                var snapshot = capture.get(5, TimeUnit.SECONDS);
                assertTrue(snapshot.online());
                assertTrue(snapshot.html().contains("1分钟 2.50 m 12 world [当前在线]"));
                bukkit.verifyNoInteractions();
            }
        }
    }

    @Test
    void foliaOfflineUsesPersistedStatisticsWithoutCallingBukkitOrPapi() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alex");
        when(player.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(2400);
        when(player.getStatistic(Statistic.WALK_ONE_CM)).thenReturn(500);
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<PlaceHolderApiManager> papi = mockStatic(PlaceHolderApiManager.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            directGlobalTasks(scheduler, new AtomicBoolean());
            try (PlayerSnapshotService first = new PlayerSnapshotService(plugin(), true)) {
                first.onJoin(new PlayerJoinEvent(player, ""));
                first.onQuit(new PlayerQuitEvent(player, ""));
                assertFalse(first.isOnline(uuid));
            }
            assertTrue(Files.exists(directory.resolve("player-statistics-cache.json")));
            try (PlayerSnapshotService reloaded = new PlayerSnapshotService(plugin(), true)) {
                var snapshot = reloaded.captureInfo(uuid, "Alex", "yesterday", "{{time}} {{walk}} {{break}} {{craft}} %custom_world%")
                        .get(5, TimeUnit.SECONDS);
                assertFalse(snapshot.online());
                assertEquals("2分钟 5.00 m 暂不可用 暂不可用 暂不可用", snapshot.html());
                var unknown = reloaded.captureInfo(UUID.randomUUID(), "Other", "yesterday", "{{time}} {{walk}} {{break}} {{craft}}")
                        .get(5, TimeUnit.SECONDS);
                assertEquals("暂不可用 暂不可用 暂不可用 暂不可用", unknown.html());
                bukkit.verifyNoInteractions();
                papi.verifyNoInteractions();
            }
        }
    }

    @Test
    void corruptWorldFileFallsBackToExistingStatisticsWithoutFailingInfo() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path mainWorld = directory.resolve("world");
        Files.createDirectories(mainWorld.resolve("stats"));
        Files.writeString(mainWorld.resolve("stats").resolve(uuid + ".json"), "{truncated");
        JavaPlugin plugin = plugin();
        World world = mock(World.class);
        when(world.getWorldFolder()).thenReturn(mainWorld.toFile());
        when(plugin.getServer().getWorlds()).thenReturn(List.of(world));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(2400);
        when(player.getStatistic(Statistic.WALK_ONE_CM)).thenReturn(500);
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<XLogger> logger = mockStatic(XLogger.class)) {
            directGlobalTasks(scheduler, new AtomicBoolean());
            scheduler.when(() -> Scheduler.callTaskAsync(any(Supplier.class))).thenAnswer(call ->
                    CompletableFuture.completedFuture(((Supplier<?>) call.getArgument(0)).get()));
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin, true)) {
                service.onQuit(new PlayerQuitEvent(player, ""));
                assertEquals("2分钟 5.00 m 暂不可用 暂不可用", service.captureInfo(uuid, "Alex", "yesterday",
                        "{{time}} {{walk}} {{break}} {{craft}}").get(5, TimeUnit.SECONDS).html());
                logger.verify(() -> XLogger.warn(eq("读取玩家 {0} 的存档统计失败，尝试使用缓存: {1}"),
                        eq(uuid), anyString()));
                assertEquals("{truncated", Files.readString(mainWorld.resolve("stats").resolve(uuid + ".json")));
            }
        }
    }

    @Test
    void paperOfflineStatisticsAndPapiUseTheGlobalScheduler() throws Exception {
        UUID uuid = UUID.randomUUID();
        OfflinePlayer player = mock(OfflinePlayer.class);
        AtomicBoolean inGlobal = new AtomicBoolean();
        when(player.getStatistic(any(Statistic.class))).thenAnswer(call -> {
            assertTrue(inGlobal.get());
            return 1200;
        });
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<PlaceHolderApiManager> papi = mockStatic(PlaceHolderApiManager.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            directGlobalTasks(scheduler, inGlobal);
            bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenAnswer(call -> {
                assertTrue(inGlobal.get());
                return player;
            });
            papi.when(() -> PlaceHolderApiManager.setPlaceholders(eq(player), anyString())).thenAnswer(call -> {
                assertTrue(inGlobal.get());
                return ((String) call.getArgument(1)).replace("%custom_rank%", "VIP");
            });
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin(), false)) {
                var snapshot = service.captureInfo(uuid, "Alex", "yesterday", "{{time}} %custom_rank%")
                        .get(5, TimeUnit.SECONDS);
                assertEquals("1分钟 VIP", snapshot.html());
                assertFalse(snapshot.online());
            }
        }
    }

    @Test
    void retiredEntityFallsBackAndDisableCompletesPendingCaptures() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alex");
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class)) {
            directGlobalTasks(scheduler, new AtomicBoolean());
            CompletableFuture<Object> retired = new CompletableFuture<>();
            scheduler.when(() -> Scheduler.callTask(eq(player), any(Supplier.class))).thenReturn(retired);
            try (PlayerSnapshotService service = new PlayerSnapshotService(plugin(), true)) {
                service.onJoin(new PlayerJoinEvent(player, ""));
                var capture = service.captureInfo(uuid, "Alex", "yesterday", "{{time}} %custom_world%");
                retired.completeExceptionally(new Scheduler.PlayerUnavailableException());
                assertEquals("暂不可用 暂不可用", capture.get(5, TimeUnit.SECONDS).html());
                CompletableFuture<Object> stalled = new CompletableFuture<>();
                scheduler.when(() -> Scheduler.callTask(eq(player), any(Supplier.class))).thenReturn(stalled);
                var pending = service.captureInfo(uuid, "Alex", "yesterday", "{{time}}");
                service.close();
                assertTrue(pending.isCancelled());
            }
        }
    }
}
