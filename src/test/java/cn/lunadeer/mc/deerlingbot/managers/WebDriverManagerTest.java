package cn.lunadeer.mc.deerlingbot.managers;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WebDriverManagerTest {
    private static BufferedImage image() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for renderer");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void serializesRenderingAndRejectsRequestsBeyondQueueCapacity() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger renders = new AtomicInteger();
        try (WebDriverManager manager = new WebDriverManager(1, 0, Duration.ZERO)) {
            CompletableFuture<BufferedImage> first = manager.render(UUID.randomUUID(), () -> {
                renders.incrementAndGet();
                started.countDown();
                await(release);
                return image();
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            CompletableFuture<BufferedImage> second = manager.render(UUID.randomUUID(), () -> {
                renders.incrementAndGet();
                return image();
            });
            CompletableFuture<BufferedImage> rejected = manager.render(UUID.randomUUID(), WebDriverManagerTest::image);
            ExecutionException error = assertThrows(ExecutionException.class, () -> rejected.get(5, TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class, error.getCause());
            assertEquals(1, renders.get());
            release.countDown();
            assertNotNull(first.get(5, TimeUnit.SECONDS));
            assertNotNull(second.get(5, TimeUnit.SECONDS));
            assertEquals(2, renders.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void sharesPendingWorkAndCachesImagesWithAnEntryLimit() throws Exception {
        UUID firstId = UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (WebDriverManager manager = new WebDriverManager(1, 1, Duration.ofMinutes(1))) {
            CompletableFuture<BufferedImage> first = manager.render(firstId, () -> {
                started.countDown();
                await(release);
                return image();
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertSame(first, manager.render(firstId, () -> fail("Duplicate request must share rendering")));
            release.countDown();
            BufferedImage rendered = first.get(5, TimeUnit.SECONDS);
            assertSame(rendered, manager.render(firstId, () -> fail("Cached image must be reused")).get(5, TimeUnit.SECONDS));
            manager.render(UUID.randomUUID(), WebDriverManagerTest::image).get(5, TimeUnit.SECONDS);
            BufferedImage replacement = image();
            assertSame(replacement, manager.render(firstId, () -> replacement).get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test
    void expiredAndFailedImagesAreRenderedAgain() throws Exception {
        UUID player = UUID.randomUUID();
        try (WebDriverManager manager = new WebDriverManager(1, 1, Duration.ZERO)) {
            BufferedImage first = manager.render(player, WebDriverManagerTest::image).get(5, TimeUnit.SECONDS);
            assertNotSame(first, manager.render(player, WebDriverManagerTest::image).get(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> manager.render(player, () -> {
                throw new IllegalStateException("Browser failed");
            }).get(5, TimeUnit.SECONDS));
            assertNotNull(manager.render(player, WebDriverManagerTest::image).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void closeCancelsPendingWorkAndRejectsNewRequests() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger queuedRenders = new AtomicInteger();
        try (WebDriverManager manager = new WebDriverManager(1, 1, Duration.ofSeconds(30))) {
            CompletableFuture<BufferedImage> active = manager.render(UUID.randomUUID(), () -> {
                started.countDown();
                await(release);
                return image();
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            CompletableFuture<BufferedImage> queued = manager.render(UUID.randomUUID(), () -> {
                queuedRenders.incrementAndGet();
                return image();
            });
            manager.close();
            assertTrue(active.isCancelled());
            assertTrue(queued.isCancelled());
            assertTrue(manager.render(UUID.randomUUID(), WebDriverManagerTest::image).isCancelled());
            assertEquals(0, queuedRenders.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void closeDuringConstructionWaitsForWorkerToDisposeLateBrowserExactlyOnce() throws Exception {
        CountDownLatch constructing = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch allowConstruction = new CountDownLatch(1);
        AtomicInteger screenshots = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AtomicReference<Thread> creator = new AtomicReference<>();
        AtomicReference<Thread> disposer = new AtomicReference<>();
        try (WebDriverManager manager = new WebDriverManager(1, 0, Duration.ZERO, () -> {
            creator.set(Thread.currentThread());
            constructing.countDown();
            awaitIgnoringInterrupts(allowConstruction, interrupted);
            return new WebDriverManager.Browser() {
                public BufferedImage takeScreenshot(File file, String id) {
                    screenshots.incrementAndGet();
                    return image();
                }

                public void close() {
                    disposer.set(Thread.currentThread());
                    closes.incrementAndGet();
                }
            };
        })) {
            CompletableFuture<BufferedImage> image = manager.render(UUID.randomUUID(),
                    () -> manager.takeScreenshot(new File("unused.html"), "card"));
            assertTrue(constructing.await(5, TimeUnit.SECONDS));
            CompletableFuture<Void> shutdown = CompletableFuture.runAsync(manager::close);
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            assertFalse(shutdown.isDone());
            allowConstruction.countDown();
            shutdown.get(5, TimeUnit.SECONDS);
            manager.close();
            assertTrue(image.isCancelled());
            assertEquals(0, screenshots.get());
            assertEquals(1, closes.get());
            assertSame(creator.get(), disposer.get());
        } finally {
            allowConstruction.countDown();
        }
    }

    @Test
    void closeDuringBrowserDisposalIsBoundedAndReloadWaitsForItsPermit() throws Exception {
        CountDownLatch disposalStarted = new CountDownLatch(1);
        CountDownLatch disposalInterrupted = new CountDownLatch(1);
        CountDownLatch allowDisposal = new CountDownLatch(1);
        CountDownLatch replacementStarted = new CountDownLatch(1);
        AtomicInteger closes = new AtomicInteger();
        try (WebDriverManager oldManager = new WebDriverManager(1, 0, Duration.ZERO, () ->
                new WebDriverManager.Browser() {
                    public BufferedImage takeScreenshot(File file, String id) {
                        return image();
                    }

                    public void close() {
                        closes.incrementAndGet();
                        disposalStarted.countDown();
                        awaitIgnoringInterrupts(allowDisposal, disposalInterrupted);
                    }
                });
             WebDriverManager replacement = new WebDriverManager(1, 0, Duration.ZERO, () -> {
                 replacementStarted.countDown();
                 return new WebDriverManager.Browser() {
                     public BufferedImage takeScreenshot(File file, String id) {
                         return image();
                     }

                     public void close() { }
                 };
             })) {
            oldManager.render(UUID.randomUUID(), () -> oldManager.takeScreenshot(new File("old.html"), "card"));
            assertTrue(disposalStarted.await(5, TimeUnit.SECONDS));
            CompletableFuture<Void> shutdown = CompletableFuture.runAsync(oldManager::close);
            assertTrue(disposalInterrupted.await(5, TimeUnit.SECONDS));
            CompletableFuture<BufferedImage> next = replacement.render(UUID.randomUUID(),
                    () -> replacement.takeScreenshot(new File("new.html"), "card"));
            // 旧浏览器刻意阻塞退出，插件关闭仍须有界返回，新实例则继续等待许可。
            shutdown.get(3, TimeUnit.SECONDS);
            assertEquals(1, closes.get());
            assertEquals(1, replacementStarted.getCount());
            assertFalse(next.isDone());
            assertThrows(IllegalStateException.class,
                    () -> replacement.takeScreenshot(new File("direct.html"), "card"));
            allowDisposal.countDown();
            assertNotNull(next.get(5, TimeUnit.SECONDS));
            assertEquals(1, closes.get());
        } finally {
            allowDisposal.countDown();
        }
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch, CountDownLatch interrupted) {
        boolean restoreInterrupt = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try {
            while (latch.getCount() > 0) {
                try {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0 || !latch.await(remaining, TimeUnit.NANOSECONDS)) {
                        throw new IllegalStateException("Timed out waiting for test release");
                    }
                } catch (InterruptedException e) {
                    restoreInterrupt = true;
                    interrupted.countDown();
                }
            }
        } finally {
            if (restoreInterrupt) Thread.currentThread().interrupt();
        }
    }
}
