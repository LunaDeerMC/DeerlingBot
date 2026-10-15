package cn.lunadeer.mc.deerlingbot.commands;

import cn.lunadeer.mc.deerlingbot.managers.AvatarManager;
import cn.lunadeer.mc.deerlingbot.managers.PlayerSnapshotService;
import cn.lunadeer.mc.deerlingbot.protocols.PrivateOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.PrivateMessage;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ImageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.tables.WhitelistTable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimpleInfoTest {
    @Test
    void failedAvatarStillRepliesWithTextWithoutBlockingTheCommand() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CompletableFuture<BufferedImage> image = new CompletableFuture<>();
            when(fixture.avatars.fetch(fixture.uuid)).thenReturn(image);

            new SimpleInfo().handle(fixture.message);
            fixture.replies.verifyNoInteractions();
            image.complete(null);

            ArgumentCaptor<MessageSegment[]> segments = ArgumentCaptor.forClass(MessageSegment[].class);
            fixture.replies.verify(() -> PrivateOperation.SendPrivateMessage(eq(7L), segments.capture()));
            assertTrue(Arrays.stream(segments.getValue()).noneMatch(ImageSegment.class::isInstance));
            String text = Arrays.stream(segments.getValue()).filter(TextSegment.class::isInstance)
                    .map(TextSegment.class::cast).map(TextSegment::getText).reduce("", String::concat);
            assertTrue(text.contains("玩家甲"));
            assertTrue(text.contains(fixture.uuid.toString()));
            assertTrue(text.contains("当前在线"));
        }
    }

    @Test
    void cancellationDuringShutdownDoesNotSendAReply() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CompletableFuture<BufferedImage> image = new CompletableFuture<>();
            when(fixture.avatars.fetch(fixture.uuid)).thenReturn(image);
            new SimpleInfo().handle(fixture.message);

            when(fixture.avatars.isClosed()).thenReturn(true);
            image.cancel(false);

            fixture.replies.verifyNoInteractions();
        }
    }

    @Test
    void databaseWaitAcrossReloadKeepsTheOriginalAvatarManager() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AvatarManager replacement = mock(AvatarManager.class);
            when(fixture.table.getUserUUID(7L)).thenAnswer(invocation -> {
                fixture.managers.when(AvatarManager::getInstance).thenReturn(replacement);
                when(fixture.avatars.isClosed()).thenReturn(true);
                return fixture.uuid;
            });
            when(fixture.avatars.fetch(fixture.uuid)).thenReturn(
                    CompletableFuture.failedFuture(new CancellationException("重载已关闭旧下载器")));

            new SimpleInfo().handle(fixture.message);

            verify(fixture.avatars).fetch(fixture.uuid);
            verifyNoInteractions(replacement);
            fixture.replies.verifyNoInteractions();
            fixture.tables.verify(WhitelistTable::getInstance, times(1));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final UUID uuid = UUID.randomUUID();
        final AvatarManager avatars = mock(AvatarManager.class);
        final WhitelistTable table = mock(WhitelistTable.class);
        final PlayerSnapshotService snapshot = mock(PlayerSnapshotService.class);
        final PrivateMessage message = mock(PrivateMessage.class);
        final MockedStatic<AvatarManager> managers = mockStatic(AvatarManager.class);
        final MockedStatic<WhitelistTable> tables = mockStatic(WhitelistTable.class);
        final MockedStatic<PlayerSnapshotService> snapshots = mockStatic(PlayerSnapshotService.class);
        final MockedStatic<PrivateOperation> replies = mockStatic(PrivateOperation.class);

        Fixture() throws Exception {
            managers.when(AvatarManager::getInstance).thenReturn(avatars);
            tables.when(WhitelistTable::getInstance).thenReturn(table);
            snapshots.when(PlayerSnapshotService::getInstance).thenReturn(snapshot);
            when(message.getUserId()).thenReturn(7L);
            when(message.getMessageId()).thenReturn(11);
            when(table.getUserUUID(7L)).thenReturn(uuid);
            when(table.getLastKnownName(uuid)).thenReturn("玩家甲");
            when(snapshot.isOnline(uuid)).thenReturn(true);
        }

        @Override
        public void close() {
            replies.close();
            snapshots.close();
            tables.close();
            managers.close();
        }
    }
}
