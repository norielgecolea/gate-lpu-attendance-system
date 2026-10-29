package org.nors.dev.codes.lpu.websocket;

import org.nors.dev.codes.lpu.model.Role;
import org.nors.dev.codes.lpu.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AuthWebSocketHandler extends TextWebSocketHandler {

    private final NotificationService notificationService;

    public AuthWebSocketHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object roleAttr = session.getAttributes().get("role");
        Object usernameAttr = session.getAttributes().get("username");
        if (!(roleAttr instanceof Role role) || !(usernameAttr instanceof String username) || username.isBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }
        Object locationAttr = session.getAttributes().get("location");
        String location = locationAttr instanceof String text && !text.isBlank() ? text : null;
        notificationService.register(session, role, username, location);
        session.sendMessage(new TextMessage(
                "{\"type\":\"AUTH_WS_CONNECTED\",\"message\":\"Connected to live notifications\"}"
        ));
        notificationService.sendGuardPresence(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        notificationService.handleIncomingText(session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        notificationService.unregister(session);
    }
}
