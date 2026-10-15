package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupDecrease;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupIsolationTest {
    private JavaPlugin plugin;
    private EventIoService io;
    private final List<Runnable> tasks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        io = mock(EventIoService.class);
        when(io.isOpen()).thenReturn(true);
        when(io.submitMessage(anyLong(), any())).thenAnswer(call -> tasks.add(call.getArgument(1)));
        when(io.submitDeletion(anyLong(), any())).thenAnswer(call -> tasks.add(call.getArgument(1)));
        Configuration.groupList = List.of("100", "101");
        Configuration.messageTransfer = new Configuration.MessageTransfer();
        Configuration.messageTransfer.enable = true;
        Configuration.messageTransfer.groupId = "100";
        Configuration.syncCardName = false;
    }

    @Test
    void leavingUnrelatedGroupDoesNotRemoveBindingAndManagedDeletionIsDeferred() throws Exception {
        WhitelistTable table = mock(WhitelistTable.class);
        try (var tables = mockStatic(WhitelistTable.class)) {
            tables.when(WhitelistTable::getInstance).thenReturn(table);
            BindManager manager = new BindManager(plugin, io);
            manager.onQuitGroup(new GroupDecrease(0, 1, 200, 42, GroupDecrease.SubType.leave, 42));
            assertTrue(tasks.isEmpty());
            manager.onQuitGroup(new GroupDecrease(0, 1, 101, 42, GroupDecrease.SubType.leave, 42));
            verifyNoInteractions(table);
            assertEquals(1, tasks.size());
            // 即使全局实例更换，已入队写入仍使用原表。
            tables.when(WhitelistTable::getInstance).thenReturn(mock(WhitelistTable.class));
            tasks.get(0).run();
            verify(table).deletePlayerForGroupLeave(42);
        }
    }

    @Test
    void unrelatedGroupsCannotEnqueueQueriesOrBroadcast() {
        try (var groups = mockStatic(GroupOperation.class); var tables = mockStatic(WhitelistTable.class);
             var scheduler = mockStatic(Scheduler.class)) {
            MessageManager manager = new MessageManager(plugin, io);
            manager.handleGroupMessageToServer(message(200));
            Configuration.messageTransfer.bindRequired = false;
            manager.handleGroupMessageToServer(message(101));
            assertTrue(tasks.isEmpty());
            groups.verifyNoInteractions();
            tables.verifyNoInteractions();
            scheduler.verifyNoInteractions();
        }
    }

    @Test
    void configuredGroupQueriesInBackgroundAndUsesSafeBroadcastScheduler() throws Exception {
        WhitelistTable table = mock(WhitelistTable.class);
        when(table.getForwardingRecord(42)).thenReturn(new WhitelistTable.ForwardingRecord(false, null));
        try (var groups = mockStatic(GroupOperation.class); var tables = mockStatic(WhitelistTable.class);
             var scheduler = mockStatic(Scheduler.class)) {
            tables.when(WhitelistTable::getInstance).thenReturn(table);
            scheduler.when(() -> Scheduler.broadcastMessage(anyString(), any(BooleanSupplier.class))).thenReturn(CompletableFuture.completedFuture(null));
            MessageManager manager = new MessageManager(plugin, io);
            manager.handleGroupMessageToServer(message(100));
            verifyNoInteractions(table);
            groups.verifyNoInteractions();
            tasks.remove(0).run();
            groups.verify(() -> GroupOperation.SendGroupMessage(eq(100L), any(MessageSegment[].class)));
            scheduler.verifyNoInteractions();
            when(table.getForwardingRecord(42)).thenReturn(new WhitelistTable.ForwardingRecord(true, "Player"));
            manager.handleGroupMessageToServer(message(100));
            tasks.remove(0).run();
            scheduler.verify(() -> Scheduler.broadcastMessage(eq("[群消息]<玩家> hello"), any(BooleanSupplier.class)));
        }
    }

    @Test
    void lateDatabaseResultDoesNotBroadcastAfterShutdown() throws Exception {
        WhitelistTable table = mock(WhitelistTable.class);
        when(table.getForwardingRecord(42)).thenAnswer(call -> {
            when(io.isOpen()).thenReturn(false);
            return new WhitelistTable.ForwardingRecord(true, "Player");
        });
        try (var groups = mockStatic(GroupOperation.class); var tables = mockStatic(WhitelistTable.class);
             var scheduler = mockStatic(Scheduler.class)) {
            tables.when(WhitelistTable::getInstance).thenReturn(table);
            new MessageManager(plugin, io).handleGroupMessageToServer(message(100));
            tasks.get(0).run();
            groups.verifyNoInteractions();
            scheduler.verifyNoInteractions();
        }
    }

    private GroupMessage message(long groupId) {
        return new GroupMessage(0, 1, GroupMessage.SubType.normal, 7, groupId, 42,
                List.of(new TextSegment("#hello")), "#hello",
                new GroupMessage.Sender(42, "玩家", "unknown", 0, "", "", "", "member", ""));
    }
}
