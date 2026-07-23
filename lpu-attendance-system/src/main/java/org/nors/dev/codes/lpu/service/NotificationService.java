package org.nors.dev.codes.lpu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.AuthEventMessage;
import org.nors.dev.codes.lpu.model.Role;
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
    /** sessionId → display label for connected GUARD kiosks (location, else username). */
    private final Map<String, String> onlineGuards = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public NotificationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session, Role role, String username, String location) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MS,
                SEND_BUFFER_LIMIT
        );
        sessions.put(session.getId(), safe);
        log.info(
                "WebSocket session registered: {} user={} role={} location={} (active={})",
                session.getId(),
                username,
                role,
                location,
                sessions.size()
        );
        if (role == Role.GUARD) {
            String label = resolveGuardLabel(location, username);
            onlineGuards.put(session.getId(), label);
            log.info("Guard online: session={} label={} (guards={})", session.getId(), label, onlineGuards.size());
            broadcastGuardPresence();
        }
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        String removedGuard = onlineGuards.remove(session.getId());
        log.info(
                "WebSocket session unregistered: {} (active={}, wasGuard={})",
                session.getId(),
                sessions.size(),
                removedGuard != null
        );
        if (removedGuard != null) {
            broadcastGuardPresence();
        }
    }

    /** Distinct gate labels that currently have at least one connected GUARD kiosk. */
    public List<String> onlineGuardLocations() {
        TreeSet<String> locations = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        locations.addAll(onlineGuards.values());
        return List.copyOf(locations);
    }

    public void sendGuardPresence(WebSocketSession session) {
        sendRaw(session, guardPresencePayload());
    }

    public void broadcastGuardPresence() {
        broadcastRaw(guardPresencePayload());
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
        for (String id : dead) {
            sessions.remove(id);
            onlineGuards.remove(id);
        }
    }

    private void sendRaw(WebSocketSession session, String payload) {
        WebSocketSession target = sessions.getOrDefault(session.getId(), session);
        if (!target.isOpen()) {
            return;
        }
        try {
            target.sendMessage(new TextMessage(payload));
        } catch (Exception ex) {
            log.warn("Failed to send WS message to session {}", session.getId(), ex);
        }
    }

    private String guardPresencePayload() {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "GUARD_PRESENCE");
            event.put("locations", onlineGuardLocations());
            event.put("message", "Online guard locations updated");
            return objectMapper.writeValueAsString(event);
        } catch (IOException ex) {
            log.error("Failed to serialize guard presence", ex);
            return "{\"type\":\"GUARD_PRESENCE\",\"locations\":[]}";
        }
    }

    private static String resolveGuardLabel(String location, String username) {
        if (location != null && !location.isBlank()) {
            return location.trim();
        }
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        return "Unknown gate";
    }
}
