package cn.lunadeer.mc.deerlingbot;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.configuration.MessageText;
import cn.lunadeer.mc.deerlingbot.managers.*;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import cn.lunadeer.mc.deerlingbot.utils.Notification;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationManager;
import cn.lunadeer.mc.deerlingbot.utils.databse.DatabaseManager;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

public final class DeerlingBot extends JavaPlugin {

    private static DeerlingBot instance;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        new Scheduler(this);
        new Notification(this);
        new XLogger(this);
        new ResourceDownloader(this);
        try {
            ConfigurationManager.load(Configuration.class, new File(getDataFolder(), "config.yml"));
            ConfigurationManager.load(MessageText.class, new File(getDataFolder(), "messages.yml"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        XLogger.setDebug(Configuration.debug);
        // https://patorjk.com/software/taag/#p=display&f=Big&t=DeerlingBot
        XLogger.info("正在加载 DeerlingBot {0}", getDescription().getVersion());
        XLogger.info("  _____                 _ _             ____        _   ");
        XLogger.info(" |  __ \\               | (_)           |  _ \\      | |  ");
        XLogger.info(" | |  | | ___  ___ _ __| |_ _ __   __ _| |_) | ___ | |_ ");
        XLogger.info(" | |  | |/ _ \\/ _ \\ '__| | | | '_ \\ / _` |  _ < / _ \\| __|");
        XLogger.info(" | |__| |  __/  __/ |  | | | | | | | (_| | |_) | (_) | |_ ");
        XLogger.info(" |_____/ \\___|\\___|_|  |_|_|_| |_|\\__, |____/ \\___/ \\__|");
        XLogger.info("                                   __/ |                ");
        XLogger.info("                                  |___/                 ");
        new BukkitCommand(this);
        new DatabaseManager(this,
                Configuration.database.type, Configuration.database.host,
                Configuration.database.port, Configuration.database.database, Configuration.database.username,
                Configuration.database.password);
        new CoreConnector().open();
        new CommandManager(this);
        new BindManager(this);
        new MessageManager(this);
        new WebDriverManager(this);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceHolderApiManager(this);
        }
        this.getServer().getPluginManager().registerEvents(new JoinLeaveEvents(), this);

        try {
            new WhitelistTable();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        DatabaseManager.instance.close();
        CoreConnector.getInstance().close();
        WebDriverManager.getInstance().close();
    }

    public static DeerlingBot getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DeerlingBot is not initialized");
        }
        return instance;
    }

    public static String userPermission = "deerling.command.user";
    public static String adminPermission = "deerling.command.admin";
}
