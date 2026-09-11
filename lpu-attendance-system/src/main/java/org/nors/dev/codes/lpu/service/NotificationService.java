package org.nors.dev.codes.lpu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.AuthEventMessage;
import org.nors.dev.codes.lpu.model.KioskGroup;
import org.nors.dev.codes.lpu.model.KioskGroups;
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
    private static final long PING_TIMEOUT_NANOS = 8_000_000_000L;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** sessionId → connected kiosk (location label + venue). */
    private final Map<String, OnlineKiosk> onlineKiosks = new ConcurrentHashMap<>();
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
        if (KioskGroups.isKioskRole(role)) {
            OnlineKiosk kiosk = new OnlineKiosk(
                    resolveGuardLabel(location, username),
                    KioskGroups.fromRole(role)
            );
            onlineKiosks.put(session.getId(), kiosk);
            log.info(
                    "Kiosk online: session={} label={} group={} (kiosks={})",
                    session.getId(),
                    kiosk.label(),
                    kiosk.group(),
                    onlineKiosks.size()
            );
            broadcastGuardPresence();
            sendPing(session.getId(), kiosk);
        }
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        OnlineKiosk removed = onlineKiosks.remove(session.getId());
        log.info(
                "WebSocket session unregistered: {} (active={}, wasKiosk={})",
                session.getId(),
                sessions.size(),
                removed != null
        );
        if (removed != null) {
            broadcastGuardPresence();
        }
    }

    /** Distinct gate labels that currently have at least one connected Main Gates kiosk. */
    public List<String> onlineGuardLocations() {
        return onlineKioskLocations(KioskGroup.MAIN_GATES);
    }

    public List<String> onlineKioskLocations(KioskGroup group) {
        TreeSet<String> locations = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        KioskGroup target = group != null ? group : KioskGroup.MAIN_GATES;
        for (OnlineKiosk kiosk : onlineKiosks.values()) {
            if (kiosk.group() == target) {
                locations.add(kiosk.label());
            }
        }
        return List.copyOf(locations);
    }

    public Map<String, List<String>> onlineKiosksByGroup() {
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        for (KioskGroup group : KioskGroup.values()) {
            byGroup.put(group.name(), onlineKioskLocations(group));
        }
        return byGroup;
    }

    /** Worst (highest) measured ping ms per location, grouped by venue. Omits locations still waiting. */
    public Map<String, Map<String, Integer>> kioskPingsByGroup() {
        long now = System.nanoTime();
        Map<String, Map<String, Integer>> byGroup = new LinkedHashMap<>();
        for (KioskGroup group : KioskGroup.values()) {
            Map<String, Integer> pings = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (OnlineKiosk kiosk : onlineKiosks.values()) {
                if (kiosk.group() != group) {
                    continue;
                }
                kiosk.expireIfStale(now, PING_TIMEOUT_NANOS);
                Integer ms = kiosk.pingMs();
                if (ms == null) {
                    continue;
                }
                pings.merge(kiosk.label(), ms, Math::max);
            }
            byGroup.put(group.name(), pings);
        }
        return byGroup;
    }

    public void sendGuardPresence(WebSocketSession session) {
        sendRaw(session, guardPresencePayload());
    }

    public void broadcastGuardPresence() {
        broadcastRaw(guardPresencePayload());
    }

    public void pingKiosks() {
        long now = System.nanoTime();
        boolean expired = false;
        for (Map.Entry<String, OnlineKiosk> entry : onlineKiosks.entrySet()) {
            OnlineKiosk kiosk = entry.getValue();
            if (kiosk.expireIfStale(now, PING_TIMEOUT_NANOS)) {
                expired = true;
            }
            sendPing(entry.getKey(), kiosk);
        }
        if (expired) {
            broadcastGuardPresence();
        }
    }

    public void handleIncomingText(String sessionId, String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (!"KIOSK_PONG".equals(node.path("type").asText())) {
                return;
            }
            handleKioskPong(sessionId, node.path("id").asText(null));
        } catch (Exception ex) {
            log.debug("Ignoring malformed WebSocket text from session {}", sessionId);
        }
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
            onlineKiosks.remove(id);
        }
    }

    private void sendPing(String sessionId, OnlineKiosk kiosk) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        String id = UUID.randomUUID().toString();
        long sentAt = System.nanoTime();
        kiosk.beginPing(id, sentAt);
        try {
            Map<String, String> event = new LinkedHashMap<>();
            event.put("type", "KIOSK_PING");
            event.put("id", id);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        } catch (Exception ex) {
            log.warn("Failed to ping kiosk session {}", sessionId, ex);
            sessions.remove(sessionId);
            if (onlineKiosks.remove(sessionId) != null) {
                broadcastGuardPresence();
            }
        }
    }

    private void handleKioskPong(String sessionId, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        OnlineKiosk kiosk = onlineKiosks.get(sessionId);
        if (kiosk == null) {
            return;
        }
        Integer ms = kiosk.completePing(id, System.nanoTime());
        if (ms == null) {
            return;
        }
        broadcastGuardPresence();
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
            event.put("kiosks", onlineKiosksByGroup());
            event.put("pings", kioskPingsByGroup());
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

    private static final class OnlineKiosk {
        private final String label;
        private final KioskGroup group;
        private String pendingPingId;
        private long pingSentAtNanos;
        private Integer pingMs;
        private long lastPongAtNanos;

        private OnlineKiosk(String label, KioskGroup group) {
            this.label = label;
            this.group = group;
        }

        private String label() {
            return label;
        }

        private KioskGroup group() {
            return group;
        }

        private synchronized void beginPing(String id, long sentAtNanos) {
            this.pendingPingId = id;
            this.pingSentAtNanos = sentAtNanos;
        }

        private synchronized Integer completePing(String id, long receivedAtNanos) {
            if (pendingPingId == null || !pendingPingId.equals(id)) {
                return null;
            }
            long rttMs = Math.max(0L, (receivedAtNanos - pingSentAtNanos) / 1_000_000L);
            pendingPingId = null;
            pingMs = rttMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rttMs;
            lastPongAtNanos = receivedAtNanos;
            return pingMs;
        }

        private synchronized boolean expireIfStale(long nowNanos, long timeoutNanos) {
            boolean expired = false;
            if (lastPongAtNanos != 0L && nowNanos - lastPongAtNanos >= timeoutNanos && pingMs != null) {
                pingMs = null;
                expired = true;
            }
            if (pendingPingId != null && nowNanos - pingSentAtNanos >= timeoutNanos) {
                pendingPingId = null;
            }
            return expired;
        }

        private synchronized Integer pingMs() {
            return pingMs;
        }
    }
}
