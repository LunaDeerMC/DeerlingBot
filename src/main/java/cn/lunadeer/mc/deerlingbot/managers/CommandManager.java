package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.commands.BotCommand;
import cn.lunadeer.mc.deerlingbot.commands.FancyCommand;
import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.Message;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.PrivateMessage;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import com.alibaba.fastjson2.JSONObject;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.listClassOfPackage;

public class CommandManager implements Listener {

    private static CommandManager instance;
    private final JavaPlugin plugin;
    private final List<BotCommand> commands = new ArrayList<>();

    public CommandManager(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;
        loadCommands();
        instance = this;
    }

    public static CommandManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CommandManager is not initialized");
        }
        return instance;
    }

    @EventHandler
    public void handleGroupCommand(GroupMessage event) {
        handle(event);
    }

    @EventHandler
    public void handlePrivateCommand(PrivateMessage event) {
        handle(event);
    }

    private void handle(Message event) {
        if (event.getMessage().isEmpty()) return;
        MessageSegment firstSegment = event.getMessage().get(0);
        if (!(firstSegment instanceof TextSegment textSegment)) return;
        String commandText = textSegment.getText();
        commandText = commandText.substring(Configuration.commandPrefix.length());
        String[] commandSplit = commandText.split(" ");
        String[] commandArgs = new String[commandSplit.length - 1];
        System.arraycopy(commandSplit, 1, commandArgs, 0, commandArgs.length);
        String commandsStr = commandSplit[0];
        for (BotCommand command : commands) {
            if (commandsStr.equalsIgnoreCase(command.getCommand())) {
                command.run(event, commandArgs);
                return;
            }
        }
    }

    private void loadCommands() {
        List<String> classesInPackage = listClassOfPackage(plugin, "cn.lunadeer.mc.deerlingbot.commands");

        for (String className : classesInPackage) {
            XLogger.debug("Loading class: {0}", className);
            try {
                Class<?> clazz = Class.forName(className);
                if (BotCommand.class.isAssignableFrom(clazz) && !Modifier.isInterface(clazz.getModifiers()) && !Modifier.isAbstract(clazz.getModifiers())) {
                    if (clazz.isAnnotationPresent(FancyCommand.class) && !Configuration.fancyCommand) {
                        XLogger.debug("Fancy command {0} is disabled", className);
                        continue;
                    }
                    BotCommand command = (BotCommand) clazz.getDeclaredConstructor().newInstance();
                    commands.add(command);
                    XLogger.debug("Registered command: {0}", command.getCommand());
                }
            } catch (Exception e) {
                XLogger.debug("Failed to register command: {0}", e.getMessage());
            }
        }
    }

}
