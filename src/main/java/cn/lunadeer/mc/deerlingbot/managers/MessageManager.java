package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.configuration.MessageText;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ReplySegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationPart;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

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

    @EventHandler
    public void handleGroupMessageToServer(GroupMessage event) {
        XLogger.debug("收到群消息: userID={0}, nickName={1}, text={2}", event.getUserId(), event.getSender().getNickname(), event.getMessage());
        if (!Configuration.messageTransfer.enable) return;
        if (event.getMessage().isEmpty()) return;
        MessageSegment firstSegment = event.getMessage().get(0);
        if (!(firstSegment instanceof TextSegment textSegment)) return;
        if (!textSegment.getText().startsWith(Configuration.messageTransfer.groupFlag)) return;
        String nickName = event.getSender().getNickname();
        try {
            if (Configuration.messageTransfer.bindRequired && !WhitelistTable.getInstance().isBind(event.getUserId())) {
                if (!Configuration.messageTransfer.groupFlag.isEmpty()) {
                    GroupOperation.SendGroupMessage(Long.parseLong(Configuration.messageTransfer.groupId),
                            new ReplySegment(event.getMessageId()),
                            new TextSegment(MessageText.messageManagerText.bindRequired));
                }
                return;
            }
            // force set card name
            if (Configuration.messageTransfer.bindRequired && Configuration.syncCardName) {
                nickName = WhitelistTable.getInstance().getLastKnownName(event.getUserId());
                GroupOperation.SetGroupCard(event.getGroupID(), event.getUserId(), nickName);
            }
        } catch (Exception e) {
            XLogger.error(e);
            return;
        }
        String text = textSegment.getText().substring(Configuration.messageTransfer.groupFlag.length());
        text = Configuration.messageTransfer.groupPrefix + text;
        text = text.replace("%nickname%", nickName);
        text = ChatColor.translateAlternateColorCodes('&', text);
        plugin.getServer().broadcastMessage(text);
    }

}
