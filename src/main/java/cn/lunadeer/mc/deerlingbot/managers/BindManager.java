package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupDecrease;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.Notification;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.generateCode;

public class BindManager implements Listener {

    private final JavaPlugin plugin;
    private static BindManager instance;

    public BindManager(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static BindManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BindManager is not initialized");
        }
        return instance;
    }

    public boolean bind(long userId, String code) {
        try {
            return WhitelistTable.getInstance().setBind(code, userId);
        } catch (Exception e) {
            XLogger.error(e);
            return false;
        }
    }

    public boolean unbind(long userId) {
        try {
            if (!WhitelistTable.getInstance().isBind(userId)) {
                return false;
            }
            WhitelistTable.getInstance().setUnbind(userId);
            return true;
        } catch (Exception e) {
            XLogger.error(e);
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        UUID uuid = event.getUniqueId();
        try {
            WhitelistTable table = WhitelistTable.getInstance();
            WhitelistTable.LoginRecord record = table.getLoginRecord(uuid);
            if (record == null) {
                table.createRecord(uuid, event.getName(), generateCode());
                // 使用实际入库的验证码，兼容碰撞重试和同 UUID 的并发登录。
                record = table.getLoginRecord(uuid);
            }
            if (record == null) throw new IllegalStateException("登录记录已失效，请重新连接");
            if (Configuration.whiteList.required && !record.bound()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                        Configuration.whiteList.getKickMessage(record.code()));
                return;
            }
            table.setName(uuid, event.getName());
        } catch (Exception e) {
            XLogger.error(e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "无法验证白名单，请稍后重试或联系管理员。");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (Configuration.whiteList.required) return;
        var player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        // 可选绑定提示也异步查库，玩家消息交回其所属线程发送。
        Scheduler.runTaskAsync(() -> {
            try {
                WhitelistTable.LoginRecord record = WhitelistTable.getInstance().getLoginRecord(uuid);
                if (record == null || record.bound()) return;
                Scheduler.runTask(player, () -> {
                    if (player.isOnline()) Notification.info(player, Configuration.whiteList.getBindMessage(record.code()));
                });
            } catch (Exception e) {
                XLogger.error(e);
            }
        });
    }

    @EventHandler
    public void onQuitGroup(GroupDecrease event) {
        if (!Configuration.groupList.contains(String.valueOf(event.getGroupId()))) return;
        try {
            WhitelistTable.getInstance().deletePlayer(event.getUserId());
        } catch (Exception e) {
            XLogger.error(e);
        }
    }
}
