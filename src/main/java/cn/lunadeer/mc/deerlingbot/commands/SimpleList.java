package cn.lunadeer.mc.deerlingbot.commands;

import cn.lunadeer.mc.deerlingbot.DeerlingBot;
import cn.lunadeer.mc.deerlingbot.configuration.MessageText;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.PrivateOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.Message;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ReplySegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationPart;
import org.bukkit.entity.Player;

import java.util.Collection;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.formatString;

public class SimpleList extends BotCommand {

    public static class ListPlayerText extends ConfigurationPart {
        public String listPlayer = "当前有 {0} 名玩家在线：\n";
    }

    public SimpleList() {
        super("list", "列出当前在线的玩家", false, false, true);
    }

    @Override
    public void handle(Message messageEvent, String... args) {
        Collection<? extends Player> players = DeerlingBot.getInstance().getServer().getOnlinePlayers();
        StringBuilder playerList = new StringBuilder(formatString(MessageText.listPlayerText.listPlayer, players.size()));
        for (Player player : players) {
            playerList.append(player.getName()).append("\n");
        }
        long messageID = messageEvent.getMessageId();
        if (messageEvent instanceof GroupMessage groupMessage) {
            long groupID = groupMessage.getGroupID();
            GroupOperation.SendGroupMessage(groupID,
                    new ReplySegment(messageID),
                    new TextSegment(playerList.toString()));
        } else {
            long userID = messageEvent.getUserId();
            PrivateOperation.SendPrivateMessage(userID,
                    new ReplySegment(messageID),
                    new TextSegment(playerList.toString()));
        }
    }
}
