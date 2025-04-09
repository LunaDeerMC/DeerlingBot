package cn.lunadeer.lagrangeMC.commands;

import cn.lunadeer.lagrangeMC.LagrangeMC;
import cn.lunadeer.lagrangeMC.configuration.MessageText;
import cn.lunadeer.lagrangeMC.protocols.GroupOperation;
import cn.lunadeer.lagrangeMC.protocols.PrivateOperation;
import cn.lunadeer.lagrangeMC.utils.XLogger;
import cn.lunadeer.lagrangeMC.utils.configuration.ConfigurationPart;
import com.alibaba.fastjson2.JSONObject;
import org.bukkit.entity.Player;

import java.util.Collection;

import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.ReplySegment;
import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.TextSegment;
import static cn.lunadeer.lagrangeMC.utils.Misc.formatString;

public class ListPlayer extends BotCommand {

    public static class ListPlayerText extends ConfigurationPart {
        public String listPlayer = "当前有 {0} 名玩家在线：\n";
    }

    public ListPlayer() {
        super("list", "列出当前在线的玩家", false, false, true);
    }

    @Override
    public void handle(long userId, String commandText, JSONObject jsonObject) {
        Collection<? extends Player> players = LagrangeMC.getInstance().getServer().getOnlinePlayers();
        StringBuilder playerList = new StringBuilder(formatString(MessageText.listPlayerText.listPlayer, players.size()));
        for (Player player : players) {
            playerList.append(player.getName()).append("\n");
        }
        if (!jsonObject.containsKey("message_id")) return;
        long messageID = jsonObject.getLong("message_id");
        if (jsonObject.containsKey("group_id")) {
            long groupID = jsonObject.getLong("group_id");
            GroupOperation.SendGroupMessage(groupID, ReplySegment(messageID), TextSegment(playerList.toString()));
        } else if (jsonObject.containsKey("user_id")) {
            long userID = jsonObject.getLong("user_id");
            PrivateOperation.SendPrivateMessage(userID, ReplySegment(messageID), TextSegment(playerList.toString()));
        } else {
            XLogger.warn("No group_id or user_id found in JSON object");
        }
    }
}
