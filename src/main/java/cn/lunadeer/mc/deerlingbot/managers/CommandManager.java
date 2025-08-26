package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.commands.BotCommand;
import cn.lunadeer.mc.deerlingbot.commands.FancyCommand;
import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import com.alibaba.fastjson2.JSONObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.listClassOfPackage;

public class CommandManager {

    private static CommandManager instance;
    private final JavaPlugin plugin;
    private final List<BotCommand> commands = new ArrayList<>();

    public CommandManager(JavaPlugin plugin) {
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

    public void handleBotCommand(String commandText, JSONObject rawJsonObj) {
        commandText = commandText.substring(Configuration.commandPrefix.length());
        String commandsStr = commandText.split(" ")[0];
        for (BotCommand command : commands) {
            if (commandsStr.equalsIgnoreCase(command.getCommand())) {
                command.run(commandText, rawJsonObj);
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
