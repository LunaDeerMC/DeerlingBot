package cn.lunadeer.lagrangeMC.managers;

import cn.lunadeer.lagrangeMC.LagrangeMC;
import cn.lunadeer.lagrangeMC.utils.XLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;

public class WebDriverManager {

    // Download from https://googlechromelabs.github.io/chrome-for-testing/#stable

    private static WebDriverManager instance;
    private final ChromeOptions options;
    private final JavaPlugin plugin;

    public WebDriverManager(JavaPlugin plugin) {
        instance = this;
        System.setProperty("webdriver.chrome.driver", new File(plugin.getDataFolder(), "libs/chromedriver").getAbsolutePath());
        options = new ChromeOptions();
        options.setBinary(new File(plugin.getDataFolder(), "libs/chrome/chrome").getAbsolutePath());
        options.addArguments("--headless"); // 无头模式
        options.addArguments("--window-size=1920,1080"); // 设置窗口大小
        options.addArguments("--disable-gpu"); // 避免某些环境下的GPU问题
        options.addArguments("--no-sandbox"); // 解决DevToolsActivePort文件不存在的报错
        options.addArguments("--lang=zh-CN");
        this.plugin = plugin;
    }

    private record AutoCloseDriver(ChromeDriver driver) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            if (driver != null) {
                driver.quit();
                XLogger.debug("ChromeDriver quit");
            }
        }
    }

    public BufferedImage takeScreenshot(File htmlFilePath, String elementId) {
        try (AutoCloseDriver autoCloseDriver = new AutoCloseDriver(new ChromeDriver(options))) {
            XLogger.debug("html file read: " + htmlFilePath.toURI());
            autoCloseDriver.driver().get(htmlFilePath.toURI().toString());
            WebElement element = new WebDriverWait(autoCloseDriver.driver(), Duration.ofSeconds(4))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.id(elementId)));
            XLogger.debug("found element: " + elementId);
            File screenshot = element.getScreenshotAs(OutputType.FILE);
            return ImageIO.read(screenshot);
        } catch (Exception e) {
            XLogger.error(e);
            return null;
        }
    }

    public static WebDriverManager getInstance() {
        if (instance == null) {
            instance = new WebDriverManager(LagrangeMC.getInstance());
        }
        return instance;
    }

    public void close() {
        if (instance != null) {
            instance = null;
        }
    }

}
