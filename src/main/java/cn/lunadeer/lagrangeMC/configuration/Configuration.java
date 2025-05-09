package cn.lunadeer.lagrangeMC.configuration;

import cn.lunadeer.lagrangeMC.LagrangeMC;
import cn.lunadeer.lagrangeMC.managers.ResourceDownloader;
import cn.lunadeer.lagrangeMC.utils.XLogger;
import cn.lunadeer.lagrangeMC.utils.configuration.*;

import java.io.File;
import java.util.List;

public class Configuration extends ConfigurationFile {

    public static int version = 1;

    @Comments("数据库配置支持的类型： sqlite, mysql, pgsql")
    public static Database database = new Database();

    public static class Database extends ConfigurationPart {
        public String type = "sqlite";
        public String host = "localhost";
        public String port = "3306";
        public String database = "lagrangeMC";
        public String username = "lagrangeMC";
        public String password = "lagrangeMC";
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
            "是否使用支持图片返回结果的指令",
            "启用后部分指令（如info）会返回美观的图片结果",
            "此特性需要手动下载额外的包并正确配置环境",
    })
    public static boolean fancyCommand = false;

    @Comments({
            "机器人接收指令的群聊列表",
            "仅以下群聊的指令会被机器人识别并执行"
    })
    public static List<String> groupList = List.of("12345678", "87654321");

    @Comments({
            "机器人管理员QQ列表",
            "仅以下QQ用户可以使用机器人管理员指令"
    })
    public static List<String> adminAccountList = List.of("12345678", "87654321");

    @Comments({"白名单配置"})
    public static WhiteList whiteList = new WhiteList();

    public static class WhiteList extends ConfigurationPart {
        @HandleManually
        public enum Type {
            none,
            code,
            question
        }

        @Comment("白名单类型：none, code, question")
        public String type = Type.none.toString();

        @HandleManually
        public Type getType() {
            try {
                return Type.valueOf(type);
            } catch (IllegalArgumentException e) {
                XLogger.error("Invalid white list type: {0}", type);
                return Type.none;
            }
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

        @Comment("玩家退群后是否自动删除白名单")
        public boolean autoRemove = true;

        @Comments({"验证码白名单配置"})
        public Code code = new Code();

        public static class Code extends ConfigurationPart {
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
        }

        @Comments({"答题白名单配置"})
        public Question question = new Question();

        public static class Question extends ConfigurationPart {
            @Comment("总题数目（请确保 questions.yml 题库中的题目数量大于此值）")
            public int total = 10;

            @Comment("答对大于等于此数量的题自动获得白名单")
            public int pass = 8;

            public List<String> kickMessage = List.of(
                    "===========================",
                    "",
                    "本服务器需要问卷白名单",
                    "",
                    "请在群内私聊机器人发送 /question 参与问卷答题获取白名单",
                    "",
                    "==========================="
            );

            @HandleManually
            public String getKickMessage() {
                StringBuilder message = new StringBuilder().append("\n");
                for (String line : kickMessage) {
                    message.append(line).append("\n");
                }
                return message.toString();
            }
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

    public static boolean debug = false;

    @PostProcess
    public void postProcess() {
        File libs = new File(LagrangeMC.getInstance().getDataFolder(), "libs");
        File templates = new File(LagrangeMC.getInstance().getDataFolder(), "templates");
        if (!libs.exists() || !templates.exists()) {
            XLogger.error("由于缺少 libs 或 templates 文件夹，无法使用 fancyCommand 特性，详情请查阅文档。");
            XLogger.warn("libs 下载地址：{0}", ResourceDownloader.getInstance().libsLink());
            XLogger.warn("templates 下载地址：{0}", ResourceDownloader.getInstance().templatesLink());
        }
    }

}
