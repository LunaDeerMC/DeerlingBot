package cn.lunadeer.lagrangeMC.commands;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import cn.lunadeer.lagrangeMC.configuration.MessageText;
import cn.lunadeer.lagrangeMC.protocols.GroupOperation;
import cn.lunadeer.lagrangeMC.protocols.PrivateOperation;
import cn.lunadeer.lagrangeMC.tables.WhitelistTable;
import cn.lunadeer.lagrangeMC.utils.XLogger;
import cn.lunadeer.lagrangeMC.utils.configuration.ConfigurationPart;
import com.alibaba.fastjson2.JSONObject;

import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.ReplySegment;
import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.TextSegment;

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

    /**
     * Executes the command logic after performing any necessary pre-processing.
     *
     * @param commandText The full text of the command as entered by the user.
     * @param jsonObject  A raw one bot v11 protocol JSON object received from the bot.
     */
    public void run(String commandText, JSONObject jsonObject) {
        // some pre-processing
        JSONObject sender = jsonObject.getJSONObject("sender");
        if (sender == null) return;
        if (!sender.containsKey("user_id")) return;
        long userID = sender.getLong("user_id");
        if (!jsonObject.containsKey("message_id")) return;
        long messageID = jsonObject.getLong("message_id");

        // group only
        Long groupID = null;
        if (jsonObject.containsKey("group_id")) {
            groupID = jsonObject.getLong("group_id");
            if (!Configuration.groupList.contains(String.valueOf(groupID))) return;
        } else if (groupOnly) {
            PrivateOperation.SendPrivateMessage(userID, ReplySegment(messageID), TextSegment(MessageText.botCommandText.groupOnly));
            return;
        }

        // admin only
        if (adminOnly && !Configuration.adminAccountList.contains(String.valueOf(userID))) {
            if (groupID != null) {
                GroupOperation.SendGroupMessage(groupID, ReplySegment(messageID), TextSegment(MessageText.botCommandText.adminOnly));
            } else {
                PrivateOperation.SendPrivateMessage(userID, ReplySegment(messageID), TextSegment(MessageText.botCommandText.adminOnly));
            }
            return;
        }

        // bind required
        try {
            if (bindRequired && !WhitelistTable.getInstance().isBind(userID)) {
                if (groupID != null) {
                    GroupOperation.SendGroupMessage(groupID, ReplySegment(messageID), TextSegment(MessageText.botCommandText.bindRequired));
                } else {
                    PrivateOperation.SendPrivateMessage(userID, ReplySegment(messageID), TextSegment(MessageText.botCommandText.bindRequired));
                }
                return;
            }
            if (bindRequired && Configuration.syncCardName && groupID != null) {
                String cardName = sender.getString("card");
                String playerName = WhitelistTable.getInstance().getLastKnownName(userID);
                if (cardName == null || !cardName.equals(playerName)) {
                    GroupOperation.SetGroupCard(groupID, userID, playerName);
                }
            }
        } catch (Exception e) {
            // Handle exception
            XLogger.error(e);
            return;
        }

        handle(userID, commandText, jsonObject);
    }

    /**
     * Executes the command logic.
     *
     * @param commandText The full text of the command as entered by the user.
     * @param jsonObject  A raw one bot v11 protocol JSON object received from the bot.
     */
    public abstract void handle(long userId, String commandText, JSONObject jsonObject);

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