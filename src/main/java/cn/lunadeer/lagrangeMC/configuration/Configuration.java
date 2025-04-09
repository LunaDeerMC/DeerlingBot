package cn.lunadeer.lagrangeMC.configuration;

import cn.lunadeer.lagrangeMC.utils.configuration.*;

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
            "机器人接收指令的群聊列表",
            "仅以下群聊的指令会被机器人识别并执行"
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

    public static boolean debug = false;

}
