package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupDecrease;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupIsolationTest {
    private JavaPlugin plugin;
    private Server server;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        Configuration.groupList = List.of("100", "101");
        Configuration.messageTransfer = new Configuration.MessageTransfer();
        Configuration.messageTransfer.enable = true;
        Configuration.messageTransfer.groupId = "100";
        Configuration.syncCardName = false;
    }

    @Test
    void leavingUnrelatedGroupDoesNotRemoveBinding() throws Exception {
        WhitelistTable table = mock(WhitelistTable.class);
        try (MockedStatic<WhitelistTable> tables = mockStatic(WhitelistTable.class)) {
            tables.when(WhitelistTable::getInstance).thenReturn(table);
            BindManager manager = new BindManager(plugin);
            manager.onQuitGroup(new GroupDecrease(0, 1, 200, 42, GroupDecrease.SubType.leave, 42));
            verifyNoInteractions(table);
            manager.onQuitGroup(new GroupDecrease(0, 1, 101, 42, GroupDecrease.SubType.leave, 42));
            verify(table).deletePlayer(42);
        }
    }

    @Test
    void unrelatedGroupsCannotBroadcastOrReceiveBindingReplies() {
        try (MockedStatic<GroupOperation> groups = mockStatic(GroupOperation.class);
             MockedStatic<WhitelistTable> tables = mockStatic(WhitelistTable.class);
             MockedStatic<XLogger> logs = mockStatic(XLogger.class)) {
            MessageManager manager = new MessageManager(plugin);
            manager.handleGroupMessageToServer(message(200));
            Configuration.messageTransfer.bindRequired = false;
            manager.handleGroupMessageToServer(message(101));
            verify(server, never()).broadcastMessage(anyString());
            groups.verifyNoInteractions();
            tables.verifyNoInteractions();
        }
    }

    @Test
    void configuredGroupRetainsBindingCheckAndForwarding() throws Exception {
        WhitelistTable table = mock(WhitelistTable.class);
        try (MockedStatic<GroupOperation> groups = mockStatic(GroupOperation.class);
             MockedStatic<WhitelistTable> tables = mockStatic(WhitelistTable.class);
             MockedStatic<XLogger> logs = mockStatic(XLogger.class)) {
            tables.when(WhitelistTable::getInstance).thenReturn(table);
            MessageManager manager = new MessageManager(plugin);
            manager.handleGroupMessageToServer(message(100));
            verify(server, never()).broadcastMessage(anyString());
            groups.verify(() -> GroupOperation.SendGroupMessage(eq(100L), any(MessageSegment[].class)));
            when(table.isBind(42L)).thenReturn(true);
            manager.handleGroupMessageToServer(message(100));
            verify(server).broadcastMessage("[群消息]<玩家> hello");
        }
    }

    private GroupMessage message(long groupId) {
        return new GroupMessage(0, 1, GroupMessage.SubType.normal, 7, groupId, 42,
                List.of(new TextSegment("#hello")), "#hello",
                new GroupMessage.Sender(42, "玩家", "unknown", 0, "", "", "", "member", ""));
    }
}
