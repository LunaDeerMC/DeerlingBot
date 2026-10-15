package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.configuration.MessageText;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ReplySegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationPart;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class MessageManager implements Listener {
    public static class MessageManagerText extends ConfigurationPart {
        public String bindRequired = "游戏账号绑定QQ后才能使用消息转发";
    }

    private static MessageManager instance;
    private final JavaPlugin plugin;
    private final EventIoService eventIo;

    public MessageManager(JavaPlugin plugin) {
        this(plugin, EventIoService.getInstance());
    }

    MessageManager(JavaPlugin plugin, EventIoService eventIo) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;
        this.eventIo = eventIo;
        instance = this;
    }

    public static MessageManager getInstance() {
        if (instance == null) throw new IllegalStateException("MessageManager is not initialized");
        return instance;
    }

    @EventHandler
    public void serverMessageToGroup(AsyncPlayerChatEvent event) {
        var config = Configuration.messageTransfer;
        if (!eventIo.isOpen() || !config.enable || event.isCancelled()) return;
        String message = event.getMessage();
        if (!message.startsWith(config.serverFlag)) return;
        String template = config.serverPrefix + message.substring(config.serverFlag.length());
        long groupId = Long.parseLong(config.groupId);
        var player = event.getPlayer();
        // 聊天事件可能来自异步线程，玩家状态与 PAPI 扩展都在玩家所属线程访问。
        Scheduler.callTask(player, () -> {
            if (!eventIo.isOpen() || !player.isOnline()) return null;
            String text = template.replace("%player_name%", player.getName());
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                text = PlaceHolderApiManager.setPlaceholders(player, text);
            }
            return text;
        }).whenComplete((text, failure) -> {
            if (!eventIo.isOpen()) return;
            if (failure != null) {
                if (!(failure instanceof Scheduler.PlayerUnavailableException)) {
                    plugin.getLogger().log(Level.WARNING, "采集聊天玩家信息失败", failure);
                }
                return;
            }
            if (text != null) GroupOperation.SendGroupMessage(groupId, text);
        });
    }

    @EventHandler
    public void handleGroupMessageToServer(GroupMessage event) {
        var config = Configuration.messageTransfer;
        if (!eventIo.isOpen() || !config.enable) return;
        if (!String.valueOf(event.getGroupID()).equals(config.groupId)) return;
        if (event.getMessage().isEmpty()) return;
        MessageSegment firstSegment = event.getMessage().get(0);
        if (!(firstSegment instanceof TextSegment textSegment)) return;
        if (!textSegment.getText().startsWith(config.groupFlag)) return;
        // 配置与消息在接收时取快照，热重载不会改变队列中消息的路由或权限语义。
        ForwardMessage message = new ForwardMessage(event.getUserId(), event.getGroupID(), event.getMessageId(),
                event.getSender().getNickname(), textSegment.getText().substring(config.groupFlag.length()),
                config.groupPrefix, config.bindRequired, Configuration.syncCardName, !config.groupFlag.isEmpty(),
                MessageText.messageManagerText.bindRequired);
        WhitelistTable table = message.bindRequired() ? WhitelistTable.getInstance() : null;
        eventIo.submitMessage(message.userId(), () -> forward(message, table));
    }

    private void forward(ForwardMessage message, WhitelistTable table) {
        String name = message.nickname();
        try {
            if (message.bindRequired()) {
                WhitelistTable.ForwardingRecord record = table.getForwardingRecord(message.userId());
                if (!eventIo.isOpen()) return;
                if (!record.bound()) {
                    if (message.replyWhenUnbound()) {
                        GroupOperation.SendGroupMessage(message.groupId(), new ReplySegment(message.messageId()),
                                new TextSegment(message.bindRequiredText()));
                    }
                    return;
                }
                if (message.syncCard()) {
                    name = record.name();
                    GroupOperation.SetGroupCard(message.groupId(), message.userId(), name);
                }
            }
            if (!eventIo.isOpen()) return;
            String text = ChatColor.translateAlternateColorCodes('&',
                    (message.prefix() + message.text()).replace("%nickname%", name));
            Scheduler.broadcastMessage(text, eventIo::isOpen).whenComplete((ignored, failure) -> {
                if (failure != null && eventIo.isOpen()) {
                    plugin.getLogger().log(Level.WARNING, "群消息广播失败", failure);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "群消息转发查询失败", e);
        }
    }

    private record ForwardMessage(long userId, long groupId, long messageId, String nickname, String text,
                                  String prefix, boolean bindRequired, boolean syncCard, boolean replyWhenUnbound,
                                  String bindRequiredText) { }
}
