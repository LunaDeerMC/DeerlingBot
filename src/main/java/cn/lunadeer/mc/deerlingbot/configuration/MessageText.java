package cn.lunadeer.mc.deerlingbot.configuration;

import cn.lunadeer.mc.deerlingbot.commands.BindPlayer;
import cn.lunadeer.mc.deerlingbot.commands.BotCommand;
import cn.lunadeer.mc.deerlingbot.commands.SimpleList;
import cn.lunadeer.mc.deerlingbot.managers.MessageManager;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationFile;

public class MessageText extends ConfigurationFile {
    public static BindPlayer.BindPlayerText bindPlayerText = new BindPlayer.BindPlayerText();
    public static SimpleList.ListPlayerText listPlayerText = new SimpleList.ListPlayerText();
    public static MessageManager.MessageManagerText messageManagerText = new MessageManager.MessageManagerText();
    public static BotCommand.BotCommandText botCommandText = new BotCommand.BotCommandText();
}
