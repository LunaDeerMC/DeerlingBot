package cn.lunadeer.lagrangeMC.managers;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

public class ResourceDownloader {

    public ResourceDownloader(JavaPlugin plugin) {
        if (!Configuration.fancyCommand) return;
    }

    private enum SYS_TYPE {
        LINUX_X64,
        WINDOWS_64,
        MACOS_X64,
        MACOS_ARM64
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
