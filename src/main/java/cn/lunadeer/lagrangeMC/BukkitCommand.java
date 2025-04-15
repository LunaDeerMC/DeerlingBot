package cn.lunadeer.lagrangeMC;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import cn.lunadeer.lagrangeMC.managers.ResourceDownloader;
import cn.lunadeer.lagrangeMC.utils.Notification;
import cn.lunadeer.lagrangeMC.utils.command.CommandManager;
import cn.lunadeer.lagrangeMC.utils.command.SecondaryCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class BukkitCommand {

    public BukkitCommand(JavaPlugin plugin) {
        CommandManager commandManager = new CommandManager(plugin, "lagrange");
    }

    public SecondaryCommand reload = new SecondaryCommand("reload", List.of(
    )) {
        @Override
        public void executeHandler(CommandSender sender) {
            LagrangeMC.getInstance().onDisable();
            LagrangeMC.getInstance().onEnable();
        }
    }.needPermission("lagrangemc.command.lagrange").register();

    public SecondaryCommand version = new SecondaryCommand("version", List.of(
    )) {
        @Override
        public void executeHandler(CommandSender sender) {
            Notification.info(sender, "=========================");
            Notification.info(sender, "LagrangeMC Version: {0}", LagrangeMC.getInstance().getDescription().getVersion());
            if (Configuration.fancyCommand) {
                Notification.info(sender, "----");
                {
                    TextComponent.Builder builder = Component.text();
                    builder.append(Component.text("下载地址: " + ResourceDownloader.getInstance().templatesLink()));
                    builder.clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, ResourceDownloader.getInstance().templatesLink()));
                    try {
                        int[] currentTemplatesVer = ResourceDownloader.getInstance().getTemplatesVer();
                        if (!ResourceDownloader.needUpdate(currentTemplatesVer, ResourceDownloader.getInstance().getLatestTemplatesVer())) {
                            Notification.info(sender, "templates: {0} [最新]", ResourceDownloader.getInstance().templatesTag(currentTemplatesVer));
                        } else {
                            Notification.warn(sender, "templates: {0} [有更新]", ResourceDownloader.getInstance().templatesTag(currentTemplatesVer));
                        }
                    } catch (Exception e) {
                        Notification.info(sender, "templates: {0}", e.getMessage());
                        Notification.warn(sender, builder.build());
                    }
                }
                Notification.info(sender, "----");
                {
                    TextComponent.Builder builder = Component.text();
                    builder.append(Component.text("下载地址: " + ResourceDownloader.getInstance().libsLink()));
                    builder.clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, ResourceDownloader.getInstance().libsLink()));
                    try {
                        int[] currentLibsVer = ResourceDownloader.getInstance().getLibsVer();
                        if (!ResourceDownloader.needUpdate(currentLibsVer, ResourceDownloader.getInstance().getLatestLibsVer())) {
                            Notification.info(sender, "libs: {0} [最新]", ResourceDownloader.getInstance().libsTag(currentLibsVer));
                        } else {
                            Notification.warn(sender, "libs: {0} [有更新]", ResourceDownloader.getInstance().libsTag(currentLibsVer));
                            Notification.warn(sender, builder.build());
                        }
                    } catch (Exception e) {
                        Notification.info(sender, "libs: {0}", e.getMessage());
                        Notification.warn(sender, builder.build());
                    }
                }
            }
            Notification.info(sender, "=========================");
        }
    }.needPermission("lagrangemc.command.lagrange").register();

}
