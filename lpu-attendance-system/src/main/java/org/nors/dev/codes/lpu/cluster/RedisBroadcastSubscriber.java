package org.nors.dev.codes.lpu.cluster;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisBroadcastSubscriber implements MessageListener {

    private final ApplicationEventPublisher publisher;

    public RedisBroadcastSubscriber(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        publisher.publishEvent(new WsBroadcastEvent(payload));
    }
}
