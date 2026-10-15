package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.Notification;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginAdmissionTest {
    private BindManager manager;
    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        manager = new BindManager(plugin, mock(EventIoService.class));
        Configuration.whiteList = new Configuration.WhiteList();
    }

    @Test
    void unboundPlayerIsRejectedBeforeJoiningWithActualStoredCode() throws Exception {
        WhitelistTable table = mock(WhitelistTable.class);
        when(table.getLoginRecord(uuid)).thenReturn(null, new WhitelistTable.LoginRecord(false, "REAL12"));
        try (var tables = mockStatic(WhitelistTable.class)) {
            tables.when(WhitelistTable::getInstance).thenReturn(table);
            AsyncPlayerPreLoginEvent event = login();
            manager.onPlayerPreLogin(event);
            assertTrue(event.isAsynchronous());
            assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, event.getLoginResult());
            assertTrue(event.getKickMessage().contains("REAL12"));
            verify(table).createRecord(eq(uuid), eq("Player"), anyString());
            verify(table, never()).setName(any(), anyString());
        }
    }

    @Test
    void boundPlayerIsAdmittedAndJoinDoesNotAccessDatabase() throws Exception {
        WhitelistTable table = mock(WhitelistTable.class);
        when(table.getLoginRecord(uuid)).thenReturn(new WhitelistTable.LoginRecord(true, "CODE12"));
        try (var tables = mockStatic(WhitelistTable.class)) {
            tables.when(WhitelistTable::getInstance).thenReturn(table);
            AsyncPlayerPreLoginEvent event = login();
            manager.onPlayerPreLogin(event);
            assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
            verify(table).setName(uuid, "Player");
            clearInvocations(table);
            manager.onPlayerJoin(new PlayerJoinEvent(mock(Player.class), ""));
            verifyNoInteractions(table);
        }
    }

    @Test
    void existingLoginDenialIsPreservedWithoutDatabaseAccess() {
        try (var tables = mockStatic(WhitelistTable.class)) {
            AsyncPlayerPreLoginEvent event = login();
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "其他插件拒绝");
            manager.onPlayerPreLogin(event);
            assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, event.getLoginResult());
            tables.verifyNoInteractions();
        }
    }

    @Test
    void databaseFailureDeniesAdmissionWithoutExposingDatabaseDetails() throws Exception {
        WhitelistTable table = mock(WhitelistTable.class);
        when(table.getLoginRecord(uuid)).thenThrow(new SQLException("secret database details"));
        try (var tables = mockStatic(WhitelistTable.class); var logs = mockStatic(XLogger.class)) {
            tables.when(WhitelistTable::getInstance).thenReturn(table);
            AsyncPlayerPreLoginEvent event = login();
            manager.onPlayerPreLogin(event);
            assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
            assertFalse(event.getKickMessage().contains("secret"));
        }
    }

    @Test
    void optionalBindingQueriesAsynchronouslyAndRepliesOnPlayerScheduler() throws Exception {
        Configuration.whiteList.required = false;
        WhitelistTable table = mock(WhitelistTable.class);
        when(table.getLoginRecord(uuid)).thenReturn(new WhitelistTable.LoginRecord(false, "CODE12"));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        List<Runnable> asyncTasks = new ArrayList<>();
        List<Runnable> playerTasks = new ArrayList<>();
        try (var tables = mockStatic(WhitelistTable.class);
             var scheduler = mockStatic(Scheduler.class);
             var notifications = mockStatic(Notification.class)) {
            tables.when(WhitelistTable::getInstance).thenReturn(table);
            scheduler.when(() -> Scheduler.runTaskAsync(any(Runnable.class))).thenAnswer(invocation -> {
                asyncTasks.add(invocation.getArgument(0));
                return null;
            });
            scheduler.when(() -> Scheduler.runTask(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
                playerTasks.add(invocation.getArgument(1));
                return null;
            });
            manager.onPlayerJoin(new PlayerJoinEvent(player, ""));
            verifyNoInteractions(table);
            notifications.verifyNoInteractions();
            assertEquals(1, asyncTasks.size());
            asyncTasks.get(0).run();
            verify(table).getLoginRecord(uuid);
            notifications.verifyNoInteractions();
            assertEquals(1, playerTasks.size());
            playerTasks.get(0).run();
            notifications.verify(() -> Notification.info(eq(player), contains("CODE12")));
        }
    }

    private AsyncPlayerPreLoginEvent login() {
        var profile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile.getId()).thenReturn(uuid);
        when(profile.getName()).thenReturn("Player");
        return new AsyncPlayerPreLoginEvent("Player", InetAddress.getLoopbackAddress(), uuid, profile);
    }
}
