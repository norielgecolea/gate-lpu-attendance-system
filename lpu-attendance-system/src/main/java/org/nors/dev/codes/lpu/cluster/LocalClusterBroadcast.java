package org.nors.dev.codes.lpu.cluster;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalClusterBroadcast implements ClusterBroadcast {

    private final ApplicationEventPublisher publisher;

    public LocalClusterBroadcast(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(String payload) {
        publisher.publishEvent(new WsBroadcastEvent(payload));
    }
}
