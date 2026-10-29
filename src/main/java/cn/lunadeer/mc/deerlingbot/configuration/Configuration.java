package cn.lunadeer.mc.deerlingbot.configuration;

import cn.lunadeer.mc.deerlingbot.utils.configuration.*;
import cn.lunadeer.mc.deerlingbot.managers.AchievementThresholds;

import java.util.List;

public class Configuration extends ConfigurationFile {

    public static int version = 1;

    @Comments("数据库配置支持的类型： sqlite, mysql, pgsql")
    public static Database database = new Database();

    public static class Database extends ConfigurationPart {
        public String type = "sqlite";
        public String host = "localhost";
        public String port = "3306";
        public String database = "DeerlingBot";
        public String username = "DeerlingBot";
        public String password = "DeerlingBot";
    }

    @Comments({
            "与 OneBot 连接的正向 WebSocket 配置",
            "仅支持 OneBot 11 协议",
    })
    public static OneBotWebSocket oneBotWebSocket = new OneBotWebSocket();

    public static class OneBotWebSocket extends ConfigurationPart {
        public String host = "127.0.0.1";
        public String port = "5700";
        public String token = "abcdefghijklmnopqrstuvwxyz==";
    }

    public static String botId = "12345678";

    public static String commandPrefix = "/";

    @Comments({
        "服务端指令执行前缀",
        "仅 admin-account-list 中的 QQ 账户可通过私聊使用",
        "群聊中的此前缀消息会被完全静默忽略"
    })
    public static String commandExecutePrefix = "!";

    @Comments({
            "是否使用支持图片返回结果的指令",
            "启用后部分指令（如info）会返回美观的图片结果",
            "使用内置 Java2D 绘图，无需下载浏览器或模板",
    })
    public static boolean fancyCommand = false;

    @Comments({
            "玩家信息图片的六类成就累计门槛；每类必须满足 0 < bronze < silver < gold",
            "elytra、walking 使用整数公里 km；其余使用累计数量或次数",
            "修改后使用 /deerling reload 生效；降低或提高门槛会重新计算已有玩家的等级",
    })
    public static AchievementThresholdConfiguration achievementThresholds = new AchievementThresholdConfiguration();

    private static volatile AchievementThresholds activeAchievementThresholds = AchievementThresholds.defaults();
    private AchievementThresholds preparedAchievementThresholds;

    public static AchievementThresholds achievementThresholdSnapshot() { return activeAchievementThresholds; }

    @PreProcess
    public void prepareAchievementThresholds() {
        preparedAchievementThresholds = AchievementThresholdConfiguration.read(getYaml());
        // Missing fields must use factory defaults, not stale values from a previous reload.
        achievementThresholds = new AchievementThresholdConfiguration();
    }

    @PostProcess
    public void publishAchievementThresholds() {
        activeAchievementThresholds = java.util.Objects.requireNonNull(preparedAchievementThresholds, "validated achievement thresholds");
    }

    @Comments({
            "机器人接收指令的群聊列表",
            "仅以下群聊的指令会被机器人识别并执行",
            "玩家退出列表中的任意一个群时撤销绑定，退出其他群不影响绑定"
    })
    public static List<String> groupList = List.of("12345678", "87654321");

    @Comments({
            "机器人管理员QQ列表",
            "仅以下QQ用户可以使用机器人管理员指令"
    })
    public static List<String> adminAccountList = List.of("12345678", "87654321");

    public static WhiteList whiteList = new WhiteList();

    public static class WhiteList extends ConfigurationPart {
        @Comment("是否要求白名单绑定")
        public boolean required = true;
        @Comments({
                "强制要求白名单绑定的话未绑定的玩家会无法加入服务器",
                "并提示 kick-message 配置的内容",
        })
        public List<String> kickMessage = List.of(
                "===========================",
                "",
                "本服务器需要白名单",
                "",
                "请在群内输入 /bind %code% 完成绑定",
                "",
                "==========================="
        );

        @HandleManually
        public String getKickMessage(String code) {
            StringBuilder message = new StringBuilder().append("\n");
            for (String line : kickMessage) {
                message.append(line).append("\n");
            }
            String finalMessage = message.toString();
            return finalMessage.replace("%code%", code);
        }

        @Comments({
                "如果不强制要求白名单",
                "那么玩家在进入服务器后会展示 bind-message 配置的信息提示绑定",
        })
        public String bindMessage = "在群内使用 /bind %code% 绑定QQ后可使用更丰富的功能";

        @HandleManually
        public String getBindMessage(String code) {
            return bindMessage.replace("%code%", code);
        }
    }

    @Comments("消息转发设置")
    public static MessageTransfer messageTransfer = new MessageTransfer();

    public static class MessageTransfer extends ConfigurationPart {
        @Comment("是否允许服务器的消息转发到群里")
        public boolean enable = false;
        @Comment("是否需要绑定QQ才能转发消息")
        public boolean bindRequired = true;
        @Comment("要转发的群消息（只支持与一个群聊之间转发）")
        public String groupId = "12345678";
        @Comment("只有以此前缀开头的服务器消息才会被转发到群，留空表示全部转发")
        public String serverFlag = "";
        @Comment("服务器消息前缀（支持PlaceHolderAPI）")
        public String serverPrefix = "[服务器消息]<%player_name%> ";
        @Comment("只有以此前缀开头的群消息才会被转发到服务器，留空表示全部转发")
        public String groupFlag = "#";
        @Comment("群消息前缀（不支持PlaceHolderAPI）")
        public String groupPrefix = "[群消息]<%nickname%> ";
    }

    @Comments("登录/登出消息设置（支持PlaceHolderAPI）")
    public static JoinQuitMessage joinQuitMessage = new JoinQuitMessage();

    public static class JoinQuitMessage extends ConfigurationPart {
        @Comment("是否启用玩家加入/离开服务器的消息")
        public boolean enable = true;
        @Comment("显示进入离开消息的群")
        public String groupId = "12345678";
        @Comment("加入服务器时的消息")
        public String joinMessage = "✔ [%player_name%] 加入了服务器";
        @Comment("离开服务器时的消息")
        public String quitMessage = "✖ [%player_name%] 离开了服务器";
    }

    @Comments("群聊入群欢迎消息设置")
    public static GroupWelcomeMessage groupWelcomeMessage = new GroupWelcomeMessage();

    @Comments({
            "群聊后置审核设置",
            "启用后会自动通过加群申请，通过申请或邀请入群的新用户均需完成一道简单算术题",
            "若 300 秒内未完成验证，将请求移出群聊",
            "重载切换群聊/私聊只影响新审核；关闭审核或移除群会取消对应审核",
            "待审核状态异步保存。重启恢复剩余时间；停机期间已过期则重新给予 300 秒"
    })
    public static PostJoinReview postJoinReview = new PostJoinReview();

    public static class PostJoinReview extends ConfigurationPart {
        @Comment("是否启用新用户进群后置审核")
        public boolean enable = false;
            @Comment("是否通过私聊发送后置审核验证消息")
            public boolean usePrivateMessage = false;
    }

    public static class GroupWelcomeMessage extends ConfigurationPart {
        @Comment("是否启用新用户进群欢迎")
        public boolean enable = true;

        @Comments({
                "欢迎消息内容，支持多行",
                "会自动在消息前 @ 新用户",
                "可用变量：%user_id%, %group_id%"
        })
        public List<String> message = List.of(
                "欢迎加入本群！",
                "请先阅读群公告和群规则。"
        );

        @HandleManually
        public String getMessage(long userId, long groupId) {
            return String.join("\n", message)
                    .replace("%user_id%", String.valueOf(userId))
                    .replace("%group_id%", String.valueOf(groupId));
        }
    }

    @Comment("是否强制设置群名片为玩家昵称(当玩家绑定时)")
    public static boolean syncCardName = true;

    public static boolean debug = false;


}
