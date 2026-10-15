package cn.lunadeer.mc.deerlingbot.managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TemplateFactoryTest {
    @TempDir Path templates;

    private Path createTemplate() throws Exception {
        Path directory = Files.createDirectories(templates.resolve("user_info"));
        Files.writeString(directory.resolve("index.html"), "<img src='avatar.png'><link href='../public/style.css'>");
        Files.writeString(directory.resolve("avatar.png"), "custom image");
        Path publicDirectory = Files.createDirectories(templates.resolve("public"));
        Files.writeString(publicDirectory.resolve("style.css"), "custom style");
        return directory;
    }

    @Test
    void preservesRelativeResourcesWithoutCopyingAndDeletesOnlyGeneratedHtml() throws Exception {
        Path directory = createTemplate();
        Path rendered;
        String source = Files.readString(directory.resolve("index.html"));
        try (TemplateFactory template = new TemplateFactory(templates, "user_info")) {
            rendered = template.writeTemplate(source).toPath();
            assertEquals(directory, rendered.getParent());
            assertEquals("custom image", Files.readString(rendered.getParent().resolve("avatar.png")));
            assertEquals("custom style", Files.readString(rendered.getParent().resolve("../public/style.css")));
            assertEquals(source, Files.readString(rendered));
            try (var files = Files.walk(templates)) {
                assertEquals(4, files.filter(Files::isRegularFile).count());
            }
        }
        assertFalse(Files.exists(rendered));
        assertEquals(source, Files.readString(directory.resolve("index.html")));
        assertTrue(Files.exists(directory.resolve("avatar.png")));
        assertTrue(Files.exists(templates.resolve("public/style.css")));
    }

    @Test
    void concurrentTemplatesHaveIndependentOutputAndCleanupOnRenderingFailure() throws Exception {
        createTemplate();
        Path first;
        Path second;
        try (TemplateFactory a = new TemplateFactory(templates, "user_info");
             TemplateFactory b = new TemplateFactory(templates, "user_info")) {
            first = a.writeTemplate("first").toPath();
            second = b.writeTemplate("second").toPath();
            assertNotEquals(first, second);
            assertEquals("first", Files.readString(first));
            assertEquals("second", Files.readString(second));
            assertThrows(IllegalStateException.class, () -> {
                try (TemplateFactory failed = new TemplateFactory(templates, "user_info")) {
                    failed.writeTemplate("failed");
                    throw new IllegalStateException("screenshot failed");
                }
            });
            try (var files = Files.list(first.getParent())) {
                assertEquals(2, files.filter(path -> path.getFileName().toString().startsWith(".deerlingbot-render-")).count());
            }
        }
        assertFalse(Files.exists(first));
        assertFalse(Files.exists(second));
    }

    @Test
    void failedConstructorAndFailedWriteLeaveNoTemporaryFile() throws Exception {
        assertThrows(IllegalStateException.class, () -> new TemplateFactory(templates, "missing"));
        Path directory = createTemplate();
        try (TemplateFactory template = new TemplateFactory(templates, "user_info")) {
            Files.delete(directory.resolve("index.html"));
            Files.delete(directory.resolve("avatar.png"));
            Files.delete(directory);
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> template.writeTemplate("content"));
            assertTrue(error.getMessage().contains("模板目录可写"));
            assertFalse(Files.exists(directory));
        }
        try (var files = Files.walk(templates)) {
            assertEquals(0, files.filter(path -> path.getFileName().toString().startsWith(".deerlingbot-render-")).count());
        }
    }
}
