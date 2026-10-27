package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.*;

/**
 * Provides online presence and immutable /info statistics without synchronous
 * world-file IO or Bukkit/PAPI statistics calls on Folia region/global threads.
 *
 * Statistics are read from the on-disk vanilla JSON snapshot for BOTH online
 * and offline players. For online players, values reflect the most recent disk
 * save, not unsaved in-memory changes. Never force-save players from /info.
 */
public final class PlayerSnapshotService implements Listener, AutoCloseable {
    private static volatile PlayerSnapshotService instance;
    private static final String UNAVAILABLE = "暂不可用";

    private final Map<UUID, OnlineEntry> online = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final Path worldDirectory;
    private volatile boolean closed;

    public record OnlinePlayer(UUID uuid, String name) { }

    /** Immutable display values; only name/presence are not from stats JSON. */
    public record InfoSnapshot(boolean online, String name, String lastJoin,
                               String time, String walk, String mined, String crafted) {
        public String summary() { return String.join(" ", time, walk, mined, crafted); }
    }

    private record OnlineEntry(Player player, OnlinePlayer snapshot) { }

    public PlayerSnapshotService(JavaPlugin plugin) {
        this.worldDirectory = plugin.getServer().getWorlds().stream()
                .filter(world -> world.getEnvironment() == org.bukkit.World.Environment.NORMAL)
                .findFirst()
                .or(() -> plugin.getServer().getWorlds().stream().findFirst())
                .map(world -> world.getWorldFolder().toPath())
                .orElse(null);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        instance = this;

        // Load presence snapshots for players already online on plugin enable/reload.
        // Bukkit player references stay on the entity's owning thread.
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
        PlayerSnapshotService service = instance;
        if (service == null) throw new IllegalStateException("玩家快照服务尚未初始化");
        return service;
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
        // No player.getStatistic() and no synchronous statistics-file read.
    }

    public CompletableFuture<InfoSnapshot> captureInfo(UUID uuid, String name, String lastJoin) {
        if (closed) return CompletableFuture.failedFuture(new CancellationException("玩家快照服务已关闭"));
        // This is a thread-safe presence snapshot, not a Bukkit player-statistics query.
        boolean currentlyOnline = online.containsKey(uuid);
        CompletableFuture<InfoSnapshot> capture = Scheduler.callTaskAsync(() -> {
            if (closed) throw new CancellationException("玩家快照服务已关闭");
            try {
                WorldStatisticsReader.Statistics data = WorldStatisticsReader.readFromWorld(worldDirectory, uuid);
                if (closed) throw new CancellationException("玩家快照服务已关闭");
                if (data != null) {
                    return new InfoSnapshot(currentlyOnline, name, lastJoin,
                            timeConverter(data.playedTicks()),
                            distanceConverter(data.walkedCentimeters()),
                            decimalConverter(data.minedBlocks()),
                            decimalConverter(data.craftedItems()));
                }
            } catch (IOException e) {
                XLogger.warn("读取玩家 {0} 的存档统计失败: {1}", uuid, e.getMessage());
            }
            // Never silently substitute stale plugin cache values for the disk JSON.
            return new InfoSnapshot(currentlyOnline, name, lastJoin,
                    UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE);
        });
        pending.add(capture);
        capture.orTimeout(5, TimeUnit.SECONDS);
        capture.whenComplete((result, error) -> pending.remove(capture));
        if (closed) capture.cancel(false);
        return capture;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        HandlerList.unregisterAll(this);
        pending.forEach(future -> future.cancel(false));
        pending.clear();
        online.clear();
        if (instance == this) instance = null;
    }
}
