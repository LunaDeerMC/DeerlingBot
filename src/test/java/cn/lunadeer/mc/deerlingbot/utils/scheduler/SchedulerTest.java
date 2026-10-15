package cn.lunadeer.mc.deerlingbot.utils.scheduler;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchedulerTest {
    private JavaPlugin plugin;
    private Server server;
    private Player player;
    private EntityScheduler entity;
    private GlobalRegionScheduler global;

    @BeforeEach
    void setup() {
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        player = mock(Player.class);
        entity = mock(EntityScheduler.class);
        global = mock(GlobalRegionScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getGlobalRegionScheduler()).thenReturn(global);
        when(server.getAsyncScheduler()).thenReturn(mock(AsyncScheduler.class));
        when(player.getScheduler()).thenReturn(entity);
        new Scheduler(plugin);
    }

    @AfterEach
    void cleanup() {
        Scheduler.cancelAll();
        Scheduler.instance = null;
    }

    @Test
    void entityDataIsReadOnlyWhenTheOwningSchedulerRuns() {
        AtomicReference<Consumer<ScheduledTask>> work = new AtomicReference<>();
        when(entity.run(eq(plugin), any(), any())).thenAnswer(call -> {
            work.set(call.getArgument(1));
            return mock(ScheduledTask.class);
        });
        AtomicInteger reads = new AtomicInteger();
        CompletableFuture<Integer> result = Scheduler.callTask(player, reads::incrementAndGet);
        assertFalse(result.isDone());
        assertEquals(0, reads.get());
        verifyNoInteractions(global);
        work.get().accept(mock(ScheduledTask.class));
        assertEquals(1, result.join());
    }

    @Test
    void retiredEntitiesCompleteBothImmediateAndDeferredRequests() {
        when(entity.run(eq(plugin), any(), any())).thenReturn(null);
        assertThrows(CancellationException.class, () -> Scheduler.callTask(player, () -> "unexpected").join());
        AtomicReference<Runnable> retired = new AtomicReference<>();
        when(entity.run(eq(plugin), any(), any())).thenAnswer(call -> {
            retired.set(call.getArgument(2));
            return mock(ScheduledTask.class);
        });
        CompletableFuture<String> result = Scheduler.callTask(player, () -> "unexpected");
        retired.get().run();
        assertThrows(CancellationException.class, result::join);
    }

    @Test
    void disableCompletesWaitersAndPreventsLatePlayerReads() {
        AtomicReference<Consumer<ScheduledTask>> work = new AtomicReference<>();
        when(entity.run(eq(plugin), any(), any())).thenAnswer(call -> {
            work.set(call.getArgument(1));
            return mock(ScheduledTask.class);
        });
        AtomicInteger reads = new AtomicInteger();
        CompletableFuture<Integer> result = Scheduler.callTask(player, reads::incrementAndGet);
        Scheduler.cancelAll();
        work.get().accept(mock(ScheduledTask.class));
        assertTrue(result.isCancelled());
        assertEquals(0, reads.get());
        assertTrue(Scheduler.callTask(() -> "late").isCancelled());
    }

    @Test
    void paperBroadcastKeepsTheServerBroadcastApi() {
        AtomicReference<Consumer<ScheduledTask>> work = new AtomicReference<>();
        when(global.run(eq(plugin), any())).thenAnswer(call -> {
            work.set(call.getArgument(1));
            return mock(ScheduledTask.class);
        });
        CompletableFuture<Void> result = Scheduler.broadcastMessage("hello");
        verify(server, never()).broadcastMessage(anyString());
        work.get().accept(mock(ScheduledTask.class));
        result.join();
        verify(server).broadcastMessage("hello");
        verify(server, never()).getOnlinePlayers();
    }

    @Test
    void broadcastQueuedBeforeBusinessShutdownIsDiscarded() {
        AtomicReference<Consumer<ScheduledTask>> work = new AtomicReference<>();
        when(global.run(eq(plugin), any())).thenAnswer(call -> {
            work.set(call.getArgument(1));
            return mock(ScheduledTask.class);
        });
        AtomicBoolean active = new AtomicBoolean(true);
        CompletableFuture<Void> result = Scheduler.broadcastMessage("stale", active::get);
        active.set(false);
        work.get().accept(mock(ScheduledTask.class));
        result.join();
        verify(server, never()).broadcastMessage(anyString());
    }
}
