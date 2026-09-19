package org.nors.dev.codes.lpu.cluster;

/**
 * Fan-out path for WebSocket payloads. Local mode delivers in-process;
 * Redis mode publishes to every replica (including the publisher).
 */
public interface ClusterBroadcast {

    void publish(String payload);
}
