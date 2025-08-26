package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.configuration.MessageText;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationPart;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import static cn.lunadeer.mc.deerlingbot.protocols.MessageSegment.ReplySegment;
import static cn.lunadeer.mc.deerlingbot.protocols.MessageSegment.TextSegment;

public class MessageManager implements Listener {

    public static class MessageManagerText extends ConfigurationPart {
        public String bindRequired = "游戏账号绑定QQ后才能使用消息转发";
    }

    private static MessageManager instance;
    private final JavaPlugin plugin;

    public MessageManager(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;
        instance = this;
    }

    public static MessageManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MessageManager is not initialized");
        }
        return instance;
    }

    @EventHandler
    public void serverMessageToGroup(AsyncPlayerChatEvent event) {
        if (!Configuration.messageTransfer.enable) return;
        if (event.isCancelled()) return;
        String message = event.getMessage();
        if (!message.startsWith(Configuration.messageTransfer.serverFlag)) return;
        message = message.substring(Configuration.messageTransfer.serverFlag.length());
        message = Configuration.messageTransfer.serverPrefix + message;
        message = message.replace("%player_name%", event.getPlayer().getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            message = PlaceHolderApiManager.setPlaceholders(event.getPlayer(), message);
        GroupOperation.SendGroupMessage(Long.parseLong(Configuration.messageTransfer.groupId), message);
    }

    public void handleGroupMessageToServer(long groupID, long userID, long messageID, String nickName, String text) {
        XLogger.debug("收到群消息: userID={0}, nickName={1}, text={2}", userID, nickName, text);
        if (!Configuration.messageTransfer.enable) return;
        if (!text.startsWith(Configuration.messageTransfer.groupFlag)) return;
        try {
            if (Configuration.messageTransfer.bindRequired && !WhitelistTable.getInstance().isBind(userID)) {
                if (!Configuration.messageTransfer.groupFlag.isEmpty()) {
                    GroupOperation.SendGroupMessage(Long.parseLong(Configuration.messageTransfer.groupId), ReplySegment(messageID), TextSegment(MessageText.messageManagerText.bindRequired));
                }
                return;
            }
            // force set card name
            if (Configuration.messageTransfer.bindRequired && Configuration.syncCardName) {
                nickName = WhitelistTable.getInstance().getLastKnownName(userID);
                GroupOperation.SetGroupCard(groupID, userID, nickName);
            }
        } catch (Exception e) {
            XLogger.error(e);
            return;
        }
        text = text.substring(Configuration.messageTransfer.groupFlag.length());
        text = Configuration.messageTransfer.groupPrefix + text;
        text = text.replace("%nickname%", nickName);
        text = ChatColor.translateAlternateColorCodes('&', text);
        plugin.getServer().broadcastMessage(text);
    }

}
