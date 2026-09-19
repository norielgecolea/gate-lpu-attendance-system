package org.nors.dev.codes.lpu.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryKioskPresenceStore implements KioskPresenceStore {

    private final Map<String, PresenceRecord> records = new ConcurrentHashMap<>();

    @Override
    public void put(String sessionId, PresenceRecord record) {
        records.put(sessionId, record);
    }

    @Override
    public void remove(String sessionId) {
        records.remove(sessionId);
    }

    @Override
    public void touch() {
        // No TTL in local mode.
    }

    @Override
    public void clearInstance() {
        records.clear();
    }

    @Override
    public List<PresenceRecord> all() {
        return new ArrayList<>(records.values());
    }
}
