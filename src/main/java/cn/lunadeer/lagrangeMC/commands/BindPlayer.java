package cn.lunadeer.lagrangeMC.commands;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import cn.lunadeer.lagrangeMC.configuration.MessageText;
import cn.lunadeer.lagrangeMC.managers.BindManager;
import cn.lunadeer.lagrangeMC.protocols.GroupOperation;
import cn.lunadeer.lagrangeMC.protocols.PrivateOperation;
import cn.lunadeer.lagrangeMC.tables.WhitelistTable;
import cn.lunadeer.lagrangeMC.utils.XLogger;
import cn.lunadeer.lagrangeMC.utils.configuration.ConfigurationPart;
import com.alibaba.fastjson2.JSONObject;

import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.ReplySegment;
import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.TextSegment;
import static cn.lunadeer.lagrangeMC.utils.Misc.formatString;

public class BindPlayer extends BotCommand {

    public static class BindPlayerText extends ConfigurationPart {
        public String bindSuccess = "绑定成功！";
        public String bindFailed = "绑定失败，请检查验证码是否正确。";
        public String alreadyBind = "你已经绑定了白名单，不能重复绑定！";
        public String commandError = "指令错误，{0}bind <code>（中间有个空格哦）";
    }

    public BindPlayer() {
        super("bind", "绑定白名单", false, false, false);
    }

    @Override
    public void handle(long userID, String commandText, JSONObject jsonObject) {
        Long groupID = jsonObject.getLong("group_id");

        if (!jsonObject.containsKey("message_id")) return;
        long messageID = jsonObject.getLong("message_id");

        String[] commandTextSplit = commandText.split(" ");
        if (commandTextSplit.length != 2) {
            if (groupID != null)
                GroupOperation.SendGroupMessage(groupID, ReplySegment(messageID), TextSegment(formatString(MessageText.bindPlayerText.commandError, Configuration.commandPrefix)));
            else
                PrivateOperation.SendPrivateMessage(userID, ReplySegment(messageID), TextSegment(formatString(MessageText.bindPlayerText.commandError, Configuration.commandPrefix)));
            return;
        }
        String code = commandTextSplit[1];

        try {
            if (WhitelistTable.getInstance().isBind(userID)) {
                XLogger.debug("User {0} is already verified", userID);
                if (groupID != null)
                    GroupOperation.SendGroupMessage(groupID, ReplySegment(messageID), TextSegment(MessageText.bindPlayerText.alreadyBind));
                else
                    PrivateOperation.SendPrivateMessage(userID, ReplySegment(messageID), TextSegment(MessageText.bindPlayerText.alreadyBind));
                return;
            }
        } catch (Exception e) {
            XLogger.error(e);
            return;
        }

        if (BindManager.getInstance().bind(userID, code)) {
            if (groupID != null)
                GroupOperation.SendGroupMessage(groupID, ReplySegment(messageID), TextSegment(MessageText.bindPlayerText.bindSuccess));
            else
                PrivateOperation.SendPrivateMessage(userID, ReplySegment(messageID), TextSegment(MessageText.bindPlayerText.bindSuccess));
        } else {
            if (groupID != null)
                GroupOperation.SendGroupMessage(groupID, ReplySegment(messageID), TextSegment(MessageText.bindPlayerText.bindFailed));
            else
                PrivateOperation.SendPrivateMessage(userID, ReplySegment(messageID), TextSegment(MessageText.bindPlayerText.bindFailed));
        }
    }
}
