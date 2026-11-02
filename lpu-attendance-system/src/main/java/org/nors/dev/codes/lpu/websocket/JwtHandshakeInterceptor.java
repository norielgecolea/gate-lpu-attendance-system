package org.nors.dev.codes.lpu.websocket;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.nors.dev.codes.lpu.model.User;
import org.nors.dev.codes.lpu.security.WsTicketService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final WsTicketService wsTicketService;

    public JwtHandshakeInterceptor(WsTicketService wsTicketService) {
        this.wsTicketService = wsTicketService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String query = request.getURI().getQuery();
        if (query == null) {
            return false;
        }
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && "ticket".equals(kv[0])) {
                String ticket = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                User user = wsTicketService.authenticate(ticket).orElse(null);
                if (user == null) {
                    return false;
                }
                attributes.put("username", user.getUsername());
                attributes.put("role", user.getRole());
                attributes.put("location", user.getLocation() != null ? user.getLocation() : "");
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }
}
