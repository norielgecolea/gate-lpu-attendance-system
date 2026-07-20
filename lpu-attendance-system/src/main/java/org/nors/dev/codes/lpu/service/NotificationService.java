package org.nors.dev.codes.lpu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.AuthEventMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

@Service
public class NotificationService {

    private static final Logger log = LogManager.getLogger(NotificationService.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT = 512 * 1024;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public NotificationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MS,
                SEND_BUFFER_LIMIT
        );
        sessions.put(session.getId(), safe);
        log.info("WebSocket session registered: {} (active={})", session.getId(), sessions.size());
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        log.info("WebSocket session unregistered: {} (active={})", session.getId(), sessions.size());
    }

    public void broadcast(AuthEventMessage event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException ex) {
            log.error("Failed to serialize auth event", ex);
            return;
        }
        broadcastRaw(payload);
    }

    public void broadcastRaw(String payload) {
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (!session.isOpen()) {
                dead.add(entry.getKey());
                continue;
            }
            try {
                // Fresh message per session — shared TextMessage can fail after first send.
                session.sendMessage(new TextMessage(payload));
            } catch (Exception ex) {
                log.warn("Failed to send WS message to session {}", entry.getKey(), ex);
                dead.add(entry.getKey());
            }
        }
        dead.forEach(sessions::remove);
    }
}
