package cn.lunadeer.lagrangeMC;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import cn.lunadeer.lagrangeMC.configuration.MessageText;
import cn.lunadeer.lagrangeMC.managers.*;
import cn.lunadeer.lagrangeMC.tables.WhitelistTable;
import cn.lunadeer.lagrangeMC.utils.Notification;
import cn.lunadeer.lagrangeMC.utils.XLogger;
import cn.lunadeer.lagrangeMC.utils.configuration.ConfigurationManager;
import cn.lunadeer.lagrangeMC.utils.databse.DatabaseManager;
import cn.lunadeer.lagrangeMC.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

public final class LagrangeMC extends JavaPlugin {

    private static LagrangeMC instance;

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
        // https://patorjk.com/software/taag/#p=display&f=Big&t=LagrangeMC
        XLogger.info("正在加载 LagrangeMC {0}", getDescription().getVersion());
        XLogger.info("  _                                            __  __  _____");
        XLogger.info(" | |                                          |  \\/  |/ ____|");
        XLogger.info(" | |     __ _  __ _ _ __ __ _ _ __   __ _  ___| \\  / | |");
        XLogger.info(" | |    / _` |/ _` | '__/ _` | '_ \\ / _` |/ _ \\ |\\/| | |");
        XLogger.info(" | |___| (_| | (_| | | | (_| | | | | (_| |  __/ |  | | |____");
        XLogger.info(" |______\\__,_|\\__, |_|  \\__,_|_| |_|\\__, |\\___|_|  |_|\\_____|");
        XLogger.info("               __/ |                 __/ |");
        XLogger.info("              |___/                 |___/");
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

    public static LagrangeMC getInstance() {
        if (instance == null) {
            throw new IllegalStateException("LagrangeMC is not initialized");
        }
        return instance;
    }
}
