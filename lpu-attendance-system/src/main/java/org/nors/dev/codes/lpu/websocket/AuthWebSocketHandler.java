package org.nors.dev.codes.lpu.websocket;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.nors.dev.codes.lpu.model.Role;
import org.nors.dev.codes.lpu.service.JwtService;
import org.nors.dev.codes.lpu.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AuthWebSocketHandler extends TextWebSocketHandler {

    private final NotificationService notificationService;
    private final JwtService jwtService;

    public AuthWebSocketHandler(NotificationService notificationService, JwtService jwtService) {
        this.notificationService = notificationService;
        this.jwtService = jwtService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session);
        if (token == null || !jwtService.isTokenValid(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }
        Role role = jwtService.extractRole(token);
        if (role != Role.SUPERADMIN
                && role != Role.GUARD
                && role != Role.MONITORING
                && role != Role.OSAS
                && role != Role.HR) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }
        String username = jwtService.extractUsername(token);
        String location = jwtService.extractLocation(token);
        Object locAttr = session.getAttributes().get("location");
        if (location == null && locAttr instanceof String text && !text.isBlank()) {
            location = text;
        }
        notificationService.register(session, role, username, location);
        session.sendMessage(new TextMessage(
                "{\"type\":\"AUTH_WS_CONNECTED\",\"message\":\"Connected to live notifications\"}"
        ));
        // Snapshot so dashboard/monitor show online gates immediately on connect.
        notificationService.sendGuardPresence(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        notificationService.unregister(session);
    }

    private String extractToken(WebSocketSession session) {
        Object tokenAttr = session.getAttributes().get("token");
        if (tokenAttr instanceof String token && !token.isBlank()) {
            return token;
        }
        if (session.getUri() == null || session.getUri().getQuery() == null) {
            return null;
        }
        for (String part : session.getUri().getQuery().split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
