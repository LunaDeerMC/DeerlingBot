package cn.lunadeer.mc.deerlingbot.commands;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.configuration.MessageText;
import cn.lunadeer.mc.deerlingbot.managers.BindManager;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.PrivateOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.Message;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ReplySegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationPart;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.formatString;

public class UnbindPlayer extends BotCommand {

    public static class UnbindPlayerText extends ConfigurationPart {
        public String unbindSuccess = "解绑成功！";
        public String unbindFailed = "解绑失败，请稍后重试。";
        public String notBind = "你当前还没有绑定游戏账号。";
        public String commandError = "指令错误，{0}unbind";
    }

    public UnbindPlayer() {
        super("unbind", "解除白名单绑定", false, false, false);
    }

    @Override
    public void handle(Message messageEvent, String... args) {
        Long groupID = messageEvent instanceof GroupMessage groupMessage ? groupMessage.getGroupID() : null;
        long userID = messageEvent.getUserId();
        long messageID = messageEvent.getMessageId();

        if (args.length != 0) {
            reply(groupID, userID, messageID,
                    formatString(MessageText.unbindPlayerText.commandError, Configuration.commandPrefix));
            return;
        }

        try {
            if (!WhitelistTable.getInstance().isBind(userID)) {
                reply(groupID, userID, messageID, MessageText.unbindPlayerText.notBind);
                return;
            }
        } catch (Exception e) {
            XLogger.error(e);
            reply(groupID, userID, messageID, MessageText.unbindPlayerText.unbindFailed);
            return;
        }

        if (BindManager.getInstance().unbind(userID)) {
            reply(groupID, userID, messageID, MessageText.unbindPlayerText.unbindSuccess);
        } else {
            reply(groupID, userID, messageID, MessageText.unbindPlayerText.unbindFailed);
        }
    }

    private void reply(Long groupID, long userID, long messageID, String text) {
        if (groupID != null) {
            GroupOperation.SendGroupMessage(groupID,
                    new ReplySegment(messageID),
                    new TextSegment(text));
        } else {
            PrivateOperation.SendPrivateMessage(userID,
                    new ReplySegment(messageID),
                    new TextSegment(text));
        }
    }
}