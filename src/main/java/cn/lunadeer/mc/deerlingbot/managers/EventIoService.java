package cn.lunadeer.mc.deerlingbot.managers;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 按 QQ 保序的有界后台队列；事件线程只提交任务，不执行 JDBC。 */
public final class EventIoService implements AutoCloseable {
    private static volatile EventIoService instance;
    private static final ThreadLocal<EventIoService> CURRENT = new ThreadLocal<>();
    private final Object lock = new Object();
    private final ThreadPoolExecutor[] workers;
    private final Map<Long, Work> lastByUser = new HashMap<>();
    private final Set<Work> deletions = new HashSet<>();
    private final Logger logger;
    private final int reserved;
    private final Duration closeTimeout;
    private volatile boolean open = true;
    private long lastWarning;

    public EventIoService(JavaPlugin plugin) {
        this(plugin.getLogger(), 4, 128, 16, Duration.ofSeconds(5));
        instance = this;
    }

    EventIoService(Logger logger, int stripes, int capacity, int reserved, Duration closeTimeout) {
        if (stripes < 1 || capacity < 1 || reserved < 0 || reserved >= capacity) {
            throw new IllegalArgumentException("无效的事件队列容量");
        }
        this.logger = logger;
        this.reserved = reserved;
        this.closeTimeout = closeTimeout;
        workers = new ThreadPoolExecutor[stripes];
        AtomicInteger sequence = new AtomicInteger();
        for (int index = 0; index < stripes; index++) {
            workers[index] = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(capacity), action -> {
                Thread thread = new Thread(action, "DeerlingBot-event-io-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
        }
    }

    public static EventIoService getInstance() {
        EventIoService current = instance;
        if (current == null) throw new IllegalStateException("事件 I/O 队列尚未初始化");
        return current;
    }

    public boolean isOpen() {
        return open;
    }

    /** 供发消息入口拦截停用后仍返回的旧后台任务。 */
    public static boolean canSendFromCurrentTask() {
        EventIoService current = CURRENT.get();
        return current == null || current.isOpen();
    }

    public boolean submitMessage(long userId, Runnable action) {
        return submit(userId, action, false, false);
    }

    public boolean submitCommand(long userId, Runnable action) {
        return submit(userId, action, false, true);
    }

    public boolean submitDeletion(long userId, Runnable action) {
        return submit(userId, action, true, true);
    }

    private boolean submit(long userId, Runnable action, boolean deletion, boolean priority) {
        synchronized (lock) {
            if (!open) return false;
            Work previous = lastByUser.get(userId);
            // 只合并连续且尚未开始的删除；中间的指令或转发会打断合并。
            if (deletion && previous != null && previous.deletion && !previous.started) return true;
            ThreadPoolExecutor worker = workers[Math.floorMod(Long.hashCode(userId), workers.length)];
            if (!priority && worker.getQueue().remainingCapacity() <= reserved) {
                warnBusy();
                return false;
            }
            if (worker.getQueue().remainingCapacity() == 0) {
                // 管理事件优先逐出尚未开始的聊天，不逐出已经接受的指令/退群任务。
                for (Runnable queued : worker.getQueue()) {
                    Work work = (Work) queued;
                    if (!work.priority && worker.getQueue().remove(work)) {
                        lastByUser.remove(work.userId, work);
                        warnBusy();
                        break;
                    }
                }
            }
            if (worker.getQueue().remainingCapacity() == 0) {
                if (deletion) logger.severe("事件队列已满，QQ " + userId + " 的退群解绑未被接受，请检查数据库并人工解绑。");
                else warnBusy();
                return false;
            }
            Work work = new Work(userId, action, deletion, priority);
            lastByUser.put(userId, work);
            if (deletion) deletions.add(work);
            worker.execute(work);
            return true;
        }
    }

    private void warnBusy() {
        long now = System.nanoTime();
        if (lastWarning == 0 || now - lastWarning >= TimeUnit.SECONDS.toNanos(10)) {
            lastWarning = now;
            logger.warning("事件 I/O 队列繁忙，部分聊天或指令未被处理，请稍后重试并检查数据库延迟。");
        }
    }

    @Override
    public void close() {
        int cancelled = 0;
        synchronized (lock) {
            if (!open) return;
            open = false;
            for (ThreadPoolExecutor worker : workers) {
                for (Runnable queued : worker.getQueue()) {
                    Work work = (Work) queued;
                    if (!work.deletion && worker.getQueue().remove(work)) {
                        lastByUser.remove(work.userId, work);
                        cancelled++;
                    }
                }
                worker.shutdown();
            }
        }
        if (cancelled > 0) logger.info("停用时取消了 " + cancelled + " 个尚未执行的聊天或指令任务。");
        long deadline = System.nanoTime() + closeTimeout.toNanos();
        for (ThreadPoolExecutor worker : workers) {
            try {
                long remaining = deadline - System.nanoTime();
                if (remaining > 0) worker.awaitTermination(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (lock) {
            for (Work work : deletions) {
                logger.severe("停用等待超时，QQ " + work.userId + " 的退群解绑尚未完成，请核对绑定数据。");
            }
            for (ThreadPoolExecutor worker : workers) worker.shutdownNow();
            lastByUser.clear();
            deletions.clear();
        }
    }

    private final class Work implements Runnable {
        final long userId;
        final Runnable action;
        final boolean deletion;
        final boolean priority;
        boolean started;

        Work(long userId, Runnable action, boolean deletion, boolean priority) {
            this.userId = userId;
            this.action = action;
            this.deletion = deletion;
            this.priority = priority;
        }

        @Override
        public void run() {
            synchronized (lock) {
                started = true;
                if (!open && !deletion) {
                    lastByUser.remove(userId, this);
                    deletions.remove(this);
                    return;
                }
            }
            CURRENT.set(EventIoService.this);
            try {
                action.run();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "QQ " + userId + " 的后台事件处理失败", e);
            } finally {
                CURRENT.remove();
                synchronized (lock) {
                    lastByUser.remove(userId, this);
                    deletions.remove(this);
                }
            }
        }
    }
}
