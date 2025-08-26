package cn.lunadeer.mc.deerlingbot;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.managers.ResourceDownloader;
import cn.lunadeer.mc.deerlingbot.utils.Notification;
import cn.lunadeer.mc.deerlingbot.utils.command.CommandManager;
import cn.lunadeer.mc.deerlingbot.utils.command.SecondaryCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import static cn.lunadeer.mc.deerlingbot.DeerlingBot.adminPermission;

public class BukkitCommand {

    public BukkitCommand(JavaPlugin plugin) {
        CommandManager commandManager = new CommandManager(plugin, "deerling");
    }

    public SecondaryCommand reload = new SecondaryCommand("reload", List.of(
    )) {
        @Override
        public void executeHandler(CommandSender sender) {
            DeerlingBot.getInstance().onDisable();
            DeerlingBot.getInstance().onEnable();
        }
    }.needPermission(adminPermission).register();

    public SecondaryCommand version = new SecondaryCommand("version", List.of(
    )) {
        @Override
        public void executeHandler(CommandSender sender) {
            Notification.info(sender, "=========================");
            Notification.info(sender, "DeerlingBot Version: {0}", DeerlingBot.getInstance().getDescription().getVersion());
            if (Configuration.fancyCommand) {
                Notification.info(sender, "----");
                {
                    String link = "下载地址: " + ResourceDownloader.getInstance().templatesLink();
                    try {
                        int[] currentTemplatesVer = ResourceDownloader.getInstance().getTemplatesVer();
                        if (!ResourceDownloader.needUpdate(currentTemplatesVer, ResourceDownloader.getInstance().getLatestTemplatesVer())) {
                            Notification.info(sender, "templates: {0} [最新]", ResourceDownloader.getInstance().templatesTag(currentTemplatesVer));
                        } else {
                            Notification.warn(sender, "templates: {0} [有更新]", ResourceDownloader.getInstance().templatesTag(currentTemplatesVer));
                            Notification.warn(sender, link);
                        }
                    } catch (Exception e) {
                        Notification.info(sender, "templates: {0}", e.getMessage());
                        Notification.warn(sender, link);
                    }
                }
                Notification.info(sender, "----");
                {
                    String link = "下载地址: " + ResourceDownloader.getInstance().libsLink();
                    try {
                        int[] currentLibsVer = ResourceDownloader.getInstance().getLibsVer();
                        if (!ResourceDownloader.needUpdate(currentLibsVer, ResourceDownloader.getInstance().getLatestLibsVer())) {
                            Notification.info(sender, "libs: {0} [最新]", ResourceDownloader.getInstance().libsTag(currentLibsVer));
                        } else {
                            Notification.warn(sender, "libs: {0} [有更新]", ResourceDownloader.getInstance().libsTag(currentLibsVer));
                            Notification.warn(sender, link);
                        }
                    } catch (Exception e) {
                        Notification.info(sender, "libs: {0}", e.getMessage());
                        Notification.warn(sender, link);
                    }
                }
            }
            Notification.info(sender, "=========================");
        }
    }.needPermission(adminPermission).register();

}
