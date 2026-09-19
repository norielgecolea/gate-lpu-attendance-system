package org.nors.dev.codes.lpu.cluster;

import java.net.InetAddress;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stable id for this JVM (container hostname in Compose). */
@Component
public class ClusterInstanceId {

    private final String value;

    public ClusterInstanceId() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception ex) {
                host = UUID.randomUUID().toString();
            }
        }
        this.value = host;
    }

    public String value() {
        return value;
    }
}
