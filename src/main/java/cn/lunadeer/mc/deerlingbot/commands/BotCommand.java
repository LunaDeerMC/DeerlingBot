package cn.lunadeer.mc.deerlingbot.commands;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.configuration.MessageText;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.PrivateOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.Message;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ReplySegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationPart;


/**
 * Abstract class representing a bot command.
 * This class provides a structure for defining commands with a name, description,
 * and an optional restriction for admin-only usage.
 */
public abstract class BotCommand {

    public static class BotCommandText extends ConfigurationPart {
        public String adminOnly = "此指令仅机器人管理员可用";
        public String groupOnly = "此指令仅可在群中使用";
        public String bindRequired = "游戏账号绑定QQ后才能使用此指令";
    }

    // The command name without any prefix
    private final String command;

    // A brief description of the command
    private final String description;

    // Indicates whether the command is restricted to admin users only
    private final boolean adminOnly;

    // Indicates whether the command can only be used in groups
    private final boolean groupOnly;

    // Indicates whether the command requires a bind
    private final boolean bindRequired;

    /**
     * Constructs a new BotCommand.
     *
     * @param command      The name of the command (without prefix).
     * @param description  A brief description of the command.
     * @param adminOnly    Whether the command is restricted to admin users.
     * @param groupOnly    Whether the command can only be used in groups.
     * @param bindRequired Whether the command requires a bind.
     */
    public BotCommand(String command, String description, boolean adminOnly, boolean groupOnly, boolean bindRequired) {
        this.command = command;
        this.description = description;
        this.adminOnly = adminOnly;
        this.groupOnly = groupOnly;
        this.bindRequired = bindRequired;
    }

    public void run(Message messageEvent, String... args) {
        // some pre-processing
        long userID = messageEvent.getUserId();
        long messageID = messageEvent.getMessageId();

        // group only
        Long groupID = null;
        if (messageEvent instanceof GroupMessage groupMessageEvent) {
            groupID = groupMessageEvent.getGroupID();
            if (!Configuration.groupList.contains(String.valueOf(groupID))) return;
        } else if (groupOnly) {
            PrivateOperation.SendPrivateMessage(userID,
                    new ReplySegment(messageID),
                    new TextSegment(MessageText.botCommandText.groupOnly));
            return;
        }

        // admin only
        if (adminOnly && !Configuration.adminAccountList.contains(String.valueOf(userID))) {
            if (groupID != null) {
                GroupOperation.SendGroupMessage(groupID,
                        new ReplySegment(messageID),
                        new TextSegment(MessageText.botCommandText.adminOnly));
            } else {
                PrivateOperation.SendPrivateMessage(userID,
                        new ReplySegment(messageID),
                        new TextSegment(MessageText.botCommandText.adminOnly));
            }
            return;
        }

        // bind required
        try {
            if (bindRequired && !WhitelistTable.getInstance().isBind(userID)) {
                if (groupID != null) {
                    GroupOperation.SendGroupMessage(groupID,
                            new ReplySegment(messageID),
                            new TextSegment(MessageText.botCommandText.bindRequired));
                } else {
                    PrivateOperation.SendPrivateMessage(userID,
                            new ReplySegment(messageID),
                            new TextSegment(MessageText.botCommandText.bindRequired));
                }
                return;
            }
            if (bindRequired && Configuration.syncCardName && messageEvent instanceof GroupMessage groupMessageEvent) {
                String cardName = groupMessageEvent.getSender().getCard();
                String playerName = WhitelistTable.getInstance().getLastKnownName(userID);
                if (!cardName.equals(playerName)) {
                    GroupOperation.SetGroupCard(groupID, userID, playerName);
                }
            }
        } catch (Exception e) {
            // Handle exception
            XLogger.error(e);
            return;
        }

        handle(messageEvent, args);
    }

    public abstract void handle(Message messageEvent, String... args);

    /**
     * Gets the name of the command.
     *
     * @return The command name (without prefix).
     */
    public String getCommand() {
        return command;
    }

    /**
     * Gets the description of the command.
     *
     * @return A brief description of the command.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if the command is restricted to admin users.
     *
     * @return True if the command is admin-only, false otherwise.
     */
    public boolean isAdminOnly() {
        return adminOnly;
    }
}