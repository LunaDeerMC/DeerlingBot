package cn.lunadeer.mc.deerlingbot;

import cn.lunadeer.mc.deerlingbot.configuration.AchievementThresholdConfiguration;
import cn.lunadeer.mc.deerlingbot.utils.Notification;
import cn.lunadeer.mc.deerlingbot.utils.command.CommandManager;
import cn.lunadeer.mc.deerlingbot.utils.command.SecondaryCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import static cn.lunadeer.mc.deerlingbot.DeerlingBot.adminPermission;

public class BukkitCommand {

    public BukkitCommand(JavaPlugin plugin) {
        new CommandManager(plugin, "deerling");
    }

    public SecondaryCommand reload = new SecondaryCommand("reload", List.of()) {
        @Override
        public void executeHandler(CommandSender sender) {
            try {
                boolean databaseRestartRequired = DeerlingBot.getInstance().reloadConfiguration();
                Notification.info(sender, "配置已重新加载。");
                if (databaseRestartRequired) {
                    Notification.warn(sender, "数据库连接配置已保存，重启服务器后生效；当前继续使用原数据库。");
                }
            } catch (AchievementThresholdConfiguration.InvalidThresholds e) {
                sender.sendMessage("成就门槛配置无效，未应用本次重载，继续使用原配置：" + e.getMessage());
            } catch (Exception e) {
                sender.sendMessage("配置重载失败，插件已停用，请检查控制台日志。");
            }
        }
    }.needPermission(adminPermission).register();

    public SecondaryCommand version = new SecondaryCommand("version", List.of()) {
        @Override
        public void executeHandler(CommandSender sender) {
            Notification.info(sender, "=========================");
            Notification.info(sender, "DeerlingBot Version: {0}", DeerlingBot.getInstance().getDescription().getVersion());
            Notification.info(sender, "图片渲染引擎: Java2D (内置)");
            Notification.info(sender, "=========================");
        }
    }.needPermission(adminPermission).register();
}
