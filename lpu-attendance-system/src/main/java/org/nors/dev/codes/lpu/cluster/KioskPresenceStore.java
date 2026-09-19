package org.nors.dev.codes.lpu.cluster;

import java.util.List;

/** Shared kiosk online/ping state. Local map when Redis is off; Redis hash when clustered. */
public interface KioskPresenceStore {

    void put(String sessionId, PresenceRecord record);

    void remove(String sessionId);

    /** Refresh this instance's key TTL so crashed nodes drop out. */
    void touch();

    void clearInstance();

    List<PresenceRecord> all();
}
