package cn.lunadeer.lagrangeMC.managers;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

// https://github.com/LunaDeerMC/LagrangeMC_resources/releases/download/<TAG>/libs-<SYS_TYPE>.zip
// https://github.com/LunaDeerMC/LagrangeMC_resources/releases/download/<TAG>/templates.zip
public class ResourceDownloader {

    private static String LIBS_TAG = "libs-2025.04.13.17.30.20";
    private static String TEMPLATES_TAG = "templates-2025.04.13.17.34.02";

    public ResourceDownloader(JavaPlugin plugin) {
        if (!Configuration.fancyCommand) return;
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

    private SYS_TYPE getSys() {
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
