package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatThreadBoundaryTest {
    private JavaPlugin plugin;
    private PluginManager plugins;
    private EventIoService io;
    private Player player;

    @BeforeEach
    void setup() {
        plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.isPluginEnabled("PlaceholderAPI")).thenReturn(true);
        io = mock(EventIoService.class);
        when(io.isOpen()).thenReturn(true);
        player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("Player");
        Configuration.messageTransfer = new Configuration.MessageTransfer();
        Configuration.messageTransfer.enable = true;
        Configuration.messageTransfer.groupId = "100";
        Configuration.messageTransfer.serverPrefix = "<%player_name%> ";
    }

    @Test
    void playerNameAndPlaceholdersAreOnlyReadByThePlayerTask() {
        List<Supplier<String>> snapshots = new ArrayList<>();
        CompletableFuture<String> result = new CompletableFuture<>();
        try (var scheduler = mockStatic(Scheduler.class); var bukkit = mockStatic(Bukkit.class);
             var placeholders = mockStatic(PlaceHolderApiManager.class); var groups = mockStatic(GroupOperation.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            scheduler.when(() -> Scheduler.callTask(eq(player), any(Supplier.class))).thenAnswer(call -> {
                snapshots.add(call.getArgument(1));
                return result;
            });
            placeholders.when(() -> PlaceHolderApiManager.setPlaceholders(player, "<Player> hello"))
                    .thenReturn("expanded message");
            MessageManager manager = new MessageManager(plugin, io);
            manager.serverMessageToGroup(new AsyncPlayerChatEvent(true, player, "hello", Set.of()));
            verify(player, never()).getName();
            placeholders.verifyNoInteractions();
            groups.verifyNoInteractions();
            assertEquals(1, snapshots.size());
            result.complete(snapshots.get(0).get());
            placeholders.verify(() -> PlaceHolderApiManager.setPlaceholders(player, "<Player> hello"));
            groups.verify(() -> GroupOperation.SendGroupMessage(100L, "expanded message"));
        }
    }

    @Test
    void closedServiceDiscardsALatePlayerSnapshot() {
        CompletableFuture<String> result = new CompletableFuture<>();
        try (var scheduler = mockStatic(Scheduler.class); var groups = mockStatic(GroupOperation.class)) {
            scheduler.when(() -> Scheduler.callTask(eq(player), any(Supplier.class))).thenReturn(result);
            new MessageManager(plugin, io).serverMessageToGroup(new AsyncPlayerChatEvent(true, player, "hello", Set.of()));
            when(io.isOpen()).thenReturn(false);
            result.complete("late snapshot");
            groups.verifyNoInteractions();
        }
    }
}
