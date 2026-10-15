package cn.lunadeer.mc.deerlingbot.tables;

import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.databse.DatabaseManager;
import cn.lunadeer.mc.deerlingbot.utils.databse.DatabaseType;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/** 绑定关系与验证码的数据库约束，所有状态变更在同一个连接中提交。 */
final class BindingStore {
    private static final int MAX_CODE_ATTEMPTS = 128;
    private static final String CODE_INDEX = "user_record_code_unique";

    private final DatabaseManager database;
    private final Supplier<String> codeGenerator;

    BindingStore(DatabaseManager database, Supplier<String> codeGenerator) {
        this.database = database;
        this.codeGenerator = codeGenerator;
    }

    void initialize() throws SQLException {
        // 未绑定玩家仍使用 user_id=0，因此用独立表约束已绑定 QQ 和 UUID 的唯一性。
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS user_binding ("
                    + "user_id BIGINT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE)"
                    + (database.getType() == DatabaseType.MYSQL ? " ENGINE=InnoDB" : ""));
        }
        int repaired = transaction(connection -> {
            execute(connection, "DELETE FROM user_binding");
            List<LegacyRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, user_id, bind, code FROM user_record ORDER BY id");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    records.add(new LegacyRecord(rows.getString("uuid"), rows.getLong("user_id"),
                            rows.getBoolean("bind"), rows.getString("code")));
                }
            }
            Set<Long> users = new HashSet<>();
            for (LegacyRecord record : records) {
                if (!record.bound()) continue;
                if (record.userId() <= 0 || !users.add(record.userId())) {
                    throw new SQLException("历史绑定数据冲突：QQ " + record.userId()
                            + " 的绑定无效或对应多个游戏账号。请备份并人工核对 user_record；原绑定已保留。");
                }
                execute(connection, "INSERT INTO user_binding (user_id, uuid) VALUES (?, ?)",
                        record.userId(), record.uuid());
            }

            // 旧版本允许重复码。仅轮换冲突/非大写验证码，保留所有玩家和 QQ 绑定。
            Set<String> reserved = new HashSet<>();
            for (LegacyRecord record : records) reserved.add(normalize(record.code()));
            Set<String> seen = new HashSet<>();
            int changed = 0;
            for (LegacyRecord record : records) {
                String normalized = normalize(record.code());
                if (record.code().equals(normalized) && seen.add(normalized)) continue;
                String code = nextUnusedCode(reserved);
                execute(connection, "UPDATE user_record SET code = ? WHERE uuid = ?", code, record.uuid());
                changed++;
            }
            return changed;
        });
        // MySQL 的 DDL 会隐式提交，因此索引在数据迁移事务提交后创建。
        try (Connection connection = database.getConnection()) {
            boolean exists = false;
            try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                    connection.getCatalog(), null, "user_record", true, false)) {
                while (indexes.next()) {
                    if (CODE_INDEX.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) exists = true;
                }
            }
            if (!exists) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE UNIQUE INDEX " + CODE_INDEX + " ON user_record (code)");
                }
            }
        }
        if (repaired > 0) {
            XLogger.warn("已轮换 {0} 个历史重复或非大写绑定码；原有 QQ 绑定保持不变，未绑定玩家请重新进服获取验证码。", repaired);
        }
    }

    String createRecord(UUID uuid, String name, String requestedCode) throws SQLException {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        String code = normalize(requestedCode);
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            try (Connection connection = database.getConnection()) {
                execute(connection, "INSERT INTO user_record (uuid, last_known_name, code, last_join_at) VALUES (?, ?, ?, ?)",
                        uuid.toString(), name, code, Timestamp.valueOf(LocalDateTime.now()));
                return code;
            } catch (SQLException e) {
                if (!isUniqueViolation(e)) throw e;
                // 同一玩家并发登录时，返回先成功插入的验证码。
                try (Connection connection = database.getConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT code FROM user_record WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet rows = statement.executeQuery()) {
                        if (rows.next()) return rows.getString(1);
                    }
                }
                code = normalize(codeGenerator.get());
            }
        }
        throw new SQLException("无法生成唯一绑定码，请稍后重试。");
    }

    boolean bind(String suppliedCode, long userId) throws SQLException {
        if (userId <= 0) return false;
        String code = normalize(suppliedCode);
        try {
            return transaction(connection -> {
                // 首条语句直接条件写入，避免先查后改，也避免 SQLite 的读事务升级竞争。
                int updated = execute(connection, "UPDATE user_record SET bind = ?, user_id = ? WHERE code = ? AND bind = ?",
                        true, userId, code, false);
                if (updated == 0) return false;
                if (updated != 1) throw new SQLException("绑定码不唯一，已回滚绑定操作。");
                String uuid;
                try (PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM user_record WHERE code = ?")) {
                    statement.setString(1, code);
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) throw new SQLException("绑定记录不存在，已回滚绑定操作。");
                        uuid = rows.getString(1);
                    }
                }
                execute(connection, "INSERT INTO user_binding (user_id, uuid) VALUES (?, ?)", userId, uuid);
                return true;
            });
        } catch (SQLException e) {
            // QQ 或 UUID 已被其他事务占用，回滚前面的验证码消费。
            if (isUniqueViolation(e)) return false;
            throw e;
        }
    }

    void unbind(long userId) throws SQLException {
        if (userId <= 0) return;
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = normalize(codeGenerator.get());
            try {
                boolean finished = transaction(connection -> {
                    int updated = execute(connection,
                            "UPDATE user_record SET bind = ?, user_id = ?, code = ? WHERE user_id = ? AND bind = ? AND code <> ?",
                            false, 0L, code, userId, true, code);
                    if (updated > 0) {
                        execute(connection, "DELETE FROM user_binding WHERE user_id = ?", userId);
                        return true;
                    }
                    // 随机数恰好等于旧码时必须重试，不能让已公开的旧码再次生效。
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT uuid FROM user_record WHERE user_id = ? AND bind = ?")) {
                        statement.setLong(1, userId);
                        statement.setBoolean(2, true);
                        try (ResultSet rows = statement.executeQuery()) {
                            return !rows.next();
                        }
                    }
                });
                if (finished) return;
            } catch (SQLException e) {
                if (!isUniqueViolation(e)) throw e;
            }
        }
        throw new SQLException("无法生成唯一绑定码，原绑定已保留，请稍后重试。");
    }

    void deletePlayer(long userId) throws SQLException {
        if (userId <= 0) return;
        transaction(connection -> {
            execute(connection, "DELETE FROM user_record WHERE user_id = ?", userId);
            execute(connection, "DELETE FROM user_binding WHERE user_id = ?", userId);
            return null;
        });
    }

    private String nextUnusedCode(Set<String> reserved) throws SQLException {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = normalize(codeGenerator.get());
            if (reserved.add(code)) return code;
        }
        throw new SQLException("无法修复历史重复绑定码，原数据已保留。");
    }

    private static String normalize(String code) {
        return Objects.requireNonNull(code, "code").toUpperCase(Locale.ROOT);
    }

    private static int execute(Connection connection, String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) statement.setObject(index + 1, arguments[index]);
            return statement.executeUpdate();
        }
    }

    private static boolean isUniqueViolation(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if ("23505".equals(current.getSQLState()) || current.getErrorCode() == 1062
                    || current.getErrorCode() == 2067 || current.getErrorCode() == 1555
                    || (current.getErrorCode() == 19 && current.getMessage() != null
                    && current.getMessage().contains("UNIQUE constraint failed"))) return true;
        }
        return false;
    }

    private <T> T transaction(Transaction<T> operation) throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = operation.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    @FunctionalInterface
    private interface Transaction<T> {
        T run(Connection connection) throws SQLException;
    }

    private record LegacyRecord(String uuid, long userId, boolean bound, String code) { }
}
