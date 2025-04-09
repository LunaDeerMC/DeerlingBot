package cn.lunadeer.lagrangeMC.configuration;

import cn.lunadeer.lagrangeMC.commands.BindPlayer;
import cn.lunadeer.lagrangeMC.commands.BotCommand;
import cn.lunadeer.lagrangeMC.commands.ListPlayer;
import cn.lunadeer.lagrangeMC.managers.MessageManager;
import cn.lunadeer.lagrangeMC.utils.configuration.ConfigurationFile;

public class MessageText extends ConfigurationFile {
    public static BindPlayer.BindPlayerText bindPlayerText = new BindPlayer.BindPlayerText();
    public static ListPlayer.ListPlayerText listPlayerText = new ListPlayer.ListPlayerText();
    public static MessageManager.MessageManagerText messageManagerText = new MessageManager.MessageManagerText();
    public static BotCommand.BotCommandText botCommandText = new BotCommand.BotCommandText();
}
