package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.DeerlingBot;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TemplateFactory implements AutoCloseable {

    private final String templateName;
    private String indexFileContent;
    private Map<String, byte[]> resourcesFileContent = new HashMap<>();
    private final Path outputPath;

    public TemplateFactory(String templateName) {
        this.templateName = templateName;
        // plugins/DeerlingBot/templates/public
        File publicFileRoot = new File(DeerlingBot.getInstance().getDataFolder(), "templates/public");
        if (!publicFileRoot.exists()) {
            throw new RuntimeException("找不到公共目录: " + publicFileRoot.getAbsolutePath());
        }
        // plugins/DeerlingBot/templates/<templateName>
        File templateFileRoot = new File(DeerlingBot.getInstance().getDataFolder(), "templates/" + templateName);
        if (!templateFileRoot.exists()) {
            throw new RuntimeException("找不到模板目录: " + templateFileRoot.getAbsolutePath());
        }
        // plugins/DeerlingBot/templates/<templateName>/index.html
        File indexFile = new File(templateFileRoot, "index.html");
        if (!indexFile.exists()) {
            throw new RuntimeException("找不到模板文件: " + indexFile.getAbsolutePath());
        }
        try {
            outputPath = Files.createTempDirectory("screen_shoot_data_");
            // read: plugins/DeerlingBot/templates/<templateName>/index.html
            indexFileContent = Files.readString(indexFile.toPath());
            // read: plugins/DeerlingBot/templates/<templateName>/
            File[] templateFiles = templateFileRoot.listFiles();
            if (templateFiles == null) {
                throw new RuntimeException("读取目录失败: " + templateFileRoot.getAbsolutePath());
            }
            resourcesFileContent.putAll(loadResourcesRecursively(templateFiles, templateName + "/"));
            // read: plugins/DeerlingBot/templates/public/
            File[] publicFiles = publicFileRoot.listFiles();
            if (publicFiles == null) {
                throw new RuntimeException("读取目录失败: " + publicFileRoot.getAbsolutePath());
            }
            resourcesFileContent.putAll(loadResourcesRecursively(publicFiles, "public/"));
        } catch (Exception e) {
            throw new RuntimeException("读取模板文件失败: " + e.getMessage(), e);
        }
    }

    public TemplateFactory setPlaceholder(String key, String value) {
        indexFileContent = indexFileContent.replace("{{" + key + "}}", value);
        return this;
    }

    public File build(OfflinePlayer player) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
                indexFileContent = PlaceHolderApiManager.setPlaceholders(player, indexFileContent);
            File indexFile = new File(outputPath.toFile(), templateName + "/index.html");
            if (!indexFile.getParentFile().exists()) {
                boolean re = indexFile.getParentFile().mkdirs();
            }
            XLogger.debug(indexFileContent);
            Files.writeString(indexFile.toPath(), indexFileContent);
            for (Map.Entry<String, byte[]> entry : resourcesFileContent.entrySet()) {
                File resourceFile = new File(outputPath.toFile(), entry.getKey());
                if (!resourceFile.getParentFile().exists()) {
                    boolean re = resourceFile.getParentFile().mkdirs();
                }
                Files.write(resourceFile.toPath(), entry.getValue());
            }
            return indexFile;
        } catch (Exception e) {
            throw new RuntimeException("写入模板文件失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws Exception {
        if (outputPath.toFile().exists()) {
            boolean re = outputPath.toFile().delete();
        }
        if (resourcesFileContent != null) {
            resourcesFileContent.clear();
        }
        indexFileContent = null;
        resourcesFileContent = null;
    }

    private static Map<String, byte[]> loadResourcesRecursively(@NotNull File[] files, String prefix) {
        Map<String, byte[]> resources = new HashMap<>();
        for (File child : files) {
            if (child.isDirectory()) {
                File[] f = child.listFiles();
                if (f == null) {
                    throw new RuntimeException("读取目录失败: " + child.getAbsolutePath());
                }
                resources.putAll(loadResourcesRecursively(f, prefix + child.getName() + "/"));
            } else {
                if (child.getName().equals("index.html")) return resources;
                try {
                    resources.put(prefix + child.getName(), Files.readAllBytes(child.toPath()));
                } catch (Exception e) {
                    throw new RuntimeException("读取文件失败: " + child.getAbsolutePath() + " " + e.getMessage(), e);
                }
            }
        }
        return resources;
    }
}
