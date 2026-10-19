package cn.lunadeer.mc.deerlingbot.managers;

import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;

/** 只读取服务端保存的原版统计，不持有或访问 Bukkit 玩家对象。 */
final class WorldStatisticsReader {
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;

    record Statistics(int playedTicks, int walkedCentimeters, long minedBlocks, long craftedItems) { }

    static Statistics read(Path directory, UUID uuid) throws IOException {
        if (directory == null) return null;
        Path file = directory.resolve(uuid + ".json");
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        } catch (NoSuchFileException e) {
            return null;
        }
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("统计文件超过 4 MiB: " + file);
        try {
            JSONObject root = JSONObject.parseObject(new String(bytes, StandardCharsets.UTF_8));
            JSONObject stats = root == null ? null : root.getJSONObject("stats");
            if (stats == null) throw new IllegalArgumentException("缺少 stats 对象");
            JSONObject custom = stats.getJSONObject("minecraft:custom");
            // 1.17 起由 play_one_minute 更名为 play_time，单位仍是 tick。
            String playKey = custom != null && custom.containsKey("minecraft:play_time")
                    ? "minecraft:play_time" : "minecraft:play_one_minute";
            return new Statistics(value(custom, playKey), value(custom, "minecraft:walk_one_cm"),
                    total(stats.getJSONObject("minecraft:mined")), total(stats.getJSONObject("minecraft:crafted")));
        } catch (RuntimeException e) {
            throw new IOException("无法解析统计文件 " + file, e);
        }
    }

    private static int value(JSONObject object, String key) {
        if (object == null || !object.containsKey(key)) return 0;
        Object value = object.get(key);
        if (!(value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException("统计值必须是整数: " + key);
        }
        long number = ((Number) value).longValue();
        if (number < 0 || number > Integer.MAX_VALUE) throw new IllegalArgumentException("统计值超出范围: " + key);
        return (int) number;
    }

    private static long total(JSONObject category) {
        if (category == null) return 0;
        long result = 0;
        for (String key : category.keySet()) result += value(category, key);
        return result;
    }
}
