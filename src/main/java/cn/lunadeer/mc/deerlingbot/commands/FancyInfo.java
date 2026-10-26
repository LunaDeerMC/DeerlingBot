package cn.lunadeer.mc.deerlingbot.commands;

import cn.lunadeer.mc.deerlingbot.managers.AvatarManager;
import cn.lunadeer.mc.deerlingbot.managers.InfoImageService;
import cn.lunadeer.mc.deerlingbot.managers.PlayerSnapshotService;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.PrivateOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.Message;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ImageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ReplySegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.SegmentType;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import com.alibaba.fastjson2.JSONObject;

import java.awt.image.BufferedImage;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

@FancyCommand
public class FancyInfo extends BotCommand {
    public FancyInfo() {
        super("info", "查看个人信息", false, false, true);
    }

    @Override
    public void handle(Message messageEvent, String... args) {
        try {
            // Capture the current renderer and avatar service: a reload must not transfer
            // in-flight results into another lifecycle.
            InfoImageService images = InfoImageService.getInstance();
            AvatarManager avatars = AvatarManager.getInstance();
            PlayerSnapshotService snapshots = PlayerSnapshotService.getInstance();
            WhitelistTable table = WhitelistTable.getInstance();
            UUID uuid = table.getUserUUID(messageEvent.getUserId());
            String name = table.getLastKnownName(uuid);
            String lastJoin = table.getLastJoinAt(uuid);

            CompletableFuture<BufferedImage> picture = snapshots.captureInfo(uuid, name, lastJoin)
                    .thenCombine(avatars.fetch(uuid), images::render)
                    .thenCompose(future -> future);
            replyWhenReady(messageEvent, images, picture);
        } catch (Exception e) {
            XLogger.error(e);
            reply(messageEvent, new TextSegment("图片生成失败，请联系管理员"));
        }
    }

    void replyWhenReady(Message messageEvent, InfoImageService manager, CompletableFuture<BufferedImage> imageFuture) {
        imageFuture.whenComplete((image, failure) -> {
            // Even already-completed futures may continue on an entity thread.
            Scheduler.callTaskAsync(() -> {
                if (manager.isClosed()) return null;
                Throwable error = failure;
                while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
                if (error instanceof CancellationException) return null;
                MessageSegment content;
                if (error instanceof RejectedExecutionException) {
                    content = new TextSegment("图片生成繁忙，请稍后重试");
                } else if (error != null || image == null) {
                    if (error != null) XLogger.error(error);
                    content = new TextSegment("图片生成失败，请联系管理员");
                } else {
                    content = new ImageSegment(image);
                }
                JSONObject encoded = content.parse();
                if (!manager.isClosed()) reply(messageEvent, new MessageSegment() {
                    @Override
                    public JSONObject parse() {
                        return encoded;
                    }

                    @Override
                    public SegmentType getType() {
                        return content.getType();
                    }
                });
                return null;
            });
        });
    }

    private void reply(Message messageEvent, MessageSegment content) {
        ReplySegment reply = new ReplySegment(messageEvent.getMessageId());
        if (messageEvent instanceof GroupMessage groupMessage) {
            GroupOperation.SendGroupMessage(groupMessage.getGroupID(), reply, content);
        } else {
            PrivateOperation.SendPrivateMessage(messageEvent.getUserId(), reply, content);
        }
    }
}
