package org.nors.dev.codes.lpu.cluster;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisKioskPresenceStore implements KioskPresenceStore {

    private static final Logger log = LogManager.getLogger(RedisKioskPresenceStore.class);
    static final String KEY_PREFIX = "kiosk:presence:";
    private static final Duration TTL = Duration.ofSeconds(15);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ClusterInstanceId instanceId;

    public RedisKioskPresenceStore(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            ClusterInstanceId instanceId
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
    }

    @Override
    public void put(String sessionId, PresenceRecord record) {
        try {
            redis.opsForHash().put(instanceKey(), sessionId, objectMapper.writeValueAsString(record));
            redis.expire(instanceKey(), TTL);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize kiosk presence for session {}", sessionId, ex);
        }
    }

    @Override
    public void remove(String sessionId) {
        redis.opsForHash().delete(instanceKey(), sessionId);
        redis.expire(instanceKey(), TTL);
    }

    @Override
    public void touch() {
        redis.expire(instanceKey(), TTL);
    }

    @Override
    public void clearInstance() {
        redis.delete(instanceKey());
    }

    @Override
    public List<PresenceRecord> all() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<PresenceRecord> records = new ArrayList<>();
        for (String key : keys) {
            Map<Object, Object> entries = redis.opsForHash().entries(key);
            for (Object value : entries.values()) {
                if (value == null) {
                    continue;
                }
                try {
                    records.add(objectMapper.readValue(value.toString(), PresenceRecord.class));
                } catch (Exception ex) {
                    log.debug("Ignoring malformed kiosk presence payload", ex);
                }
            }
        }
        return records;
    }

    private String instanceKey() {
        return KEY_PREFIX + instanceId.value();
    }
}
