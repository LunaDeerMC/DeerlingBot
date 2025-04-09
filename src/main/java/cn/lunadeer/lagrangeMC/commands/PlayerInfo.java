package cn.lunadeer.lagrangeMC.commands;

import cn.lunadeer.lagrangeMC.LagrangeMC;
import cn.lunadeer.lagrangeMC.protocols.GroupOperation;
import cn.lunadeer.lagrangeMC.protocols.PrivateOperation;
import cn.lunadeer.lagrangeMC.tables.WhitelistTable;
import cn.lunadeer.lagrangeMC.utils.XLogger;
import com.alibaba.fastjson2.JSONObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.*;

public class PlayerInfo extends BotCommand {

    public PlayerInfo() {
        super("info", "查看个人信息", false, false, true);
    }

    @Override
    public void handle(long userId, String commandText, JSONObject jsonObject) {
        UUID uuid;
        String lastKnownName;
        String status;
        try {
            uuid = WhitelistTable.getInstance().getUserUUID(userId);
            lastKnownName = WhitelistTable.getInstance().getLastKnownName(uuid);
            Player player = LagrangeMC.getInstance().getServer().getOnlinePlayers().stream()
                    .filter(p -> p.getUniqueId().equals(uuid))
                    .findFirst()
                    .orElse(null);
            if (player == null) {
                status = "上次在线 " + WhitelistTable.getInstance().getLastJoinAt(uuid);
            } else {
                status = "当前在线";
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

        if (!jsonObject.containsKey("message_id")) return;
        long messageID = jsonObject.getLong("message_id");
        List<JSONObject> playerInfos = new ArrayList<>();
        playerInfos.add(ReplySegment(messageID));
        if (headImage != null) {
            playerInfos.add(ImageSegment(headImage));
        }
        playerInfos.add(TextSegment("【游戏昵称】：" + lastKnownName + "\n"));
        playerInfos.add(TextSegment("【UUID】：" + uuid + "\n"));
        playerInfos.add(TextSegment("【状态】：" + status + "\n"));
        playerInfos.add(TextSegment("=======统计========\n"));
        playerInfos.add(TextSegment("【累计挖掘石头】：" + offlinePlayer.getStatistic(Statistic.MINE_BLOCK, Material.STONE) + "\n"));
        playerInfos.add(TextSegment("【累计行走距离】：" + offlinePlayer.getStatistic(Statistic.WALK_ONE_CM) / 100 + "m\n"));
        playerInfos.add(TextSegment("【累计游玩时间】：" + offlinePlayer.getStatistic(Statistic.TOTAL_WORLD_TIME) / 20 / 60 + "分钟\n"));

        if (jsonObject.containsKey("group_id")) {
            long groupID = jsonObject.getLong("group_id");
            GroupOperation.SendGroupMessage(groupID, playerInfos.toArray(new JSONObject[0]));
        } else {
            PrivateOperation.SendPrivateMessage(userId, playerInfos.toArray(new JSONObject[0]));
        }

    }

}
