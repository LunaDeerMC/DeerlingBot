package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.utils.XLogger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** 有界下载玩家头像；网络失败时返回空图片，关闭时取消尚未完成的请求。 */
public final class AvatarManager implements AutoCloseable {
    private static volatile AvatarManager instance;

    record Limits(int connectMillis, int readMillis, int totalMillis, int maxBytes, int maxDimension,
                  int parallelism, int queueCapacity, int cacheCapacity, Duration cacheTtl) {
        Limits {
            if (connectMillis <= 0 || readMillis <= 0 || totalMillis <= 0 || maxBytes <= 0
                    || maxDimension <= 0 || parallelism <= 0 || queueCapacity <= 0 || cacheCapacity <= 0
                    || cacheTtl.isNegative() || cacheTtl.isZero()) {
                throw new IllegalArgumentException("头像下载限制必须为正数");
            }
        }
    }

    private record CachedAvatar(BufferedImage image, long expiresAt) {}

    private final Limits limits;
    private final Function<UUID, URI> endpoint;
    private final LongSupplier clock;
    private final Consumer<String> warning;
    private final HttpClient http;
    private final ThreadPoolExecutor workers;
    private final ScheduledThreadPoolExecutor timeouts;
    private final Map<UUID, Request> pending = new HashMap<>();
    private final LinkedHashMap<UUID, CachedAvatar> cache = new LinkedHashMap<>(16, 0.75f, true);
    private volatile boolean closed;

    public AvatarManager() {
        this(uuid -> URI.create("https://api.mineatar.io/face/" + uuid + "?scale=12"),
                new Limits(1000, 1000, 3000, 512 * 1024, 256, 2, 16, 128, Duration.ofMinutes(10)),
                System::nanoTime, XLogger::warn);
        instance = this;
    }

    AvatarManager(Function<UUID, URI> endpoint, Limits limits, LongSupplier clock, Consumer<String> warning) {
        this.endpoint = endpoint;
        this.limits = limits;
        this.clock = clock;
        this.warning = warning;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(limits.connectMillis()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.workers = new ThreadPoolExecutor(limits.parallelism(), limits.parallelism(), 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(limits.queueCapacity()),
                daemonThreads("deerling-avatar-download"), new ThreadPoolExecutor.AbortPolicy());
        this.timeouts = new ScheduledThreadPoolExecutor(1, daemonThreads("deerling-avatar-timeout"));
        this.timeouts.setRemoveOnCancelPolicy(true);
    }

    private static ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    public static AvatarManager getInstance() {
        AvatarManager current = instance;
        if (current == null) throw new IllegalStateException("AvatarManager is not initialized");
        return current;
    }

    public boolean isClosed() {
        return closed;
    }

    public synchronized CompletableFuture<BufferedImage> fetch(UUID uuid) {
        if (closed) return CompletableFuture.failedFuture(new CancellationException("头像下载器已关闭"));
        long now = clock.getAsLong();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        CachedAvatar cached = cache.get(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached.image());
        Request previous = pending.get(uuid);
        if (previous != null) return previous.result.thenApply(image -> image);

        Request request = new Request(uuid);
        pending.put(uuid, request);
        request.task = new FutureTask<>(() -> {
            download(request);
            return null;
        });
        request.totalTimeout = timeouts.schedule(() -> finish(request, null, "超过总耗时限制"),
                limits.totalMillis(), TimeUnit.MILLISECONDS);
        try {
            workers.execute(request.task);
        } catch (RejectedExecutionException error) {
            finish(request, null, "下载队列已满");
        }
        // 每个调用者拿到独立的等待句柄，取消其中一个不会取消其他人的同 UUID 请求。
        return request.result.thenApply(image -> image);
    }

    private void download(Request request) {
        if (request.stopped) return;
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint.apply(request.uuid))
                    .timeout(Duration.ofMillis(limits.totalMillis()))
                    .header("Accept", "image/*").GET().build();
            armReadTimeout(request);
            CompletableFuture<HttpResponse<byte[]>> response = http.sendAsync(httpRequest, info -> {
                if (info.statusCode() != 200) throw new CompletionException(new IOException("HTTP " + info.statusCode()));
                if (info.headers().firstValueAsLong("Content-Length").orElse(0L) > limits.maxBytes()) {
                    throw new CompletionException(new IOException("头像文件过大"));
                }
                BoundedBody body = new BoundedBody(request);
                request.body = body;
                return body;
            });
            request.httpFuture = response;
            if (request.stopped) response.cancel(true);
            byte[] bytes = response.get().body();
            BufferedImage image = decode(bytes);
            finish(request, image, null);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            finish(request, null, "下载已中断");
        } catch (Exception error) {
            Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
            finish(request, null, cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        }
    }

    private BufferedImage decode(byte[] bytes) throws IOException {
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("响应不是可识别的图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > limits.maxDimension() || height > limits.maxDimension()) {
                    throw new IOException("头像尺寸超过限制");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private synchronized void armReadTimeout(Request request) {
        if (closed || request.stopped) return;
        if (request.readTimeout != null) request.readTimeout.cancel(false);
        long generation = ++request.readGeneration;
        request.readTimeout = timeouts.schedule(
                () -> finish(request, null, "等待头像响应超时", generation),
                limits.readMillis(), TimeUnit.MILLISECONDS);
    }

    private synchronized void stopReadTimeout(Request request) {
        ++request.readGeneration;
        if (request.readTimeout != null) request.readTimeout.cancel(false);
    }

    private void finish(Request request, BufferedImage image, String failure) {
        finish(request, image, failure, 0L);
    }

    private void finish(Request request, BufferedImage image, String failure, long readGeneration) {
        synchronized (this) {
            if (pending.get(request.uuid) != request) return;
            if (readGeneration != 0L && request.readGeneration != readGeneration) return;
            pending.remove(request.uuid);
            request.stopped = true;
            if (image != null && !closed) {
                cache.put(request.uuid, new CachedAvatar(image, clock.getAsLong() + limits.cacheTtl().toNanos()));
                while (cache.size() > limits.cacheCapacity()) cache.remove(cache.keySet().iterator().next());
            }
        }
        // 取消 HTTP 交换可能同步触发响应回调，不能持有管理器锁等待它完成。
        release(request, image == null);
        if (closed) {
            request.result.cancel(false);
            return;
        }
        if (image == null) {
            warning.accept("玩家 " + request.uuid + " 的头像获取失败，改用文字回复：" + failure);
        }
        request.result.complete(image);
    }

    private void release(Request request, boolean cancelDownload) {
        if (request.totalTimeout != null) request.totalTimeout.cancel(false);
        stopReadTimeout(request);
        if (cancelDownload) {
            if (request.httpFuture != null) request.httpFuture.cancel(true);
            if (request.body != null) request.body.cancel();
            if (request.task != null) {
                request.task.cancel(true);
                workers.remove(request.task);
            }
        }
    }

    @Override
    public void close() {
        List<Request> requests;
        synchronized (this) {
            if (closed) return;
            closed = true;
            requests = List.copyOf(pending.values());
            requests.forEach(request -> request.stopped = true);
            pending.clear();
            cache.clear();
        }
        for (Request request : requests) {
            release(request, true);
            request.result.cancel(false);
        }
        workers.shutdownNow();
        timeouts.shutdownNow();
        if (instance == this) instance = null;
    }

    private static final class Request {
        private final UUID uuid;
        private final CompletableFuture<BufferedImage> result = new CompletableFuture<>();
        private FutureTask<Void> task;
        private ScheduledFuture<?> totalTimeout;
        private ScheduledFuture<?> readTimeout;
        private long readGeneration;
        private volatile boolean stopped;
        private volatile CompletableFuture<?> httpFuture;
        private volatile BoundedBody body;

        private Request(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final Request request;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private volatile Flow.Subscription subscription;

        private BoundedBody(Request request) {
            this.request = request;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (request.stopped || result.isDone()) {
                cancel();
                return;
            }
            armReadTimeout(request);
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limits.maxBytes() - bytes.size()) {
                    result.completeExceptionally(new IOException("头像文件过大"));
                    subscription.cancel();
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            armReadTimeout(request);
            subscription.request(1);
        }

        @Override
        public void onError(Throwable error) {
            stopReadTimeout(request);
            result.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            stopReadTimeout(request);
            result.complete(bytes.toByteArray());
        }

        private void cancel() {
            result.cancel(false);
            Flow.Subscription current = subscription;
            if (current != null) current.cancel();
        }
    }
}
