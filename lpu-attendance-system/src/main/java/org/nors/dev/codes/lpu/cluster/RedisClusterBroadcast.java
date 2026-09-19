package org.nors.dev.codes.lpu.cluster;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisClusterBroadcast implements ClusterBroadcast {

    public static final String CHANNEL = "lpu:ws:broadcast";

    private final StringRedisTemplate redis;

    public RedisClusterBroadcast(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void publish(String payload) {
        redis.convertAndSend(CHANNEL, payload);
    }
}
