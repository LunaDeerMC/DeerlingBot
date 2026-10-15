package cn.lunadeer.mc.deerlingbot;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.*;

/** 串行异步持久化；只有关服时允许有界等待已提交的写入。 */
final class ReviewStore implements AutoCloseable {
    record Entry(long groupId, long userId, String session, String expression, int answer,
                 boolean privateMessage, long deadline) { }

    private static final ConcurrentMap<Path, ReviewStore> OWNERS = new ConcurrentHashMap<>();
    private final Path file;
    private volatile boolean abandoned;
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128), task -> {
        Thread thread = new Thread(task, "DeerlingBot-review-store");
        thread.setDaemon(true);
        return thread;
    });

    ReviewStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
        synchronized (OWNERS) { OWNERS.put(this.file, this); }
    }

    CompletableFuture<List<Entry>> load() {
        return submit(() -> {
            try {
                if (!Files.exists(file)) return List.of();
                JSONObject root = JSONObject.parseObject(Files.readString(file));
                if (root == null || root.getIntValue("version") != 1 || root.getJSONArray("reviews") == null)
                    throw new IOException("无效的审核状态文件: " + file);
                List<Entry> entries = new ArrayList<>();
                for (Object value : root.getJSONArray("reviews")) {
                    JSONObject row = (JSONObject) value;
                    Entry entry = new Entry(row.getLongValue("groupId"), row.getLongValue("userId"),
                            row.getString("session"), row.getString("expression"), row.getIntValue("answer"),
                            row.getBooleanValue("privateMessage"), row.getLongValue("deadline"));
                    if (entry.groupId() <= 0 || entry.userId() <= 0 || entry.session() == null
                            || entry.expression() == null || entry.deadline() <= 0)
                        throw new IOException("审核状态记录缺少必要字段: " + file);
                    entries.add(entry);
                }
                return entries;
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        });
    }

    CompletableFuture<Void> save(List<Entry> entries) {
        List<Entry> snapshot = List.copyOf(entries);
        return submit(() -> {
            Path temporary = null;
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                JSONArray rows = new JSONArray();
                for (Entry entry : snapshot) {
                    JSONObject row = new JSONObject();
                    row.put("groupId", entry.groupId()); row.put("userId", entry.userId());
                    row.put("session", entry.session()); row.put("expression", entry.expression());
                    row.put("answer", entry.answer()); row.put("privateMessage", entry.privateMessage());
                    row.put("deadline", entry.deadline()); rows.add(row);
                }
                JSONObject root = new JSONObject(); root.put("version", 1); root.put("reviews", rows);
                temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "reviews-", ".tmp");
                Files.writeString(temporary, root.toJSONString());
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
                synchronized (OWNERS) {
                    if (abandoned || OWNERS.get(file) != this)
                        throw new IOException("旧审核存储实例已关闭，拒绝覆盖新实例状态");
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception error) {
                throw new CompletionException(error);
            } finally {
                if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
            return null;
        });
    }

    private <T> CompletableFuture<T> submit(Supplier<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(result);
        try {
            writer.execute(() -> {
                try { result.complete(operation.get()); }
                catch (Throwable error) { result.completeExceptionally(error); }
                finally { pending.remove(result); }
            });
        } catch (RejectedExecutionException error) {
            pending.remove(result);
            result.completeExceptionally(error);
        }
        return result;
    }

    /** 关服时合并未开始的旧快照，给最终状态预留位置，仍由同一文件线程写入。 */
    void flushAndClose(List<Entry> snapshot) {
        writer.getQueue().clear();
        pending.forEach(result -> result.completeExceptionally(new CancellationException("合并为关服最终审核快照")));
        pending.clear();
        CompletableFuture<Void> finalWrite = save(snapshot);
        close();
        // close 已有界等待；这里不会再次阻塞，但必须暴露最终写入失败。
        finalWrite.join();
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) {
                abandoned = true;
                writer.shutdownNow();
                pending.forEach(result -> result.completeExceptionally(new IOException("关服等待审核写入超时")));
                throw new IllegalStateException("审核状态写入在 3 秒内未完成，已取消剩余写入；最后状态可能未保存，请检查磁盘。");
            }
        } catch (InterruptedException error) {
            abandoned = true;
            writer.shutdownNow();
            pending.forEach(result -> result.completeExceptionally(error));
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待审核状态写入时被中断", error);
        }
    }
}
