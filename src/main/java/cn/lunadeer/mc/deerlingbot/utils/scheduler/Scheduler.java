package cn.lunadeer.mc.deerlingbot.utils.scheduler;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.isPaper;

public class Scheduler {
    public static Scheduler instance;
    private final JavaPlugin plugin;
    private boolean isPaper = false;
    private volatile boolean stopped;
    private final Set<CompletableFuture<?>> pendingCalls = ConcurrentHashMap.newKeySet();

    public Scheduler(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;
        this.isPaper = isPaper();
    }

    public static void cancelAll() {
        if (instance == null) return;
        instance.stopped = true;
        instance.pendingCalls.forEach(future -> future.cancel(false));
        instance.pendingCalls.clear();
        if (instance.isPaper) {
            instance.plugin.getServer().getGlobalRegionScheduler().cancelTasks(instance.plugin);
            instance.plugin.getServer().getAsyncScheduler().cancelTasks(instance.plugin);
        } else {
            instance.plugin.getServer().getScheduler().cancelTasks(instance.plugin);
        }
    }

    /**
     * Run a task later
     *
     * @param task  The task to run
     * @param delay The delay in ticks (20 ticks = 1 second)
     */
    public static CancellableTask runTaskLater(Runnable task, long delay) {
        if (delay <= 0) {
            runTask(task);
            return null;
        }
        if (instance.isPaper) {
            return new PaperTask(instance.plugin.getServer().getGlobalRegionScheduler().runDelayed(instance.plugin, (plugin) -> task.run(), delay));
        } else {
            return new SpigotTask(instance.plugin.getServer().getScheduler().runTaskLater(instance.plugin, task, delay));
        }
    }

    /**
     * Run a task
     *
     * @param task The task to run
     */
    public static CancellableTask runTask(Runnable task) {
        if (instance.isPaper) {
            return new PaperTask(instance.plugin.getServer().getGlobalRegionScheduler().run(instance.plugin, (plugin) -> task.run()));
        } else {
            return new SpigotTask(instance.plugin.getServer().getScheduler().runTask(instance.plugin, task));
        }
    }

    /** 玩家相关操作在 Folia 的实体线程执行，传统服务端则回到主线程。 */
    public static CancellableTask runTask(Player player, Runnable task) {
        if (instance.isPaper) {
            var scheduled = player.getScheduler().run(instance.plugin, ignored -> task.run(), null);
            return scheduled == null ? null : new PaperTask(scheduled);
        }
        return runTask(task);
    }

    /** 只允许采集少量服务器数据；SQL、网络和渲染仍应留在后台。 */
    public static <T> CompletableFuture<T> callTask(Supplier<T> task) {
        return scheduleCall(null, task, false);
    }

    /** 玩家退出、实体退休、调度失败或停用时也保证结束等待。 */
    public static <T> CompletableFuture<T> callTask(Player player, Supplier<T> task) {
        return scheduleCall(player, task, false);
    }

    /** 将快照后的处理交回后台，并同样保证停用时结束等待。 */
    public static <T> CompletableFuture<T> callTaskAsync(Supplier<T> task) {
        return scheduleCall(null, task, true);
    }

    private static <T> CompletableFuture<T> scheduleCall(Player player, Supplier<T> task, boolean async) {
        Scheduler current = instance;
        if (current == null || current.stopped) {
            return CompletableFuture.failedFuture(new CancellationException("插件调度已停止"));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        current.pendingCalls.add(result);
        result.whenComplete((value, error) -> current.pendingCalls.remove(result));
        result.orTimeout(5, TimeUnit.SECONDS);
        Runnable work = () -> {
            if (result.isDone() || current.stopped) return;
            try {
                result.complete(task.get());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        };
        try {
            if (current.stopped) {
                result.cancel(false);
            } else if (async) {
                if (current.isPaper) current.plugin.getServer().getAsyncScheduler().runNow(current.plugin, ignored -> work.run());
                else current.plugin.getServer().getScheduler().runTaskAsynchronously(current.plugin, work);
            } else if (player != null && current.isPaper) {
                Runnable retired = () -> result.completeExceptionally(new PlayerUnavailableException());
                var scheduled = player.getScheduler().run(current.plugin, ignored -> work.run(), retired);
                if (scheduled == null) retired.run();
            } else if (current.isPaper) {
                current.plugin.getServer().getGlobalRegionScheduler().run(current.plugin, ignored -> work.run());
            } else {
                current.plugin.getServer().getScheduler().runTask(current.plugin, work);
            }
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    public static final class PlayerUnavailableException extends CancellationException {
        public PlayerUnavailableException() {
            super("玩家已退出或实体调度已退休");
        }
    }

    /** 全局线程仅枚举引用，权限检查及发送交回每个玩家的实体线程。 */
    public static CompletableFuture<Void> broadcastMessage(String text) {
        return broadcastMessage(text, () -> true);
    }

    /** 业务关闭后，已进入调度器的旧广播也应丢弃。 */
    public static CompletableFuture<Void> broadcastMessage(String text, BooleanSupplier allowed) {
        Scheduler current = instance;
        if (current == null) return CompletableFuture.failedFuture(new CancellationException("插件调度未初始化"));
        if (!isFolia()) {
            // 传统服务端保留 BroadcastMessageEvent 和广播订阅者语义。
            return callTask(() -> {
                if (allowed.getAsBoolean()) current.plugin.getServer().broadcastMessage(text);
                return null;
            });
        }
        return callTask(() -> {
            if (!allowed.getAsBoolean()) return List.<Player>of();
            current.plugin.getServer().getConsoleSender().sendMessage(text);
            return List.copyOf(current.plugin.getServer().getOnlinePlayers());
        }).thenCompose(players -> CompletableFuture.allOf(players.stream().map(player ->
                callTask(player, () -> {
                    if (allowed.getAsBoolean() && player.isOnline() && player.hasPermission("bukkit.broadcast.user")) player.sendMessage(text);
                    return null;
                }).exceptionally(error -> {
                    if (error instanceof PlayerUnavailableException) return null;
                    throw new java.util.concurrent.CompletionException(error);
                })).toArray(CompletableFuture[]::new)));
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /**
     * Run a task repeatedly
     *
     * @param task   The task to run
     * @param delay  The delay in ticks (20 ticks = 1 second)
     * @param period The period in ticks (20 ticks = 1 second)
     */
    public static CancellableTask runTaskRepeat(Runnable task, long delay, long period) {
        if (instance.isPaper) {
            return new PaperTask(instance.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(instance.plugin, (plugin) -> task.run(), delay, period));
        } else {
            return new SpigotTask(instance.plugin.getServer().getScheduler().runTaskTimer(instance.plugin, task, delay, period));
        }
    }

    /**
     * Run a task later asynchronously
     *
     * @param task  The task to run
     * @param delay The delay in ticks (20 ticks = 1 second)
     */
    public static CancellableTask runTaskLaterAsync(Runnable task, long delay) {
        if (delay <= 0) {
            runTaskAsync(task);
            return null;
        }
        if (instance.isPaper) {
            return new PaperTask(instance.plugin.getServer().getAsyncScheduler().runDelayed(instance.plugin, (plugin) -> task.run(), delay * 50, TimeUnit.MILLISECONDS));
        } else {
            return new SpigotTask(instance.plugin.getServer().getScheduler().runTaskLaterAsynchronously(instance.plugin, task, delay));
        }
    }

    /**
     * Run a task asynchronously
     *
     * @param task The task to run
     */
    public static CancellableTask runTaskAsync(Runnable task) {
        if (instance.isPaper) {
            return new PaperTask(instance.plugin.getServer().getAsyncScheduler().runNow(instance.plugin, (plugin) -> task.run()));
        } else {
            return new SpigotTask(instance.plugin.getServer().getScheduler().runTaskAsynchronously(instance.plugin, task));
        }
    }

    /**
     * Run a task repeatedly asynchronously
     *
     * @param task   The task to run
     * @param delay  The delay in ticks (20 ticks = 1 second)
     * @param period The period in ticks (20 ticks = 1 second)
     */
    public static CancellableTask runTaskRepeatAsync(Runnable task, long delay, long period) {
        if (instance.isPaper) {
            return new PaperTask(instance.plugin.getServer().getAsyncScheduler().runAtFixedRate(instance.plugin, (plugin) -> task.run(), delay * 50, period * 50, TimeUnit.MILLISECONDS));
        } else {
            return new SpigotTask(instance.plugin.getServer().getScheduler().runTaskTimerAsynchronously(instance.plugin, task, delay, period));
        }
    }
}
