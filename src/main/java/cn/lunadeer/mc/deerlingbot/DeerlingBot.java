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
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

public final class DeerlingBot extends JavaPlugin {

    private static DeerlingBot instance;
    private DatabaseManager database;
    private DatabaseSettings databaseSettings;
    private CoreConnector connector;
    private CommandManager botCommands;
    private InfoImageService infoImages;
    private PlaceHolderApiManager placeholders;
    private JoinLeaveEvents joinLeaveEvents;
    private EventIoService eventIo;
    private PlayerSnapshotService playerSnapshots;
    private AvatarManager avatars;

    private record DatabaseSettings(String type, String host, String port, String name, String user, String password) {
        static DatabaseSettings current() {
            var config = Configuration.database;
            return new DatabaseSettings(config.type, config.host, config.port, config.database, config.username, config.password);
        }

        void restoreRuntime() {
            var config = new Configuration.Database();
            config.type = type;
            config.host = host;
            config.port = port;
            config.database = name;
            config.username = user;
            config.password = password;
            Configuration.database = config;
        }
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        new Scheduler(this);
        new Notification(this);
        new XLogger(this);
        try {
            loadSettings();
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
        try {
            configureDatabase();
            eventIo = new EventIoService(this);
            playerSnapshots = new PlayerSnapshotService(this);
            avatars = new AvatarManager();
            botCommands = new CommandManager(this);
            new BindManager(this);
            new MessageManager(this);
            infoImages = new InfoImageService();
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                placeholders = new PlaceHolderApiManager(this);
            }
            joinLeaveEvents = new JoinLeaveEvents(getDataFolder().toPath().resolve("pending-reviews.json"));
            getServer().getPluginManager().registerEvents(joinLeaveEvents, this);
            connector = new CoreConnector();
            // 文件读取在线程池执行；恢复完成后才接收新入群/退群事件。
            joinLeaveEvents.start(() -> {
                if (isEnabled() && connector != null) connector.open();
            }, error -> {
                getLogger().log(java.util.logging.Level.SEVERE, "无法恢复入群审核，插件已停用", error);
                getServer().getPluginManager().disablePlugin(this);
            });
        } catch (Exception e) {
            getServer().getPluginManager().disablePlugin(this);
            throw new RuntimeException(e);
        }
    }

    private void loadSettings() throws Exception {
        ConfigurationManager.load(Configuration.class, new File(getDataFolder(), "config.yml"));
        ConfigurationManager.load(MessageText.class, new File(getDataFolder(), "messages.yml"));
        XLogger.setDebug(Configuration.debug);
    }

    private void configureDatabase() throws SQLException {
        DatabaseSettings next = DatabaseSettings.current();
        if (next.equals(databaseSettings)) return;
        DatabaseManager previous = database;
        DatabaseManager replacement = new DatabaseManager(this, next.type(), next.host(), next.port(),
                next.name(), next.user(), next.password());
        try {
            new WhitelistTable();
        } catch (Exception e) {
            replacement.close();
            DatabaseManager.instance = previous;
            throw e;
        }
        database = replacement;
        databaseSettings = next;
        if (previous != null) previous.close();
    }

    public boolean reloadConfiguration() throws Exception {
        try {
            loadSettings();
            // 在途异步指令仍使用当前库，连接参数变更留待重启，避免跨库执行同一操作。
            boolean databaseRestartRequired = !DatabaseSettings.current().equals(databaseSettings);
            if (databaseRestartRequired) databaseSettings.restoreRuntime();
            if (joinLeaveEvents != null) joinLeaveEvents.reloadConfiguration();
            avatars.close();
            avatars = new AvatarManager();
            botCommands.reloadCommands();
            infoImages.close();
            infoImages = new InfoImageService();
            connector.close();
            if (joinLeaveEvents == null || joinLeaveEvents.isLoaded()) connector.open();
            // 保留监听器及正在进行的入群审核，避免重复注册或丢失审核超时任务。
            return databaseRestartRequired;
        } catch (Exception e) {
            XLogger.error(e);
            getServer().getPluginManager().disablePlugin(this);
            throw e;
        }
    }

    @Override
    public void onDisable() {
        // 每项清理独立执行，部分初始化失败也能安全关闭其余资源。
        if (avatars != null) closeResource(avatars::close);
        if (connector != null) closeResource(connector::close);
        // 先停止接收新任务并排空退群写入，再关闭数据库和调度器。
        if (eventIo != null) closeResource(eventIo::close);
        if (joinLeaveEvents != null) closeResource(joinLeaveEvents::close);
        if (playerSnapshots != null) closeResource(playerSnapshots::close);
        closeResource(() -> HandlerList.unregisterAll(this));
        closeResource(Scheduler::cancelAll);
        if (infoImages != null) closeResource(infoImages::close);
        if (placeholders != null) closeResource(() -> {
            try {
                placeholders.unregister();
            } finally {
                PlaceHolderApiManager.instance = null;
            }
        });
        if (database != null) closeResource(database::close);
        closeResource(cn.lunadeer.mc.deerlingbot.utils.command.CommandManager::close);
        closeResource(Notification::close);
        joinLeaveEvents = null;
        eventIo = null;
        playerSnapshots = null;
        avatars = null;
        connector = null;
        infoImages = null;
        placeholders = null;
        database = null;
        databaseSettings = null;
        botCommands = null;
        instance = null;
    }

    private void closeResource(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "关闭插件资源失败", e);
        }
    }

    public static void notifyBotConnected() {
        DeerlingBot plugin = instance;
        if (plugin != null && plugin.isEnabled() && plugin.joinLeaveEvents != null)
            plugin.joinLeaveEvents.onBotConnected();
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
