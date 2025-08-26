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

import static cn.lunadeer.mc.deerlingbot.utils.Misc.*;

@FancyCommand
public class FancyInfo extends BotCommand {

    private final String template = "user_info";

    public FancyInfo() {
        super("info", "查看个人信息", false, false, true);
    }

    @Override
    public void handle(Message messageEvent, String... args) {
        UUID uuid;
        String lastKnownName;
        String status;
        try {
            uuid = WhitelistTable.getInstance().getUserUUID(messageEvent.getUserId());
            lastKnownName = WhitelistTable.getInstance().getLastKnownName(uuid);
            Player player = DeerlingBot.getInstance().getServer().getOnlinePlayers().stream()
                    .filter(p -> p.getUniqueId().equals(uuid))
                    .findFirst()
                    .orElse(null);
            if (player == null) {
                status = "[上次在线] " + WhitelistTable.getInstance().getLastJoinAt(uuid);
            } else {
                status = "[当前在线]";
            }
        } catch (Exception e) {
            XLogger.error(e);
            return;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

        long messageID = messageEvent.getMessageId();

        try (TemplateFactory userInfo = new TemplateFactory(template)) {
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
            offlinePlayer.getStatistic(Statistic.MOB_KILLS);
            BufferedImage userInfoImage = WebDriverManager.getInstance().takeScreenshot(userInfo.build(offlinePlayer), template);
            if (messageEvent instanceof GroupMessage groupMessage) {
                long groupID = groupMessage.getGroupID();
                GroupOperation.SendGroupMessage(groupID,
                        new ReplySegment(messageID), userInfoImage != null ?
                                new ImageSegment(userInfoImage) : new TextSegment("出错了，请联系管理员"));
            } else {
                PrivateOperation.SendPrivateMessage(messageEvent.getUserId(),
                        new ReplySegment(messageID), userInfoImage != null ?
                                new ImageSegment(userInfoImage) : new TextSegment("出错了，请联系管理员"));
            }
        } catch (Exception e) {
            XLogger.error("Failed to generate user info image");
            XLogger.error(e);
        }
    }

    private static String convertDecimal(String value) {
        return decimalConverter(Integer.parseInt(value));
    }

}
