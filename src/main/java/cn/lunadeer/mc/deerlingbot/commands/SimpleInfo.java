package cn.lunadeer.mc.deerlingbot.commands;

import cn.lunadeer.mc.deerlingbot.DeerlingBot;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.PrivateOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.Message;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ImageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ReplySegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class SimpleInfo extends BotCommand {
    public SimpleInfo() {
        super("info", "查看个人信息", false, false, true);
    }

    @Override
    public void handle(Message messageEvent, String... args) {
        UUID uuid;
        String lastKnownName;
        String status;
        long userId = messageEvent.getUserId();
        try {
            uuid = WhitelistTable.getInstance().getUserUUID(userId);
            lastKnownName = WhitelistTable.getInstance().getLastKnownName(uuid);
            Player player = DeerlingBot.getInstance().getServer().getOnlinePlayers().stream()
                    .filter(p -> p.getUniqueId().equals(uuid))
                    .findFirst()
                    .orElse(null);
            if (player == null) {
                status = "【上次在线】 " + WhitelistTable.getInstance().getLastJoinAt(uuid);
            } else {
                status = "【当前在线】";
            }
        } catch (Exception e) {
            XLogger.error(e);
            return;
        }
        // get image from api https://api.mineatar.io/face/<UUID>
        BufferedImage headImage = null;
        try {
            headImage = ImageIO.read(new URL("https://api.mineatar.io/face/" + uuid + "?scale=12"));
        } catch (Exception e) {
            XLogger.error("Failed to fetch image for UUID: " + uuid, e);
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

        long messageID = messageEvent.getMessageId();
        List<MessageSegment> playerInfos = new ArrayList<>();
        playerInfos.add(new ReplySegment(messageID));
        if (headImage != null) {
            playerInfos.add(new ImageSegment(headImage));
        }
        playerInfos.add(new TextSegment("【游戏昵称】：" + lastKnownName + "\n"));
        playerInfos.add(new TextSegment("【UUID】：" + uuid + "\n"));
        playerInfos.add(new TextSegment(status + "\n"));

        if (messageEvent instanceof GroupMessage groupMessage) {
            long groupID = groupMessage.getGroupID();
            GroupOperation.SendGroupMessage(groupID, playerInfos.toArray(new MessageSegment[0]));
        } else {
            PrivateOperation.SendPrivateMessage(userId, playerInfos.toArray(new MessageSegment[0]));
        }
    }
}
