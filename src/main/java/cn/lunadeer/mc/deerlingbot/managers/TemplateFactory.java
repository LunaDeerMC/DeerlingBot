package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.DeerlingBot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TemplateFactory implements AutoCloseable {

    private final Path templateDirectory;
    private String indexFileContent;
    private Path outputFile;
    private boolean closed;

    public TemplateFactory(String templateName) {
        this(DeerlingBot.getInstance().getDataFolder().toPath().resolve("templates"), templateName);
    }

    TemplateFactory(Path templatesDirectory, String templateName) {
        templateDirectory = templatesDirectory.resolve(templateName);
        try {
            // 先读取模板，避免构造失败时遗留临时文件。
            indexFileContent = Files.readString(templateDirectory.resolve("index.html"));
        } catch (IOException e) {
            throw new IllegalStateException("读取模板文件失败: " + templateDirectory, e);
        }
    }

    public TemplateFactory setPlaceholder(String key, String value) {
        ensureOpen();
        indexFileContent = indexFileContent.replace("{{" + key + "}}", value);
        return this;
    }

    public String content() {
        ensureOpen();
        return indexFileContent;
    }

    /** 仅写入已在玩家所属线程解析完毕的 HTML，不再访问 Bukkit 或 PAPI。 */
    public File build(String content) {
        ensureOpen();
        return writeTemplate(content);
    }

    File writeTemplate(String content) {
        ensureOpen();
        try {
            if (outputFile == null) {
                // 与 index.html 保持同级，自定义相对资源和 <base> 无需改写或复制。
                outputFile = Files.createTempFile(templateDirectory, ".deerlingbot-render-", ".html");
            }
            Files.writeString(outputFile, content);
            return outputFile.toFile();
        } catch (IOException e) {
            try {
                close();
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw new IllegalStateException("写入模板失败，请确认模板目录可写: " + templateDirectory, e);
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        indexFileContent = null;
        if (outputFile != null) {
            Files.deleteIfExists(outputFile);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("TemplateFactory is closed");
        }
    }
}
