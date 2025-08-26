package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.Notification;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
            if (WhitelistTable.getInstance().isCodeAvailable(code.toUpperCase())) {
                WhitelistTable.getInstance().setBind(code.toUpperCase(), userId);
                return true;
            } else {
                XLogger.debug("Code {0} is not available", code);
                return false;
            }
        } catch (Exception e) {
            XLogger.error(e);
            return false;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        OfflinePlayer player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String code;
        try {
            if (WhitelistTable.getInstance().isRecorded(uuid)) {
                WhitelistTable.getInstance().setName(uuid, player.getName());    // update name
                if (WhitelistTable.getInstance().isBind(uuid)) return;
                code = WhitelistTable.getInstance().getCode(uuid);
            } else {
                code = generateCode();
                WhitelistTable.getInstance().createRecord(uuid, player.getName(), code);
            }
        } catch (Exception e) {
            XLogger.error(e);
            event.getPlayer().kickPlayer(e.getMessage());
            return;
        }
        if (Configuration.whiteList.required) {
            event.getPlayer().kickPlayer(Configuration.whiteList.getKickMessage(code));
        } else {
            Notification.info(event.getPlayer(), Configuration.whiteList.getBindMessage(code));
        }
    }
}
