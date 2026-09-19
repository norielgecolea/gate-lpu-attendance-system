package org.nors.dev.codes.lpu.cluster;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisClusterLock implements ClusterLock {

    private final StringRedisTemplate redis;
    private final ClusterInstanceId instanceId;

    public RedisClusterLock(StringRedisTemplate redis, ClusterInstanceId instanceId) {
        this.redis = redis;
        this.instanceId = instanceId;
    }

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        Boolean acquired = redis.opsForValue().setIfAbsent(key, instanceId.value(), ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(String key) {
        String holder = redis.opsForValue().get(key);
        if (instanceId.value().equals(holder)) {
            redis.delete(key);
        }
    }
}
