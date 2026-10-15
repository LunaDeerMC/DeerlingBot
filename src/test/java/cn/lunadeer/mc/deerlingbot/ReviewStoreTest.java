package cn.lunadeer.mc.deerlingbot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReviewStoreTest {
    @TempDir Path directory;

    @Test
    void writesAreOrderedAndShutdownFlushesLatestSnapshot() throws Exception {
        Path file = directory.resolve("reviews.json");
        ReviewStore.Entry first = new ReviewStore.Entry(1, 2, "first", "10 + 2", 12, false, 1000);
        ReviewStore.Entry latest = new ReviewStore.Entry(1, 3, "latest", "20 - 2", 18, true, 2000);
        try (ReviewStore store = new ReviewStore(file)) {
            store.save(List.of(first));
            store.save(List.of(latest));
        }
        try (ReviewStore reopened = new ReviewStore(file)) {
            assertEquals(List.of(latest), reopened.load().get(3, TimeUnit.SECONDS));
            reopened.save(List.of()).get(3, TimeUnit.SECONDS);
        }
        try (ReviewStore reopened = new ReviewStore(file)) {
            assertTrue(reopened.load().get(3, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void replacedStoreCannotOverwriteNewOwnersState() throws Exception {
        Path file = directory.resolve("reviews.json");
        try (ReviewStore old = new ReviewStore(file); ReviewStore current = new ReviewStore(file)) {
            current.save(List.of()).get(3, TimeUnit.SECONDS);
            assertThrows(Exception.class, () -> old.save(List.of(new ReviewStore.Entry(
                    1, 2, "old", "1 + 1", 2, false, 1000))).get(3, TimeUnit.SECONDS));
            assertTrue(current.load().get(3, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void damagedStateFailsLoadingWithoutOverwritingFile() throws Exception {
        Path file = directory.resolve("reviews.json");
        Files.writeString(file, "broken state");
        try (ReviewStore store = new ReviewStore(file)) {
            assertThrows(Exception.class, () -> store.load().get(3, TimeUnit.SECONDS));
            assertEquals("broken state", Files.readString(file));
        }
    }

    @Test
    void finalFlushReplacesQueuedSnapshotsWithLatestState() throws Exception {
        Path file = directory.resolve("reviews.json");
        ReviewStore.Entry entry = new ReviewStore.Entry(1, 2, "pending", "1 + 1", 2, false, 1000);
        ReviewStore store = new ReviewStore(file);
        for (int index = 0; index < 150; index++) store.save(List.of(entry));
        store.flushAndClose(List.of());
        try (ReviewStore reopened = new ReviewStore(file)) {
            assertTrue(reopened.load().get(3, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void shutdownBeforeLoadedStateIsAppliedDoesNotErasePendingReviews() throws Exception {
        Path file = directory.resolve("reviews.json");
        ReviewStore.Entry entry = new ReviewStore.Entry(1, 2, "pending", "1 + 1", 2, false, 1000);
        try (ReviewStore initial = new ReviewStore(file)) {
            initial.save(List.of(entry)).get(3, TimeUnit.SECONDS);
        }
        List<Runnable> callbacks = new ArrayList<>();
        CountDownLatch queued = new CountDownLatch(1);
        JoinLeaveEvents listener = new JoinLeaveEvents(new ReviewStore(file), System::currentTimeMillis, task -> {
            callbacks.add(task); queued.countDown();
        });
        listener.start(() -> fail("关闭后不能打开连接"), error -> fail(error));
        assertTrue(queued.await(3, TimeUnit.SECONDS));
        listener.close();
        callbacks.get(0).run();
        try (ReviewStore reopened = new ReviewStore(file)) {
            assertEquals(List.of(entry), reopened.load().get(3, TimeUnit.SECONDS));
        }
    }
}
