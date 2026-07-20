package org.nors.dev.codes.lpu.config;

import org.nors.dev.codes.lpu.websocket.AuthWebSocketHandler;
import org.nors.dev.codes.lpu.websocket.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AuthWebSocketHandler authWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(
            AuthWebSocketHandler authWebSocketHandler,
            JwtHandshakeInterceptor jwtHandshakeInterceptor
    ) {
        this.authWebSocketHandler = authWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(authWebSocketHandler, "/ws/notifications")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
