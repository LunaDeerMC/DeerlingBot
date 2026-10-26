package cn.lunadeer.mc.deerlingbot.managers;

import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Single bounded off-thread Java2D renderer.
 * Limits peak native-image memory rather than caching full-size 1440x1656 pictures.
 */
public final class InfoImageService implements AutoCloseable {
    private static volatile InfoImageService instance;
    private final InfoCardRenderer renderer;
    private final ThreadPoolExecutor executor;
    private final Set<CompletableFuture<BufferedImage>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public InfoImageService() {
        renderer = new InfoCardRenderer();
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4),
                runnable -> {
                    Thread worker = new Thread(runnable, "DeerlingBot-java2d-renderer");
                    worker.setDaemon(true);
                    return worker;
                }, new ThreadPoolExecutor.AbortPolicy());
        instance = this;
    }

    public static InfoImageService getInstance() {
        InfoImageService current = instance;
        if (current == null) throw new IllegalStateException("图片服务尚未初始化");
        return current;
    }

    public CompletableFuture<BufferedImage> render(PlayerSnapshotService.InfoSnapshot snapshot, BufferedImage avatar) {
        if (closed) return CompletableFuture.failedFuture(new CancellationException("图片服务已停用"));
        CompletableFuture<BufferedImage> future = new CompletableFuture<>();
        pending.add(future);
        if (closed) {
            pending.remove(future);
            future.cancel(false);
            return future;
        }
        try {
            executor.execute(() -> {
                try {
                    if (!closed && !future.isCancelled()) {
                        BufferedImage image = renderer.render(snapshot, avatar);
                        if (closed) future.cancel(false);
                        else future.complete(image);
                    }
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                } finally {
                    pending.remove(future);
                }
            });
        } catch (RejectedExecutionException ex) {
            pending.remove(future);
            if (closed) future.cancel(false);
            else future.completeExceptionally(ex);
        }
        return future;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        pending.forEach(future -> future.cancel(false));
        pending.clear();
        executor.shutdownNow();
        if (instance == this) instance = null;
    }
}
