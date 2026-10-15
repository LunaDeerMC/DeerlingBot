package cn.lunadeer.mc.deerlingbot.commands;

import cn.lunadeer.mc.deerlingbot.managers.PlayerSnapshotService;
import cn.lunadeer.mc.deerlingbot.managers.TemplateFactory;
import cn.lunadeer.mc.deerlingbot.managers.WebDriverManager;
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

    private final String template = "user_info";

    public FancyInfo() {
        super("info", "查看个人信息", false, false, true);
    }

    @Override
    public void handle(Message messageEvent, String... args) {
        try {
            // 先固定本次任务所属服务，慢 SQL 返回后也不能借用重新启用的新实例。
            WebDriverManager manager = WebDriverManager.getInstance();
            PlayerSnapshotService snapshots = PlayerSnapshotService.getInstance();
            WhitelistTable table = WhitelistTable.getInstance();
            UUID uuid = table.getUserUUID(messageEvent.getUserId());
            String name = table.getLastKnownName(uuid);
            String lastJoin = table.getLastJoinAt(uuid);
            String source;
            try (TemplateFactory userInfo = new TemplateFactory(template)) {
                source = userInfo.content();
            }
            CompletableFuture<BufferedImage> image = snapshots.captureInfo(uuid, name, lastJoin, source)
                    .thenCompose(snapshot -> Scheduler.callTaskAsync(() ->
                            manager.render(uuid, () -> renderInfo(snapshot.html(), manager))).thenCompose(future -> future));
            replyWhenReady(messageEvent, manager, image);
        } catch (Exception e) {
            XLogger.error(e);
            reply(messageEvent, new TextSegment("图片生成失败，请联系管理员"));
        }
    }

    void replyWhenReady(Message messageEvent, WebDriverManager manager, CompletableFuture<BufferedImage> imageFuture) {
        imageFuture.whenComplete((image, failure) -> {
            // 已完成的缓存 future 可能在注册回调的实体线程续接，回复解析和编码也必须显式异步。
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
                    content = new TextSegment("图片生成失败，请联系管理员检查模板和浏览器配置");
                } else {
                    content = new ImageSegment(image);
                }
                // 图片编码可能耗时，编码后再次确认原服务仍有效，发送时直接复用编码结果。
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

    private BufferedImage renderInfo(String html, WebDriverManager manager) {
        try (TemplateFactory userInfo = new TemplateFactory(template)) {
            return manager.takeScreenshot(userInfo.build(html), template);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate user info image", e);
        }
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
