package org.nors.dev.codes.lpu.websocket;

import java.util.Map;
import org.nors.dev.codes.lpu.service.JwtService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
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
            if (kv.length == 2 && "token".equals(kv[0])) {
                String token = kv[1];
                if (jwtService.isTokenValid(token)) {
                    attributes.put("token", token);
                    attributes.put("username", jwtService.extractUsername(token));
                    return true;
                }
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
