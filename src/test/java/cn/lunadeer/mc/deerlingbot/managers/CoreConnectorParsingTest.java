package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import com.alibaba.fastjson2.JSONObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.net.http.WebSocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoreConnectorParsingTest {
    private final WebSocket socket = mock(WebSocket.class);
    private final PluginManager plugins = mock(PluginManager.class);
    private WebSocket.Listener listener;
    private CoreConnector previousInstance;
    private MockedStatic<Scheduler> scheduler;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<XLogger> logger;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = mockStatic(Scheduler.class);
        bukkit = mockStatic(Bukkit.class);
        logger = mockStatic(XLogger.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        scheduler.when(() -> Scheduler.runTask(any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return null;
        });
        previousInstance = (CoreConnector) instanceField().get(null);
        CoreConnector connector = new CoreConnector();
        Field field = CoreConnector.class.getDeclaredField("listener");
        field.setAccessible(true);
        listener = (WebSocket.Listener) field.get(connector);
    }

    @AfterEach
    void tearDown() throws Exception {
        instanceField().set(null, previousInstance);
        logger.close();
        bukkit.close();
        scheduler.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "null", "[]", "{\"post_type\":\"message\"}",
            "{\"post_type\":\"message\",\"message_type\":\"group\"}",
            "{\"post_type\":\"meta_event\",\"meta_event_type\":\"lifecycle\"}"})
    void malformedOrIncompleteEventDoesNotPreventTheNextMessage(String input) {
        assertDoesNotThrow(() -> listener.onText(socket, input, true));
        verifyNoInteractions(plugins);
        assertNextMessageIsDelivered();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[{\"type\":\"at\",\"data\":{\"qq\":\"invalid\"}}]",
            "[{\"type\":\"text\",\"data\":{}}]", "[{\"type\":\"text\",\"data\":null}]",
            "[{\"type\":\"text\",\"data\":{\"text\":42}}]", "[null]",
            "\"[CQ:at,qq=invalid]\"", "42"})
    void malformedMessageSegmentIsRejectedBeforeDispatchAndConnectionRemainsUsable(String content) {
        JSONObject event = event();
        event.put("message", com.alibaba.fastjson2.JSON.parse(content));
        assertDoesNotThrow(() -> listener.onText(socket, event.toJSONString(), true));
        verifyNoInteractions(plugins);
        assertNextMessageIsDelivered();
    }

    @Test
    void unknownSubTypeIsIsolatedBeforeNextValidEvent() {
        JSONObject invalid = event();
        invalid.put("sub_type", "invalid");
        assertDoesNotThrow(() -> listener.onText(socket, invalid.toJSONString(), true));
        verifyNoInteractions(plugins);
        assertNextMessageIsDelivered();
    }

    @Test
    void fragmentedMessageIsDecodedExactlyOnceAfterTheFinalFrame() {
        String payload = event().toJSONString();
        int first = payload.length() / 3;
        assertDoesNotThrow(() -> listener.onText(socket, payload.substring(0, first), false));
        assertDoesNotThrow(() -> listener.onText(socket, payload.substring(first, first * 2), false));
        verifyNoInteractions(plugins);
        assertDoesNotThrow(() -> listener.onText(socket, payload.substring(first * 2), true));
        verify(plugins).callEvent(isA(GroupMessage.class));
        verifyNoMoreInteractions(plugins);
        verify(socket, times(3)).request(1);
        verify(socket, never()).abort();
    }

    private void assertNextMessageIsDelivered() {
        listener.onText(socket, event().toJSONString(), true);
        ArgumentCaptor<GroupMessage> captured = ArgumentCaptor.forClass(GroupMessage.class);
        verify(plugins).callEvent(captured.capture());
        assertEquals("/list", assertInstanceOf(TextSegment.class, captured.getValue().getMessage().get(0)).getText());
        verifyNoMoreInteractions(plugins);
        verify(socket, times(2)).request(1);
        verify(socket, never()).abort();
    }

    private static JSONObject event() {
        return JSONObject.parseObject("""
                {"post_type":"message","message_type":"group","sub_type":"normal", "message_id":7,
                 "self_id":999001,"time":1,"group_id":10001,"user_id":42,
                 "message":"/list","raw_message":"/list",
                 "sender":{"user_id":42,"nickname":"玩家","sex":"unknown","age":0}}
                """);
    }

    private static Field instanceField() throws Exception {
        Field field = CoreConnector.class.getDeclaredField("instance");
        field.setAccessible(true);
        return field;
    }
}
