package cn.lunadeer.mc.deerlingbot.commands;

import cn.lunadeer.mc.deerlingbot.DeerlingBot;
import cn.lunadeer.mc.deerlingbot.managers.PlaceHolderApiManager;
import cn.lunadeer.mc.deerlingbot.managers.TemplateFactory;
import cn.lunadeer.mc.deerlingbot.managers.WebDriverManager;
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
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.awt.image.BufferedImage;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.*;

@FancyCommand
public class FancyInfo extends BotCommand {

    private final String template = "user_info";

    public FancyInfo() {
        super("info", "查看个人信息", false, false, true);
    }

    @Override
    public void handle(Message messageEvent, String... args) {
        try {
            UUID uuid = WhitelistTable.getInstance().getUserUUID(messageEvent.getUserId());
            WebDriverManager manager = WebDriverManager.getInstance();
            manager.render(uuid, () -> renderInfo(uuid, manager)).whenComplete((image, error) -> {
                if (error instanceof CancellationException) return;
                if (error instanceof RejectedExecutionException) {
                    reply(messageEvent, new TextSegment("图片生成繁忙，请稍后重试"));
                } else if (error != null || image == null) {
                    if (error != null) XLogger.error(error);
                    reply(messageEvent, new TextSegment("图片生成失败，请联系管理员检查模板和浏览器配置"));
                } else {
                    reply(messageEvent, new ImageSegment(image));
                }
            });
        } catch (Exception e) {
            XLogger.error(e);
            reply(messageEvent, new TextSegment("图片生成失败，请联系管理员"));
        }
    }

    private BufferedImage renderInfo(UUID uuid, WebDriverManager manager) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        try (TemplateFactory userInfo = new TemplateFactory(template)) {
            String lastKnownName = WhitelistTable.getInstance().getLastKnownName(uuid);
            Player player = DeerlingBot.getInstance().getServer().getPlayer(uuid);
            String status = player == null
                    ? "[上次在线] " + WhitelistTable.getInstance().getLastJoinAt(uuid) : "[当前在线]";
            // /papi ecloud download Statistic
            userInfo.setPlaceholder("nickname", lastKnownName)
                    .setPlaceholder("status", status)
                    .setPlaceholder("avatar", "https://api.mineatar.io/face/" + uuid + "?scale=12") // get image from api https://api.mineatar.io/face/<UUID>
                    .setPlaceholder("time", timeConverter(offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE)))
                    .setPlaceholder("walk", distanceConverter(offlinePlayer.getStatistic(Statistic.WALK_ONE_CM)))
                    .setPlaceholder("break", Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ?
                            convertDecimal(PlaceHolderApiManager.setPlaceholders(offlinePlayer, "%statistic_mine_block%")) : "nil")
                    .setPlaceholder("craft", Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ?
                            convertDecimal(PlaceHolderApiManager.setPlaceholders(offlinePlayer, "%statistic_craft_item%")) : "nil")
            ;
            return manager.takeScreenshot(userInfo.build(offlinePlayer), template);
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

    private static String convertDecimal(String value) {
        return decimalConverter(Integer.parseInt(value));
    }

}
