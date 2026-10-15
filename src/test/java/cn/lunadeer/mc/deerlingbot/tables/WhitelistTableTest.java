package cn.lunadeer.mc.deerlingbot.tables;

import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.databse.DatabaseManager;
import cn.lunadeer.mc.deerlingbot.utils.databse.DatabaseType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhitelistTableTest {
    @TempDir
    Path directory;

    private DatabaseManager previousDatabase;
    private WhitelistTable previousTable;
    private String jdbcUrl;

    @BeforeEach
    void setupDatabase() throws Exception {
        previousDatabase = DatabaseManager.instance;
        var instanceField = WhitelistTable.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        previousTable = (WhitelistTable) instanceField.get(null);
        jdbcUrl = "jdbc:sqlite:" + directory.resolve("bindings.db") + "?busy_timeout=10000";
        DatabaseManager database = mock(DatabaseManager.class);
        when(database.getType()).thenReturn(DatabaseType.SQLITE);
        when(database.getConnection()).thenAnswer(invocation -> DriverManager.getConnection(jdbcUrl));
        DatabaseManager.instance = database;
    }

    @AfterEach
    void restoreStatics() throws Exception {
        DatabaseManager.instance = previousDatabase;
        var instanceField = WhitelistTable.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, previousTable);
    }

    @Test
    void concurrentUsersCanOnlyConsumeOneCodeOnce() throws Exception {
        WhitelistTable table = new WhitelistTable();
        UUID player = UUID.randomUUID();
        table.createRecord(player, "Player", "SHARED");
        List<Callable<Boolean>> requests = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            long user = 1000L + index;
            requests.add(() -> table.setBind("shared", user));
        }

        assertEquals(1, runTogether(requests).stream().filter(Boolean::booleanValue).count());
        assertEquals(1, number("SELECT COUNT(*) FROM user_binding"));
        assertEquals(1, number("SELECT COUNT(*) FROM user_record WHERE bind = true"));
        assertEquals(number("SELECT user_id FROM user_record"), number("SELECT user_id FROM user_binding"));
    }

    @Test
    void concurrentCodesCanOnlyBindOnePlayerToTheSameUser() throws Exception {
        WhitelistTable table = new WhitelistTable();
        List<Callable<Boolean>> requests = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            String code = "CODE0" + index;
            table.createRecord(UUID.randomUUID(), "Player" + index, code);
            requests.add(() -> table.setBind(code, 12345L));
        }

        assertEquals(1, runTogether(requests).stream().filter(Boolean::booleanValue).count());
        assertEquals(1, number("SELECT COUNT(*) FROM user_binding WHERE user_id = 12345"));
        assertEquals(1, number("SELECT COUNT(*) FROM user_record WHERE bind = true AND user_id = 12345"));
        assertEquals(7, number("SELECT COUNT(*) FROM user_record WHERE bind = false AND user_id = 0"));
    }

    @Test
    void invalidOrConsumedCodeDoesNotChangeTheOriginalBinding() throws Exception {
        WhitelistTable table = new WhitelistTable();
        UUID player = UUID.randomUUID();
        table.createRecord(player, "Player", "CODE11");
        assertFalse(table.setBind("MISSING", 11L));
        assertFalse(table.setBind("CODE11", 0L));
        assertTrue(table.setBind("CODE11", 11L));
        assertFalse(table.setBind("CODE11", 22L));
        assertEquals(player, table.getUserUUID(11L));
        assertFalse(table.isBind(22L));
    }

    @Test
    void recordCreationRetriesCodeCollisionsAndReturnsTheActualCode() throws Exception {
        WhitelistTable table = new WhitelistTable(codes("SHARED", "FRESH1"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        table.createRecord(first, "First", "SHARED");

        assertEquals("FRESH1", table.createRecord(second, "Second", "shared"));
        assertEquals("SHARED", table.getCode(first));
        assertEquals("FRESH1", table.getCode(second));
        assertEquals("FRESH1", table.createRecord(second, "Second", "NEW111"));
        assertEquals(2, number("SELECT COUNT(*) FROM user_record"));
        assertThrows(SQLException.class, () -> insertLegacy(UUID.randomUUID(), 0, false, "SHARED"));
    }

    @Test
    void unbindRetriesOldAndConflictingCodesAndReleasesTheUserClaim() throws Exception {
        WhitelistTable table = new WhitelistTable(codes("OLD111", "USED22", "FRESH1"));
        UUID player = UUID.randomUUID();
        table.createRecord(player, "First", "OLD111");
        table.createRecord(UUID.randomUUID(), "Second", "USED22");
        assertTrue(table.setBind("OLD111", 11L));

        table.setUnbind(11L);

        assertFalse(table.isBind(player));
        assertEquals("FRESH1", table.getCode(player));
        assertEquals(0, number("SELECT COUNT(*) FROM user_binding"));
        assertFalse(table.setBind("OLD111", 22L));
        assertTrue(table.setBind("FRESH1", 22L));
        assertTrue(table.setBind("USED22", 11L));
    }

    @Test
    void leavingDeletesBothTheRecordAndTheUniqueUserClaim() throws Exception {
        WhitelistTable table = new WhitelistTable();
        UUID first = UUID.randomUUID();
        table.createRecord(first, "First", "FIRST1");
        table.createRecord(UUID.randomUUID(), "Second", "SECOND");
        assertTrue(table.setBind("FIRST1", 11L));

        table.deletePlayer(11L);

        assertFalse(table.isRecorded(first));
        assertEquals(0, number("SELECT COUNT(*) FROM user_binding"));
        assertTrue(table.setBind("SECOND", 11L));
    }

    @Test
    void migrationPreservesBindingsAndRepairsDuplicateCodesAcrossRestarts() throws Exception {
        createLegacyTable();
        UUID boundPlayer = UUID.randomUUID();
        UUID unboundPlayer = UUID.randomUUID();
        insertLegacy(boundPlayer, 11L, true, "SHARED");
        insertLegacy(unboundPlayer, 0, false, "SHARED");
        try (var logger = mockStatic(XLogger.class)) {
            WhitelistTable table = new WhitelistTable(codes("SHARED", "FRESH1"));
            assertTrue(table.isBind(boundPlayer));
            assertEquals(boundPlayer, table.getUserUUID(11L));
            assertEquals("SHARED", table.getCode(boundPlayer));
            assertEquals("FRESH1", table.getCode(unboundPlayer));
            assertEquals(1, number("SELECT COUNT(*) FROM user_binding WHERE user_id = 11"));
            assertFalse(table.setBind("FRESH1", 11L));
            assertFalse(table.isBind(unboundPlayer));

            WhitelistTable reopened = new WhitelistTable();
            assertEquals(boundPlayer, reopened.getUserUUID(11L));
            assertEquals("FRESH1", reopened.getCode(unboundPlayer));
            assertTrue(reopened.setBind("FRESH1", 22L));
            logger.verify(() -> XLogger.warn(anyString(), eq(1)));
        }
    }

    @Test
    void migrationRejectsConflictingLegacyBindingsWithoutDroppingRecords() throws Exception {
        createLegacyTable();
        insertLegacy(UUID.randomUUID(), 11L, true, "SHARED");
        insertLegacy(UUID.randomUUID(), 11L, true, "SHARED");

        SQLException error = assertThrows(SQLException.class, WhitelistTable::new);

        assertTrue(error.getMessage().contains("历史绑定数据冲突"));
        assertEquals(2, number("SELECT COUNT(*) FROM user_record WHERE user_id = 11 AND bind = true AND code = 'SHARED'"));
        assertEquals(0, number("SELECT COUNT(*) FROM user_binding"));
    }

    @Test
    void failedClaimInsertRollsBackCodeConsumption() throws Exception {
        WhitelistTable table = new WhitelistTable();
        UUID player = UUID.randomUUID();
        table.createRecord(player, "Player", "CODE11");
        execute("CREATE TRIGGER reject_binding BEFORE INSERT ON user_binding BEGIN "
                + "SELECT RAISE(ABORT, '模拟数据库写入失败'); END");

        assertThrows(SQLException.class, () -> table.setBind("CODE11", 11L));

        assertFalse(table.isBind(player));
        assertEquals(0, number("SELECT user_id FROM user_record"));
        assertEquals("CODE11", table.getCode(player));
        assertEquals(0, number("SELECT COUNT(*) FROM user_binding"));
    }

    @Test
    void failedClaimDeleteRollsBackUnbindingAndCodeRotation() throws Exception {
        WhitelistTable table = new WhitelistTable(codes("FRESH1"));
        UUID player = UUID.randomUUID();
        table.createRecord(player, "Player", "CODE11");
        assertTrue(table.setBind("CODE11", 11L));
        execute("CREATE TRIGGER reject_unbind BEFORE DELETE ON user_binding BEGIN "
                + "SELECT RAISE(ABORT, '模拟数据库删除失败'); END");

        assertThrows(SQLException.class, () -> table.setUnbind(11L));

        assertTrue(table.isBind(player));
        assertEquals(player, table.getUserUUID(11L));
        assertEquals("CODE11", table.getCode(player));
        assertEquals(1, number("SELECT COUNT(*) FROM user_binding WHERE user_id = 11"));
    }

    @Test
    void groupEventQueriesUseOneSnapshotAndDeletionReleasesTheBindingClaim() throws Exception {
        WhitelistTable table = new WhitelistTable();
        UUID player = UUID.randomUUID();
        table.createRecord(player, "Player", "CODE11");
        assertFalse(table.getForwardingRecord(11L).bound());
        assertTrue(table.setBind("CODE11", 11L));
        assertEquals(new WhitelistTable.ForwardingRecord(true, "Player"), table.getForwardingRecord(11L));
        table.deletePlayerForGroupLeave(11L);
        assertFalse(table.isRecorded(player));
        assertFalse(table.getForwardingRecord(11L).bound());
        assertEquals(0, number("SELECT COUNT(*) FROM user_binding"));
        table.createRecord(UUID.randomUUID(), "Next", "NEXT11");
        assertTrue(table.setBind("NEXT11", 11L));
    }

    @Test
    void groupLeaveDeleteFailureRollsBackThePlayerAndBindingClaim() throws Exception {
        WhitelistTable table = new WhitelistTable();
        UUID player = UUID.randomUUID();
        table.createRecord(player, "Player", "CODE11");
        assertTrue(table.setBind("CODE11", 11L));
        execute("CREATE TRIGGER reject_group_leave BEFORE DELETE ON user_binding BEGIN "
                + "SELECT RAISE(ABORT, '模拟数据库删除失败'); END");
        assertThrows(SQLException.class, () -> table.deletePlayerForGroupLeave(11L));
        assertTrue(table.isBind(player));
        assertEquals(player, table.getUserUUID(11L));
        assertEquals(1, number("SELECT COUNT(*) FROM user_binding WHERE user_id = 11"));
    }

    private List<Boolean> runTogether(List<Callable<Boolean>> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CyclicBarrier barrier = new CyclicBarrier(requests.size());
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Callable<Boolean> request : requests) {
                results.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return request.call();
                }));
            }
            List<Boolean> values = new ArrayList<>();
            for (Future<Boolean> result : results) values.add(result.get(20, TimeUnit.SECONDS));
            return values;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Supplier<String> codes(String... values) {
        ArrayDeque<String> codes = new ArrayDeque<>(List.of(values));
        return codes::removeFirst;
    }

    private void createLegacyTable() throws SQLException {
        execute("CREATE TABLE user_record (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id BIGINT NOT NULL DEFAULT 0, "
                + "uuid TEXT NOT NULL UNIQUE, last_known_name TEXT NOT NULL DEFAULT 'unknown', "
                + "code TEXT NOT NULL, bind BOOLEAN NOT NULL DEFAULT false, last_join_at TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00')");
    }

    private void insertLegacy(UUID uuid, long user, boolean bound, String code) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO user_record (uuid, user_id, bind, code) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, user);
            statement.setBoolean(3, bound);
            statement.setString(4, code);
            statement.executeUpdate();
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private long number(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
