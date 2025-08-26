package cn.lunadeer.mc.deerlingbot.managers;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.formatString;

public class ResourceDownloader {

    private final static String USER = "LunaDeerMC";
    private final static String REPO = "DeerlingBot_resources";

    private final JavaPlugin plugin;
    private static ResourceDownloader instance;

    private final int[] LIBS_VER = {2025, 4, 15, 6, 57, 31};  // libs-VER
    private final int[] TEMPLATES_VER = {2025, 4, 25, 3, 44, 5};  // templates-VER

    public ResourceDownloader(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static ResourceDownloader getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ResourceDownloader is not initialized");
        }
        return instance;
    }

    public int[] getLatestTemplatesVer() {
        return TEMPLATES_VER;
    }

    public int[] getLatestLibsVer() {
        return LIBS_VER;
    }

    public String libsTag(int[] ver) {
        return "libs-" + String.format("%04d", ver[0]) + "." +
                String.format("%02d", ver[1]) + "." +
                String.format("%02d", ver[2]) + "." +
                String.format("%02d", ver[3]) + "." +
                String.format("%02d", ver[4]) + "." +
                String.format("%02d", ver[5]);
    }

    public String libsLink() {
        return formatString("https://github.com/{0}/{1}/releases/download/{2}/libs-{3}.zip", USER, REPO, libsTag(LIBS_VER), getSys().name);
    }

    public String templatesTag(int[] ver) {
        return "templates-" + String.format("%04d", ver[0]) + "." +
                String.format("%02d", ver[1]) + "." +
                String.format("%02d", ver[2]) + "." +
                String.format("%02d", ver[3]) + "." +
                String.format("%02d", ver[4]) + "." +
                String.format("%02d", ver[5]);
    }

    public String templatesLink() {
        return formatString("https://github.com/{0}/{1}/releases/download/{2}/templates.zip", USER, REPO, templatesTag(TEMPLATES_VER));
    }

    public int[] getTemplatesVer() {
        File file = new File(plugin.getDataFolder(), "templates/version.txt");
        if (!file.exists()) {
            throw new RuntimeException("未下载 | 找不到 templates/version.txt 文件");
        }
        return getVersionNumbers(file);
    }

    public int[] getLibsVer() {
        File file = new File(plugin.getDataFolder(), "libs/version.txt");
        if (!file.exists()) {
            throw new RuntimeException("未下载 | 找不到 libs/version.txt 文件");
        }
        return getVersionNumbers(file);
    }

    private static int[] getVersionNumbers(File file) {
        try {
            String version = Files.readString(file.toPath()).replace("\n", "").replace("\r", "");
            String[] versionParts = version.split("\\.");
            if (versionParts.length != 6) {
                throw new RuntimeException(file.getAbsolutePath() + " 文件格式错误");
            }
            int[] versionNumbers = new int[6];
            for (int i = 0; i < versionParts.length; i++) {
                versionNumbers[i] = Integer.parseInt(versionParts[i]);
            }
            return versionNumbers;
        } catch (Exception e) {
            throw new RuntimeException("读取版本号失败: " + e.getMessage(), e);
        }
    }

    public static boolean needUpdate(int[] currentVersion, int[] newVersion) {
        for (int i = 0; i < currentVersion.length; i++) {
            if (currentVersion[i] < newVersion[i]) {
                return true;
            } else if (currentVersion[i] > newVersion[i]) {
                return false;
            }
        }
        return false;
    }

    private enum SYS_TYPE {
        LINUX_X64("linux64"),
        WINDOWS_64("win64"),
        MACOS_X64("mac-x64"),
        MACOS_ARM64("mac-arm64");

        public final String name;

        SYS_TYPE(String name) {
            this.name = name;
        }
    }

    private static SYS_TYPE getSys() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        if (os.contains("win")) {
            return SYS_TYPE.WINDOWS_64;
        } else if (os.contains("mac")) {
            if (arch.contains("arm")) {
                return SYS_TYPE.MACOS_ARM64;
            } else {
                return SYS_TYPE.MACOS_X64;
            }
        } else if (os.contains("nix") || os.contains("nux")) {
            return SYS_TYPE.LINUX_X64;
        } else {
            throw new RuntimeException("Unsupported OS: " + os);
        }
    }
}
