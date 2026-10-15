package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.http.ClientConfig;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class WebDriverManager implements AutoCloseable {

    private static volatile WebDriverManager instance;
    // 重载后的新实例也必须等待旧浏览器完成退出。
    private static final Semaphore BROWSER_PERMIT = new Semaphore(1, true);
    private final ThreadPoolExecutor executor;
    private final Map<UUID, CompletableFuture<BufferedImage>> inFlight = new HashMap<>();
    private final Map<UUID, CachedImage> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final int cacheCapacity;
    private final long cacheTtlNanos;
    private final BrowserFactory browserFactory;
    private volatile Thread rendererThread;
    private boolean closed;

    private record CachedImage(BufferedImage image, long createdAt) { }

    public WebDriverManager(JavaPlugin plugin) {
        this(4, 16, Duration.ofSeconds(30), () -> openChrome(plugin));
        instance = this;
    }

    WebDriverManager(int queueCapacity, int cacheCapacity, Duration cacheTtl) {
        this(queueCapacity, cacheCapacity, cacheTtl, () -> {
            throw new IllegalStateException("Browser factory is not configured");
        });
    }

    WebDriverManager(int queueCapacity, int cacheCapacity, Duration cacheTtl, BrowserFactory browserFactory) {
        this.cacheCapacity = cacheCapacity;
        this.cacheTtlNanos = cacheTtl.toNanos();
        this.browserFactory = browserFactory;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "DeerlingBot-image-renderer");
                    thread.setDaemon(true);
                    rendererThread = thread;
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** 最多运行一个浏览器，同一玩家的重复请求共用正在生成的图片。 */
    public synchronized CompletableFuture<BufferedImage> render(UUID playerId, Supplier<BufferedImage> renderer) {
        if (closed) {
            return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("Image renderer is closed"));
        }
        long now = System.nanoTime();
        cache.entrySet().removeIf(entry -> now - entry.getValue().createdAt() >= cacheTtlNanos);
        CachedImage cached = cache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.image());
        }
        CompletableFuture<BufferedImage> pending = inFlight.get(playerId);
        if (pending != null && !pending.isDone()) {
            return pending;
        }
        CompletableFuture<BufferedImage> result = new CompletableFuture<>();
        inFlight.put(playerId, result);
        try {
            executor.execute(() -> {
                try {
                    if (result.isCancelled()) return;
                    BufferedImage image = renderer.get();
                    synchronized (this) {
                        if (!closed && image != null && cacheCapacity > 0) {
                            cache.put(playerId, new CachedImage(image, System.nanoTime()));
                            while (cache.size() > cacheCapacity) {
                                cache.remove(cache.keySet().iterator().next());
                            }
                        }
                    }
                    result.complete(image);
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                } finally {
                    synchronized (this) {
                        inFlight.remove(playerId, result);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.remove(playerId);
            result.completeExceptionally(e);
        }
        return result;
    }

    /** 仅供通过有界队列准入的渲染任务调用，浏览器始终由同一个线程创建、使用和关闭。 */
    public BufferedImage takeScreenshot(File htmlFilePath, String elementId) {
        if (Thread.currentThread() != rendererThread) {
            throw new IllegalStateException("Screenshots must be submitted through render()");
        }
        boolean acquired = false;
        try {
            BROWSER_PERMIT.acquire();
            acquired = true;
            if (isClosed()) return null;
            try (OwnedBrowser browser = new OwnedBrowser(browserFactory.open())) {
                // 构造期间停用也必须等构造返回后退出浏览器，再释放跨实例许可。
                if (isClosed()) return null;
                return browser.browser.takeScreenshot(htmlFilePath, elementId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            if (!isClosed()) XLogger.error(e);
            return null;
        } finally {
            if (acquired) BROWSER_PERMIT.release();
        }
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public static WebDriverManager getInstance() {
        WebDriverManager current = instance;
        if (current == null) {
            throw new IllegalStateException("WebDriverManager is not initialized");
        }
        return current;
    }

    @Override
    public void close() {
        ArrayList<CompletableFuture<BufferedImage>> pending;
        synchronized (this) {
            if (closed) return;
            closed = true;
            pending = new ArrayList<>(inFlight.values());
            inFlight.clear();
            cache.clear();
        }
        pending.forEach(future -> future.cancel(false));
        executor.shutdownNow();
        if (instance == this) instance = null;
        // 不在插件线程执行 WebDriver 网络请求，也不等待自身回调退出。
        if (Thread.currentThread() != rendererThread) {
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @FunctionalInterface
    interface BrowserFactory {
        Browser open() throws Exception;
    }

    interface Browser extends AutoCloseable {
        BufferedImage takeScreenshot(File htmlFilePath, String elementId) throws Exception;

        @Override
        void close() throws Exception;
    }

    private static final class OwnedBrowser implements AutoCloseable {
        private final Browser browser;
        private final AtomicBoolean closed = new AtomicBoolean();

        private OwnedBrowser(Browser browser) {
            this.browser = browser;
        }

        @Override
        public void close() throws Exception {
            if (!closed.compareAndSet(false, true)) return;
            // shutdownNow 的中断不能让退出请求跳过清理，完成后恢复中断标志。
            boolean interrupted = Thread.interrupted();
            try {
                browser.close();
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    private static Browser openChrome(JavaPlugin plugin) {
        ChromeOptions options = new ChromeOptions();
        options.setBinary(new File(plugin.getDataFolder(), "libs/chrome/chrome").getAbsolutePath());
        options.addArguments("--headless", "--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--lang=zh-CN");
        ChromeDriverService service = new ChromeDriverService.Builder()
                .usingDriverExecutable(new File(plugin.getDataFolder(), "libs/chromedriver"))
                .withTimeout(Duration.ofSeconds(10)).build();
        ChromeDriver driver;
        try {
            driver = new ChromeDriver(service, options, ClientConfig.defaultConfig()
                    .connectionTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(15)));
        } catch (RuntimeException | Error e) {
            quitDriver(null, service);
            throw e;
        }
        return new Browser() {
            @Override
            public BufferedImage takeScreenshot(File htmlFilePath, String elementId) throws Exception {
                driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
                driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(5));
                driver.get(htmlFilePath.toURI().toString());
                WebElement element = new WebDriverWait(driver, Duration.ofSeconds(4))
                        .until(ExpectedConditions.visibilityOfElementLocated(By.id(elementId)));
                // 直接解码截图字节，避免 Selenium 额外生成临时 PNG 文件。
                return ImageIO.read(new ByteArrayInputStream(element.getScreenshotAs(OutputType.BYTES)));
            }

            @Override
            public void close() {
                quitDriver(driver, service);
            }
        };
    }

    private static void quitDriver(ChromeDriver driver, ChromeDriverService service) {
        boolean interrupted = Thread.interrupted();
        try {
            if (driver != null) driver.quit();
        } catch (Exception e) {
            XLogger.error(e);
        } finally {
            try {
                if (service != null) service.stop();
            } catch (Exception e) {
                XLogger.error(e);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }
}
