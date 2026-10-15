package cn.lunadeer.mc.deerlingbot;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.managers.*;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.Notification;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationManager;
import cn.lunadeer.mc.deerlingbot.utils.databse.DatabaseManager;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReloadLifecycleTest {
    @TempDir Path directory;

    @Test
    void repeatedReloadKeepsListenersAndDatabaseAndReleasesResourcesOnDisable() throws Exception {
        Configuration.database = new Configuration.Database();
        DeerlingBot plugin = mock(DeerlingBot.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        doReturn(server).when(plugin).getServer();
        doReturn(true).when(plugin).isEnabled();
        doReturn(directory.toFile()).when(plugin).getDataFolder();
        doReturn(new PluginDescriptionFile("DeerlingBot", "test", DeerlingBot.class.getName())).when(plugin).getDescription();
        doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();
        when(server.getPluginManager()).thenReturn(plugins);

        try (var bukkit = mockStatic(Bukkit.class);
             var settings = mockStatic(ConfigurationManager.class);
             var logs = mockStatic(XLogger.class);
             var notifications = mockStatic(Notification.class);
             var scheduler = mockStatic(Scheduler.class);
             var schedulerInstances = mockConstruction(Scheduler.class);
             var notificationInstances = mockConstruction(Notification.class);
             var logInstances = mockConstruction(XLogger.class);
             var databases = mockConstruction(DatabaseManager.class);
             var queueAccess = mockStatic(EventIoService.class);
             var eventQueues = mockConstruction(EventIoService.class, (queue, context) ->
                     queueAccess.when(EventIoService::getInstance).thenReturn(queue));
             var snapshots = mockConstruction(PlayerSnapshotService.class);
             var avatars = mockConstruction(AvatarManager.class);
             var tables = mockConstruction(WhitelistTable.class);
             var commands = mockConstruction(CommandManager.class);
             var drivers = mockConstruction(WebDriverManager.class);
             var connectors = mockConstruction(CoreConnector.class);
             var reviews = mockConstruction(JoinLeaveEvents.class, (review, context) -> {
                 when(review.isLoaded()).thenReturn(true);
                 doAnswer(invocation -> {
                     ((Runnable) invocation.getArgument(0)).run();
                     return null;
                 }).when(review).start(any(), any());
             })) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(() -> Bukkit.getPluginCommand("deerling")).thenReturn(mock(PluginCommand.class));

            plugin.onEnable();
            assertFalse(plugin.reloadConfiguration());
            Configuration.database.host = "new-database";
            assertTrue(plugin.reloadConfiguration());
            assertEquals("localhost", Configuration.database.host);
            assertEquals(1, databases.constructed().size());
            assertEquals(1, eventQueues.constructed().size());
            assertEquals(1, snapshots.constructed().size());
            verify(eventQueues.constructed().get(0), never()).close();
            verify(snapshots.constructed().get(0), never()).close();
            assertEquals(3, avatars.constructed().size());
            verify(avatars.constructed().get(0)).close();
            verify(avatars.constructed().get(1)).close();
            verify(avatars.constructed().get(2), never()).close();
            assertEquals(1, commands.constructed().size());
            verify(commands.constructed().get(0), times(2)).reloadCommands();
            verify(plugins, times(1)).registerEvents(any(JoinLeaveEvents.class), eq(plugin));
            assertEquals(1, reviews.constructed().size());
            verify(reviews.constructed().get(0), times(2)).reloadConfiguration();
            verify(plugins, times(1)).registerEvents(any(BindManager.class), eq(plugin));
            verify(connectors.constructed().get(0), times(3)).open();
            scheduler.verifyNoInteractions(); // 重载保留审核定时任务。
            verify(databases.constructed().get(0), never()).close();

            // 一项关闭失败不能阻止数据库和其他资源清理。
            doThrow(new IllegalStateException("test close failure")).when(drivers.constructed().get(2)).close();
            plugin.onDisable();
            var shutdownOrder = inOrder(avatars.constructed().get(2), connectors.constructed().get(0), eventQueues.constructed().get(0),
                    snapshots.constructed().get(0), databases.constructed().get(0));
            shutdownOrder.verify(avatars.constructed().get(2)).close();
            shutdownOrder.verify(connectors.constructed().get(0)).close();
            shutdownOrder.verify(eventQueues.constructed().get(0)).close();
            shutdownOrder.verify(snapshots.constructed().get(0)).close();
            shutdownOrder.verify(databases.constructed().get(0)).close();
            verify(databases.constructed().get(0)).close();
            verify(reviews.constructed().get(0)).close();
            scheduler.verify(Scheduler::cancelAll);
            notifications.verify(Notification::close);
            assertThrows(IllegalStateException.class,
                    cn.lunadeer.mc.deerlingbot.utils.command.CommandManager::getInstance);
        } finally {
            cn.lunadeer.mc.deerlingbot.utils.command.CommandManager.close();
        }
    }

    @Test
    void disableBeforeInitializationIsSafe() {
        DeerlingBot plugin = mock(DeerlingBot.class, CALLS_REAL_METHODS);
        doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();
        try (var scheduler = mockStatic(Scheduler.class);
             var notifications = mockStatic(Notification.class)) {
            assertDoesNotThrow(plugin::onDisable);
        }
    }
}
