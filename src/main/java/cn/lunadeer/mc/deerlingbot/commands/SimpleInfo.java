package cn.lunadeer.mc.deerlingbot.commands;

import cn.lunadeer.mc.deerlingbot.managers.AvatarManager;
import cn.lunadeer.mc.deerlingbot.managers.PlayerSnapshotService;
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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class SimpleInfo extends BotCommand {
    public SimpleInfo() {
        super("info", "查看个人信息", false, false, true);
    }

    @Override
    public void handle(Message messageEvent, String... args) {
        AvatarManager avatars = AvatarManager.getInstance();
        UUID uuid;
        String lastKnownName;
        String status;
        long userId = messageEvent.getUserId();
        try {
            WhitelistTable whitelist = WhitelistTable.getInstance();
            uuid = whitelist.getUserUUID(userId);
            lastKnownName = whitelist.getLastKnownName(uuid);
            if (!PlayerSnapshotService.getInstance().isOnline(uuid)) {
                status = "【上次在线】 " + whitelist.getLastJoinAt(uuid);
            } else {
                status = "【当前在线】";
            }
        } catch (Exception e) {
            XLogger.error(e);
            return;
        }
        avatars.fetch(uuid).thenAccept(headImage -> {
            // 捕获本次请求的下载器，重载或停用取消的旧请求不得再发送回复。
            if (!avatars.isClosed()) reply(messageEvent, uuid, lastKnownName, status, headImage);
        });
    }

    private void reply(Message messageEvent, UUID uuid, String lastKnownName, String status, BufferedImage headImage) {
        long userId = messageEvent.getUserId();
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
