package cn.lunadeer.mc.deerlingbot;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.managers.PlaceHolderApiManager;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;


public class JoinLeaveEvents implements Listener {

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!Configuration.joinQuitMessage.enable) return;
        String message = Configuration.joinQuitMessage.joinMessage;
        message = message.replace("%player_name%", event.getPlayer().getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            message = PlaceHolderApiManager.setPlaceholders(event.getPlayer(), message);

        GroupOperation.SendGroupMessage(Long.parseLong(Configuration.joinQuitMessage.groupId), new TextSegment(message));
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (!Configuration.joinQuitMessage.enable) return;
        String message = Configuration.joinQuitMessage.quitMessage;
        message = message.replace("%player_name%", event.getPlayer().getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            message = PlaceHolderApiManager.setPlaceholders(event.getPlayer(), message);

        GroupOperation.SendGroupMessage(Long.parseLong(Configuration.joinQuitMessage.groupId), new TextSegment(message));
    }
}
