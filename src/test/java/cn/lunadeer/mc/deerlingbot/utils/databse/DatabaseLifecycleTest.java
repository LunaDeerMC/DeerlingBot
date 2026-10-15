package cn.lunadeer.mc.deerlingbot.utils.databse;

import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseLifecycleTest {
    @TempDir Path directory;

    @Test
    void closeActuallyClosesPoolAndCannotReopenIt() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        DatabaseManager manager = new DatabaseManager(plugin, "sqlite", "", "", "test", "", "");
        try (var connection = manager.getConnection()) {
            assertFalse(connection.isClosed());
        }
        var poolField = DatabaseManager.class.getDeclaredField("ds");
        poolField.setAccessible(true);
        HikariDataSource pool = (HikariDataSource) poolField.get(manager);
        manager.close();
        assertTrue(pool.isClosed());
        assertNull(poolField.get(manager));
        assertThrows(SQLException.class, manager::getConnection);
        assertThrows(IllegalStateException.class, manager::reconnect);
        assertDoesNotThrow(manager::close);
    }

    @Test
    void closeBeforeFirstConnectionDoesNotCreatePool() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        DatabaseManager manager = new DatabaseManager(plugin, "sqlite", "", "", "test", "", "");
        manager.close();
        assertThrows(SQLException.class, manager::getConnection);
    }
}
