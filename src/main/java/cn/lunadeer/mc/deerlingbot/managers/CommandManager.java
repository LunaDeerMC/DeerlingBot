package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.commands.BotCommand;
import cn.lunadeer.mc.deerlingbot.commands.FancyCommand;
import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.configuration.MessageText;
import cn.lunadeer.mc.deerlingbot.protocols.PrivateOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.Message;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.PrivateMessage;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ReplySegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationPart;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.listClassOfPackage;

public class CommandManager implements Listener {

    public static class CommandManagerText extends ConfigurationPart {
        public String executeCommandEmpty = "指令错误，{0}<服务端指令>";
        public String executeCommandNoOutput = "指令执行完成，无输出。";
        public String executeCommandFailed = "指令执行失败：{0}";
        public String executeCommandUnknown = "未找到服务端指令：{0}";
    }

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
        String messageText = textSegment.getText();

        if (!Configuration.commandExecutePrefix.isEmpty() && messageText.startsWith(Configuration.commandExecutePrefix)) {
            if (event instanceof GroupMessage) {
                return;
            }
            handlePrivateExecuteCommand((PrivateMessage) event, messageText);
            return;
        }

        if (!messageText.startsWith(Configuration.commandPrefix)) return;

        String commandText = messageText.substring(Configuration.commandPrefix.length()).trim();
        if (commandText.isEmpty()) return;
        String[] commandSplit = commandText.split("\\s+");
        String[] commandArgs = new String[commandSplit.length - 1];
        System.arraycopy(commandSplit, 1, commandArgs, 0, commandArgs.length);
        String commandsStr = commandSplit[0];
        for (BotCommand command : commands) {
            if (commandsStr.equalsIgnoreCase(command.getCommand())) {
                command.runAsync(event, commandArgs);
                return;
            }
        }
    }

    private void handlePrivateExecuteCommand(PrivateMessage event, String messageText) {
        long userId = event.getUserId();
        long messageId = event.getMessageId();
        if (!Configuration.adminAccountList.contains(String.valueOf(userId))) {
            PrivateOperation.SendPrivateMessage(userId,
                    new ReplySegment(messageId),
                    new TextSegment(MessageText.botCommandText.adminOnly));
            return;
        }

        String commandLine = messageText.substring(Configuration.commandExecutePrefix.length()).trim();
        if (commandLine.isEmpty()) {
            PrivateOperation.SendPrivateMessage(userId,
                    new ReplySegment(messageId),
                    new TextSegment(cn.lunadeer.mc.deerlingbot.utils.Misc.formatString(
                            MessageText.commandManagerText.executeCommandEmpty,
                            Configuration.commandExecutePrefix)));
            return;
        }

        if (commandLine.startsWith("/")) {
            commandLine = commandLine.substring(1).trim();
        }
        if (commandLine.isEmpty()) {
            PrivateOperation.SendPrivateMessage(userId,
                    new ReplySegment(messageId),
                    new TextSegment(cn.lunadeer.mc.deerlingbot.utils.Misc.formatString(
                            MessageText.commandManagerText.executeCommandEmpty,
                            Configuration.commandExecutePrefix)));
            return;
        }

        XLogger.info("管理员 {0} 通过私聊执行服务端指令: {1}", userId, commandLine);
        List<String> outputLines = executeServerCommand(commandLine);
        if (outputLines.isEmpty()) {
            outputLines.add(MessageText.commandManagerText.executeCommandNoOutput);
        }
        sendPrivateOutput(userId, messageId, outputLines);
    }

    private List<String> executeServerCommand(String commandLine) {
        List<String> outputLines = new ArrayList<>();
        org.bukkit.command.CommandSender feedbackSender = plugin.getServer().createCommandSender(
                component -> collectOutput(outputLines, component)
        );

        try {
            boolean success = plugin.getServer().dispatchCommand(feedbackSender, commandLine);
            if (!success && outputLines.isEmpty()) {
                outputLines.add(cn.lunadeer.mc.deerlingbot.utils.Misc.formatString(
                        MessageText.commandManagerText.executeCommandUnknown,
                        commandLine));
            }
        } catch (Exception e) {
            XLogger.error(e);
            outputLines.add(cn.lunadeer.mc.deerlingbot.utils.Misc.formatString(
                    MessageText.commandManagerText.executeCommandFailed,
                    e.getMessage()));
        }
        return outputLines;
    }

    private void collectOutput(List<String> outputLines, Component component) {
        appendOutput(outputLines, PlainTextComponentSerializer.plainText().serialize(component));
    }

    private void appendOutput(List<String> outputLines, String line) {
        if (line == null) return;
        String normalized = ChatColor.stripColor(line).replace("\r", "");
        String[] splitLines = normalized.split("\n");
        for (String splitLine : splitLines) {
            if (!splitLine.isBlank()) {
                outputLines.add(splitLine);
            }
        }
    }

    private void sendPrivateOutput(long userId, long messageId, List<String> outputLines) {
        StringBuilder chunk = new StringBuilder();
        boolean firstMessage = true;
        for (String line : outputLines) {
            int extraLength = line.length() + (chunk.isEmpty() ? 0 : 1);
            if (chunk.length() + extraLength > 1500) {
                sendPrivateChunk(userId, messageId, chunk.toString(), firstMessage);
                chunk.setLength(0);
                firstMessage = false;
            }
            if (!chunk.isEmpty()) {
                chunk.append("\n");
            }
            chunk.append(line);
        }
        if (!chunk.isEmpty()) {
            sendPrivateChunk(userId, messageId, chunk.toString(), firstMessage);
        }
    }

    private void sendPrivateChunk(long userId, long messageId, String text, boolean withReply) {
        if (withReply) {
            PrivateOperation.SendPrivateMessage(userId,
                    new ReplySegment(messageId),
                    new TextSegment(text));
        } else {
            PrivateOperation.SendPrivateMessage(userId, text);
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
