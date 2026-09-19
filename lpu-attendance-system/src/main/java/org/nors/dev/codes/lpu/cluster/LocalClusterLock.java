package org.nors.dev.codes.lpu.cluster;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalClusterLock implements ClusterLock {

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        return true;
    }

    @Override
    public void release(String key) {
        // Same-JVM mutual exclusion lives on BackupService's AtomicBoolean.
    }
}
