package cn.lunadeer.lagrangeMC;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import cn.lunadeer.lagrangeMC.managers.PlaceHolderApiManager;
import cn.lunadeer.lagrangeMC.protocols.GroupOperation;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.TextSegment;

public class JoinLeaveEvents implements Listener {

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!Configuration.joinQuitMessage.enable) return;
        String message = Configuration.joinQuitMessage.joinMessage;
        message = message.replace("%player_name%", event.getPlayer().getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            message = PlaceHolderApiManager.setPlaceholders(event.getPlayer(), message);

        GroupOperation.SendGroupMessage(Long.parseLong(Configuration.joinQuitMessage.groupId), TextSegment(message));
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (!Configuration.joinQuitMessage.enable) return;
        String message = Configuration.joinQuitMessage.quitMessage;
        message = message.replace("%player_name%", event.getPlayer().getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            message = PlaceHolderApiManager.setPlaceholders(event.getPlayer(), message);

        GroupOperation.SendGroupMessage(Long.parseLong(Configuration.joinQuitMessage.groupId), TextSegment(message));
    }
}
