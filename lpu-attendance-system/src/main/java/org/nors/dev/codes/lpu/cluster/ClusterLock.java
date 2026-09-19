package org.nors.dev.codes.lpu.cluster;

import java.time.Duration;

/** Cross-instance mutex. Local mode always succeeds; Redis uses SET NX. */
public interface ClusterLock {

    boolean tryAcquire(String key, Duration ttl);

    void release(String key);
}
