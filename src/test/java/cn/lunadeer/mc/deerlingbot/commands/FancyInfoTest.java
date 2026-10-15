package cn.lunadeer.mc.deerlingbot.commands;

import cn.lunadeer.mc.deerlingbot.managers.CoreConnector;
import cn.lunadeer.mc.deerlingbot.managers.WebDriverManager;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.Message;
import cn.lunadeer.mc.deerlingbot.utils.Misc;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FancyInfoTest {
    @Test
    void alreadyCompletedCacheFutureStillDefersEncodingAndSending() {
        WebDriverManager manager = mock(WebDriverManager.class);
        CoreConnector connector = mock(CoreConnector.class);
        Message event = mock(Message.class);
        BufferedImage cached = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        AtomicReference<Supplier<?>> queued = new AtomicReference<>();
        AtomicBoolean executingAsyncTask = new AtomicBoolean();
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<CoreConnector> core = mockStatic(CoreConnector.class);
             MockedStatic<Misc> misc = mockStatic(Misc.class)) {
            core.when(CoreConnector::getInstance).thenReturn(connector);
            scheduler.when(() -> Scheduler.callTaskAsync(any(Supplier.class))).thenAnswer(call -> {
                queued.set(call.getArgument(0));
                return new CompletableFuture<>();
            });
            misc.when(() -> Misc.imageToBase64(cached)).thenAnswer(call -> {
                assertTrue(executingAsyncTask.get(), "图片编码必须在显式异步回复任务内执行");
                return "encoded";
            });
            // 模拟实体线程注册回调时，截图缓存和中间异步 future 均已完成。
            new FancyInfo().replyWhenReady(event, manager, CompletableFuture.completedFuture(cached));
            assertNotNull(queued.get());
            misc.verifyNoInteractions();
            verifyNoInteractions(connector);
            executingAsyncTask.set(true);
            queued.get().get();
            misc.verify(() -> Misc.imageToBase64(cached));
            verify(connector).send(any());
        }
    }

    @Test
    void closingCapturedManagerDiscardsAQueuedCachedReply() {
        WebDriverManager manager = mock(WebDriverManager.class);
        Message event = mock(Message.class);
        AtomicReference<Supplier<?>> queued = new AtomicReference<>();
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<CoreConnector> core = mockStatic(CoreConnector.class);
             MockedStatic<Misc> misc = mockStatic(Misc.class)) {
            scheduler.when(() -> Scheduler.callTaskAsync(any(Supplier.class))).thenAnswer(call -> {
                queued.set(call.getArgument(0));
                return new CompletableFuture<>();
            });
            new FancyInfo().replyWhenReady(event, manager, CompletableFuture.completedFuture(
                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)));
            when(manager.isClosed()).thenReturn(true);
            queued.get().get();
            core.verifyNoInteractions();
            misc.verifyNoInteractions();
        }
    }

    @Test
    void closeDuringEncodingDiscardsThePreparedReply() {
        WebDriverManager manager = mock(WebDriverManager.class);
        Message event = mock(Message.class);
        BufferedImage cached = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        AtomicReference<Supplier<?>> queued = new AtomicReference<>();
        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<CoreConnector> core = mockStatic(CoreConnector.class);
             MockedStatic<Misc> misc = mockStatic(Misc.class)) {
            scheduler.when(() -> Scheduler.callTaskAsync(any(Supplier.class))).thenAnswer(call -> {
                queued.set(call.getArgument(0));
                return new CompletableFuture<>();
            });
            misc.when(() -> Misc.imageToBase64(cached)).thenAnswer(call -> {
                when(manager.isClosed()).thenReturn(true);
                return "encoded";
            });
            new FancyInfo().replyWhenReady(event, manager, CompletableFuture.completedFuture(cached));
            queued.get().get();
            misc.verify(() -> Misc.imageToBase64(cached));
            core.verifyNoInteractions();
        }
    }
}
