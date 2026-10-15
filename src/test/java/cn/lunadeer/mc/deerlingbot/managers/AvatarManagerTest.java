package cn.lunadeer.mc.deerlingbot.managers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AvatarManagerTest {
    private HttpServer server;
    private ExecutorService serverWorkers;
    private AvatarManager manager;
    private byte[] png;
    private volatile HttpHandler handler;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicLong clock = new AtomicLong();
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startLocalServer() throws IOException {
        png = png(32, 32);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverWorkers = Executors.newFixedThreadPool(4);
        server.setExecutor(serverWorkers);
        handler = exchange -> respond(exchange, 200, png);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            try (exchange) {
                handler.handle(exchange);
            } catch (IOException ignored) {
                // 客户端超时主动关闭连接后，服务端仍写入时会收到 IOException。
            }
        });
        server.start();
    }

    @AfterEach
    void closeResources() {
        if (manager != null) manager.close();
        server.stop(0);
        serverWorkers.shutdownNow();
    }

    private void createManager(int readMillis, int totalMillis, int queueCapacity, int cacheCapacity) {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        manager = new AvatarManager(uuid -> base.resolve(uuid.toString()),
                new AvatarManager.Limits(1000, readMillis, totalMillis, 4096, 128,
                        1, queueCapacity, cacheCapacity, Duration.ofSeconds(10)),
                clock::get, warnings::add);
    }

    private void createManager() {
        createManager(800, 2500, 2, 2);
    }

    @Test
    void cachesSuccessAndExpiresAfterTtl() throws Exception {
        createManager();
        UUID uuid = UUID.randomUUID();
        BufferedImage first = manager.fetch(uuid).get(3, TimeUnit.SECONDS);
        assertNotNull(first);
        assertSame(first, manager.fetch(uuid).get(1, TimeUnit.SECONDS));
        assertEquals(1, hits.get());

        clock.set(Duration.ofSeconds(11).toNanos());
        assertNotNull(manager.fetch(uuid).get(3, TimeUnit.SECONDS));
        assertEquals(2, hits.get());
    }

    @Test
    void evictsLeastRecentlyUsedAvatarWhenCacheIsFull() throws Exception {
        createManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        assertNotNull(manager.fetch(first).get(3, TimeUnit.SECONDS));
        assertNotNull(manager.fetch(second).get(3, TimeUnit.SECONDS));
        assertNotNull(manager.fetch(first).get(1, TimeUnit.SECONDS));
        assertNotNull(manager.fetch(third).get(3, TimeUnit.SECONDS));
        assertNotNull(manager.fetch(first).get(1, TimeUnit.SECONDS));
        assertEquals(3, hits.get());
        assertNotNull(manager.fetch(second).get(3, TimeUnit.SECONDS));
        assertEquals(4, hits.get());
    }

    @Test
    void mergesSameUuidRequestsAndOneCallerCannotCancelTheOthers() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        handler = exchange -> {
            entered.countDown();
            await(release);
            respond(exchange, 200, png);
        };
        createManager();
        UUID uuid = UUID.randomUUID();
        CompletableFuture<BufferedImage> first = manager.fetch(uuid);
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        List<CompletableFuture<BufferedImage>> others = new ArrayList<>();
        for (int i = 0; i < 20; i++) others.add(manager.fetch(uuid));
        first.cancel(true);
        release.countDown();
        for (CompletableFuture<BufferedImage> other : others) assertNotNull(other.get(3, TimeUnit.SECONDS));
        assertEquals(1, hits.get());
    }

    @Test
    void failedStatusAndInvalidImageFallBackWithoutPoisoningSuccessCache() throws Exception {
        createManager();
        UUID uuid = UUID.randomUUID();
        handler = exchange -> respond(exchange, 503, new byte[0]);
        assertNull(manager.fetch(uuid).get(3, TimeUnit.SECONDS));
        handler = exchange -> respond(exchange, 200, "not an image".getBytes());
        assertNull(manager.fetch(uuid).get(3, TimeUnit.SECONDS));
        handler = exchange -> respond(exchange, 200, png);
        assertNotNull(manager.fetch(uuid).get(3, TimeUnit.SECONDS));
        assertEquals(3, hits.get());
        assertEquals(2, warnings.size());
    }

    @Test
    void rejectsOversizedDeclaredAndChunkedResponses() throws Exception {
        createManager();
        handler = exchange -> {
            exchange.sendResponseHeaders(200, 5000);
            exchange.getResponseBody().write(new byte[5000]);
        };
        assertNull(manager.fetch(UUID.randomUUID()).get(3, TimeUnit.SECONDS));
        handler = exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[5000]);
        };
        assertNull(manager.fetch(UUID.randomUUID()).get(3, TimeUnit.SECONDS));
        assertTrue(warnings.stream().allMatch(message -> message.contains("过大")));
    }

    @Test
    void rejectsOversizedImageBeforeDecodingPixels() throws Exception {
        createManager();
        byte[] tooWide = png(129, 1);
        handler = exchange -> respond(exchange, 200, tooWide);
        assertNull(manager.fetch(UUID.randomUUID()).get(3, TimeUnit.SECONDS));
        assertTrue(warnings.get(0).contains("尺寸"));
    }

    @Test
    void slowHeadersTimeOutAndReleaseWorkerForTheNextRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        handler = exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("000000000001")) {
                entered.countDown();
                await(release);
            }
            respond(exchange, 200, png);
        };
        createManager(350, 2000, 2, 2);
        CompletableFuture<BufferedImage> slow = manager.fetch(new UUID(0, 1));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertNull(slow.get(1500, TimeUnit.MILLISECONDS));
        assertTrue(warnings.get(0).contains("响应超时"));
        assertNotNull(manager.fetch(new UUID(0, 2)).get(2, TimeUnit.SECONDS));
        release.countDown();
    }

    @Test
    void stalledBodyTimesOutAndReleasesWorker() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        handler = exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("000000000001")) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(png, 0, 8);
                exchange.getResponseBody().flush();
                entered.countDown();
                await(release);
            } else {
                respond(exchange, 200, png);
            }
        };
        createManager(350, 2000, 2, 2);
        CompletableFuture<BufferedImage> slow = manager.fetch(new UUID(0, 1));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertNull(slow.get(1500, TimeUnit.MILLISECONDS));
        assertNotNull(manager.fetch(new UUID(0, 2)).get(2, TimeUnit.SECONDS));
        release.countDown();
    }

    @Test
    void continuousSlowDataStillHitsTotalDeadlineAndDisconnects() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        handler = exchange -> {
            if (!exchange.getRequestURI().getPath().endsWith("000000000001")) {
                respond(exchange, 200, png);
                return;
            }
            exchange.sendResponseHeaders(200, 0);
            try {
                for (int i = 0; i < 30; i++) {
                    exchange.getResponseBody().write(1);
                    exchange.getResponseBody().flush();
                    try {
                        Thread.sleep(80);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (IOException error) {
                disconnected.countDown();
            }
        };
        createManager(400, 900, 2, 2);
        assertNull(manager.fetch(new UUID(0, 1)).get(2, TimeUnit.SECONDS));
        assertTrue(warnings.get(0).contains("总耗时"));
        assertNotNull(manager.fetch(new UUID(0, 2)).get(2, TimeUnit.SECONDS));
        assertTrue(disconnected.await(2, TimeUnit.SECONDS), "总超时必须取消底层 HTTP 连接");
    }

    @Test
    void boundedQueueImmediatelyRejectsExcessWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        handler = exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("000000000001")) {
                entered.countDown();
                await(release);
            }
            respond(exchange, 200, png);
        };
        createManager(1500, 2500, 1, 2);
        CompletableFuture<BufferedImage> first = manager.fetch(new UUID(0, 1));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        CompletableFuture<BufferedImage> second = manager.fetch(new UUID(0, 2));
        assertNull(manager.fetch(new UUID(0, 3)).get(100, TimeUnit.MILLISECONDS));
        assertTrue(warnings.get(0).contains("队列已满"));
        assertEquals(1, hits.get());
        release.countDown();
        assertNotNull(first.get(3, TimeUnit.SECONDS));
        assertNotNull(second.get(3, TimeUnit.SECONDS));
        assertEquals(2, hits.get());
    }

    @Test
    void totalDeadlineIncludesTimeWaitingInTheQueue() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        handler = exchange -> {
            if (!exchange.getRequestURI().getPath().endsWith("000000000003")) {
                entered.countDown();
                await(release);
            }
            respond(exchange, 200, png);
        };
        createManager(2000, 800, 2, 2);
        CompletableFuture<BufferedImage> first = manager.fetch(new UUID(0, 1));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        CompletableFuture<BufferedImage> queued = manager.fetch(new UUID(0, 2));

        assertNull(first.get(1500, TimeUnit.MILLISECONDS));
        assertNull(queued.get(400, TimeUnit.MILLISECONDS), "排队不能重新获得完整下载期限");
        assertEquals(2, warnings.size());
        assertTrue(warnings.stream().allMatch(message -> message.contains("总耗时")));
        assertNotNull(manager.fetch(new UUID(0, 3)).get(2, TimeUnit.SECONDS));
        release.countDown();
    }

    @Test
    void closeCancelsActiveAndQueuedRequestsWithoutReplyCallbacks() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        handler = exchange -> {
            entered.countDown();
            await(release);
            respond(exchange, 200, png);
        };
        createManager();
        AtomicInteger replies = new AtomicInteger();
        CompletableFuture<BufferedImage> first = manager.fetch(UUID.randomUUID());
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        CompletableFuture<BufferedImage> second = manager.fetch(UUID.randomUUID());
        first.thenAccept(image -> replies.incrementAndGet());
        second.thenAccept(image -> replies.incrementAndGet());

        manager.close();
        manager.close();
        release.countDown();

        assertTrue(first.isCompletedExceptionally());
        assertTrue(second.isCompletedExceptionally());
        assertTrue(manager.fetch(UUID.randomUUID()).isCompletedExceptionally());
        assertEquals(0, replies.get());
        assertEquals(1, hits.get());
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("测试服务等待超时");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException(error);
        }
    }

    private static void respond(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }
}
