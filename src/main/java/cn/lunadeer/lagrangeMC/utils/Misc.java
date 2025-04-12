package cn.lunadeer.lagrangeMC.utils;

import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

public class Misc {

    public static boolean isPaper() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String formatString(String str, Object... args) {
        String formatStr = str;
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                args[i] = "[null for formatString (args[" + i + "])]";
            }
            formatStr = formatStr.replace("{" + i + "}", args[i].toString());
        }
        return formatStr;
    }

    public static String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int type = new Random().nextInt(2);
            if (type == 0) {
                code.append((char) (new Random().nextInt(26) + 'a'));
            } else {
                code.append((char) (new Random().nextInt(10) + '0'));
            }
        }
        return code.toString().toUpperCase();
    }

    public static List<String> listClassOfPackage(JavaPlugin plugin, String packageName) {
        List<String> classesInPackage = new ArrayList<>();
        // list all classes in the packageName package
        String path = packageName.replace('.', '/');
        URL packageDir = plugin.getClass().getClassLoader().getResource(path);
        if (packageDir == null) {
            return classesInPackage;
        }
        String packageDirPath = packageDir.getPath();
        // if the package is in a jar file, unpack it and list the classes
        packageDirPath = packageDirPath.substring(0, packageDirPath.indexOf("jar!") + 4);
        packageDirPath = packageDirPath.replace("file:", "");
        packageDirPath = packageDirPath.replace("!", "");
        // unpack the jar file
        XLogger.debug("Unpacking class in jar: {0}", packageDirPath);
        File jarFile = new File(packageDirPath);
        if (!jarFile.exists() || !jarFile.isFile()) {
            XLogger.debug("Skipping {0} because it is not a jar file", packageDirPath);
            return classesInPackage;
        }
        // list the classes in the jar file
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            jar.stream().filter(entry -> entry.getName().endsWith(".class") && entry.getName().startsWith(path))
                    .forEach(entry -> classesInPackage.add(entry.getName().replace('/', '.').substring(0, entry.getName().length() - 6)));
        } catch (Exception e) {
            XLogger.debug("Failed to list classes in jar: {0}", e.getMessage());
            return classesInPackage;
        }
        return classesInPackage;
    }

    public static String imageToBase64(BufferedImage image) {
        StringBuilder sb = new StringBuilder();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            sb.append(base64);
        } catch (IOException e) {
            XLogger.error(e);
        }
        return sb.toString();
    }

    public static void saveDefaultResource(JavaPlugin plugin, String resourcePath, boolean replace) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (file.exists() && !replace) {
            return;
        }
        try {
            if (!file.getParentFile().exists()) {
                boolean re = file.getParentFile().mkdirs();
            }
            plugin.saveResource(resourcePath, replace);
        } catch (Exception e) {
            XLogger.error(e);
        }
    }

    public static String distanceConverter(int cm) {
        if (cm < 100) {
            return cm + "cm";
        } else if (cm < 100000) {
            return String.format("%.2f", cm / 100.0) + " m";
        } else if (cm < 100000000) {
            return String.format("%.2f", cm / 100000.0) + " km";
        } else {
            return String.format("%.2f", cm / 100000000.0) + " 万km";
        }
    }

    public static String timeConverter(int tick) {
        int min = tick / 1200;
        if (min < 60) {
            return min + "分钟";
        } else if (min < 1440) {
            return String.format("%.2f", min / 60.0) + " 小时";
        } else {
            return String.format("%.2f", min / 1440.0) + " 天";
        }
    }
}
