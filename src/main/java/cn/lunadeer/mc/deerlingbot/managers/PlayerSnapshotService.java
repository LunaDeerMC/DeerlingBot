package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import com.alibaba.fastjson2.JSONObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.*;

/** 将玩家状态和占位符在所属线程转换为不可变数据，后台任务不保留玩家对象。 */
public final class PlayerSnapshotService implements Listener, AutoCloseable {
    private static volatile PlayerSnapshotService instance;
    private static final Object CACHE_OWNER_LOCK = new Object();
    private static final String UNAVAILABLE = "暂不可用";
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("%[A-Za-z0-9]+_[^%\\r\\n]+%");
    private static final int MAX_STATISTICS = 512;
    private final boolean folia;
    private final Map<UUID, OnlineEntry> online = new ConcurrentHashMap<>();
    private final Map<UUID, StatisticsSnapshot> statistics = new LinkedHashMap<>();
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final Path cacheFile;
    private final Path statisticsDirectory;
    private final ThreadPoolExecutor cacheWriter;
    private final CompletableFuture<Void> cacheLoaded = new CompletableFuture<>();
    private volatile boolean closed;

    public record OnlinePlayer(UUID uuid, String name) { }
    public record InfoSnapshot(boolean online, String html) { }
    private record OnlineEntry(Player player, OnlinePlayer snapshot) { }
    private record StatisticsSnapshot(int playedTicks, int walkedCentimeters) { }

    public PlayerSnapshotService(JavaPlugin plugin) {
        this(plugin, Scheduler.isFolia());
    }

    PlayerSnapshotService(JavaPlugin plugin, boolean folia) {
        this.folia = folia;
        this.cacheFile = plugin.getDataFolder().toPath().resolve("player-statistics-cache.json");
        // 原版玩家统计统一保存在主世界，不能累加下界、末地或其他世界的副本。
        var worlds = plugin.getServer().getWorlds();
        this.statisticsDirectory = worlds.isEmpty() ? null : worlds.get(0).getWorldFolder().toPath().resolve("stats");
        // 只保留最新一次待写入任务，避免频繁上下线积累磁盘任务。
        cacheWriter = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, "DeerlingBot-statistics-cache");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
        cacheWriter.execute(() -> {
            try {
                loadStatistics();
            } finally {
                cacheLoaded.complete(null);
            }
        });
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        synchronized (CACHE_OWNER_LOCK) {
            instance = this;
        }
        // 启用时已有玩家也补入快照；全局线程只取得引用，名字和状态由实体线程读取。
        Scheduler.callTask(() -> List.copyOf(plugin.getServer().getOnlinePlayers())).thenAccept(players -> {
            if (closed) return;
            for (Player player : players) {
                Scheduler.callTask(player, () -> {
                    if (!closed && player.isOnline()) recordOnline(player);
                    return null;
                });
            }
        });
    }

    public static PlayerSnapshotService getInstance() {
        PlayerSnapshotService current = instance;
        if (current == null) throw new IllegalStateException("玩家快照服务尚未初始化");
        return current;
    }

    public boolean isOnline(UUID uuid) {
        return !closed && online.containsKey(uuid);
    }

    public List<OnlinePlayer> onlinePlayers() {
        return online.values().stream().map(OnlineEntry::snapshot)
                .sorted(Comparator.comparing(OnlinePlayer::name)).toList();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!closed) recordOnline(event.getPlayer());
    }

    private void recordOnline(Player player) {
        UUID uuid = player.getUniqueId();
        online.put(uuid, new OnlineEntry(player, new OnlinePlayer(uuid, player.getName())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (closed) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        online.computeIfPresent(uuid, (ignored, entry) -> entry.player() == player ? null : entry);
        // 退出事件仍在玩家所属线程，只读取基础统计，不生成模板或运行任意 PAPI 扩展。
        remember(uuid, readStatistics(player));
        saveLater();
    }

    public CompletableFuture<InfoSnapshot> captureInfo(UUID uuid, String name, String lastJoin, String template) {
        if (closed) return CompletableFuture.failedFuture(new CancellationException("玩家快照服务已关闭"));
        OnlineEntry entry = online.get(uuid);
        CompletableFuture<InfoSnapshot> capture;
        if (entry != null) {
            capture = Scheduler.callTask(entry.player(), () -> {
                if (closed) throw new CancellationException("玩家快照服务已关闭");
                if (!entry.player().isOnline()) throw new Scheduler.PlayerUnavailableException();
                return capturePlayer(uuid, entry.player(), true, name, lastJoin, template);
            }).exceptionallyCompose(error -> {
                if (error instanceof Scheduler.PlayerUnavailableException && !closed) {
                    return captureOffline(uuid, name, lastJoin, template);
                }
                return CompletableFuture.failedFuture(error);
            });
        } else {
            capture = captureOffline(uuid, name, lastJoin, template);
        }
        pending.add(capture);
        capture.orTimeout(5, TimeUnit.SECONDS);
        capture.whenComplete((result, error) -> pending.remove(capture));
        if (closed) capture.cancel(false);
        return capture;
    }

    private CompletableFuture<InfoSnapshot> captureOffline(UUID uuid, String name, String lastJoin, String template) {
        if (folia) {
            // 文件 IO 留在后台；即使查询途中玩家上线，也不访问跨区域的玩家对象。
            return Scheduler.callTaskAsync(() -> {
                if (closed) throw new CancellationException("玩家快照服务已关闭");
                try {
                    WorldStatisticsReader.Statistics saved = WorldStatisticsReader.read(statisticsDirectory, uuid);
                    if (closed) throw new CancellationException("玩家快照服务已关闭");
                    if (saved != null) {
                        return new InfoSnapshot(false, fillTemplate(template, uuid, name, lastJoin, false,
                                new StatisticsSnapshot(saved.playedTicks(), saved.walkedCentimeters()),
                                decimalConverter(saved.minedBlocks()), decimalConverter(saved.craftedItems()), null));
                    }
                } catch (IOException e) {
                    XLogger.warn("读取玩家 {0} 的存档统计失败，尝试使用缓存: {1}", uuid, e.getMessage());
                }
                cacheLoaded.join();
                if (closed) throw new CancellationException("玩家快照服务已关闭");
                StatisticsSnapshot snapshot;
                synchronized (statistics) {
                    snapshot = statistics.get(uuid);
                }
                return new InfoSnapshot(false,
                        fillTemplate(template, uuid, name, lastJoin, false, snapshot, UNAVAILABLE, UNAVAILABLE, null));
            });
        }
        return Scheduler.callTask(() -> {
            if (closed) throw new CancellationException("玩家快照服务已关闭");
            // Paper 的离线统计可能读取磁盘，但其在线切换及 PAPI 必须由主线程串行保护。
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            return capturePlayer(uuid, player, player.isOnline(), name, lastJoin, template);
        });
    }

    private InfoSnapshot capturePlayer(UUID uuid, OfflinePlayer player, boolean isOnline,
                                       String name, String lastJoin, String template) {
        StatisticsSnapshot snapshot = readStatistics(player);
        remember(uuid, snapshot);
        String mined = numericPlaceholder(player, "%statistic_mine_block%");
        String crafted = numericPlaceholder(player, "%statistic_craft_item%");
        return new InfoSnapshot(isOnline,
                fillTemplate(template, uuid, name, lastJoin, isOnline, snapshot, mined, crafted, player));
    }

    private static StatisticsSnapshot readStatistics(OfflinePlayer player) {
        return new StatisticsSnapshot(player.getStatistic(Statistic.PLAY_ONE_MINUTE),
                player.getStatistic(Statistic.WALK_ONE_CM));
    }

    private static String numericPlaceholder(OfflinePlayer player, String placeholder) {
        try {
            return decimalConverter(Integer.parseInt(PlaceHolderApiManager.setPlaceholders(player, placeholder)));
        } catch (NumberFormatException e) {
            return UNAVAILABLE;
        }
    }

    private static String fillTemplate(String template, UUID uuid, String name, String lastJoin, boolean isOnline,
                                       StatisticsSnapshot stats, String mined, String crafted, OfflinePlayer player) {
        Map<String, String> values = Map.of(
                "nickname", name, "status", isOnline ? "[当前在线]" : "[上次在线] " + lastJoin,
                "avatar", "https://api.mineatar.io/face/" + uuid + "?scale=12",
                "time", stats == null ? UNAVAILABLE : timeConverter(stats.playedTicks()),
                "walk", stats == null ? UNAVAILABLE : distanceConverter(stats.walkedCentimeters()),
                "break", mined, "craft", crafted);
        String result = template;
        for (var value : values.entrySet()) result = result.replace("{{" + value.getKey() + "}}", value.getValue());
        // 解析完整模板，保留自定义 PAPI 占位符；离线 Folia 不调用无法确定线程归属的扩展。
        if (player != null) result = PlaceHolderApiManager.setPlaceholders(player, result);
        return UNRESOLVED_PLACEHOLDER.matcher(result).replaceAll(UNAVAILABLE);
    }

    private void remember(UUID uuid, StatisticsSnapshot value) {
        synchronized (statistics) {
            statistics.remove(uuid);
            statistics.put(uuid, value);
            while (statistics.size() > MAX_STATISTICS) statistics.remove(statistics.keySet().iterator().next());
        }
    }

    private void saveLater() {
        if (!cacheWriter.isShutdown()) cacheWriter.execute(this::saveStatistics);
    }

    private void loadStatistics() {
        try {
            if (!Files.exists(cacheFile) || Files.size(cacheFile) > 256 * 1024) return;
            JSONObject cached = JSONObject.parseObject(Files.readString(cacheFile));
            synchronized (statistics) {
                for (String key : cached.keySet()) {
                    if (statistics.size() >= MAX_STATISTICS) break;
                    JSONObject data = cached.getJSONObject(key);
                    statistics.putIfAbsent(UUID.fromString(key), new StatisticsSnapshot(
                            Math.max(0, data.getIntValue("playedTicks")), Math.max(0, data.getIntValue("walkedCentimeters"))));
                }
            }
        } catch (Exception e) {
            XLogger.warn("读取玩家统计缓存失败，将等待新的在线快照: {0}", e.getMessage());
        }
    }

    private void saveStatistics() {
        Path temporary = null;
        try {
            JSONObject data = new JSONObject();
            synchronized (statistics) {
                statistics.forEach((uuid, value) -> data.put(uuid.toString(), value));
            }
            Files.createDirectories(cacheFile.getParent());
            temporary = Files.createTempFile(cacheFile.getParent(), ".statistics-", ".json");
            Files.writeString(temporary, data.toJSONString());
            synchronized (CACHE_OWNER_LOCK) {
                // 旧服务超时退出后不得覆盖新服务的缓存。
                if (instance != this) return;
                try {
                    Files.move(temporary, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            XLogger.warn("写入玩家统计缓存失败: {0}", e.getMessage());
        } finally {
            if (temporary != null) try {
                Files.deleteIfExists(temporary);
            } catch (Exception e) {
                XLogger.warn("清理玩家统计缓存临时文件失败: {0}", e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        HandlerList.unregisterAll(this);
        pending.forEach(future -> future.cancel(false));
        pending.clear();
        online.clear();
        saveLater();
        cacheWriter.shutdown();
        try {
            if (!cacheWriter.awaitTermination(1, TimeUnit.SECONDS)) XLogger.warn("玩家统计缓存尚未写完，本次停用不再继续等待");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (CACHE_OWNER_LOCK) {
            if (instance == this) instance = null;
        }
    }
}
