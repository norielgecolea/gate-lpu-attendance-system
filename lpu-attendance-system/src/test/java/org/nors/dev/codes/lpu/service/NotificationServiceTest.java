package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nors.dev.codes.lpu.cluster.ClusterBroadcast;
import org.nors.dev.codes.lpu.cluster.InMemoryKioskPresenceStore;
import org.nors.dev.codes.lpu.cluster.PresenceRecord;
import org.nors.dev.codes.lpu.cluster.WsBroadcastEvent;
import org.nors.dev.codes.lpu.model.KioskGroup;

class NotificationServiceTest {

    private final List<String> published = new ArrayList<>();
    private InMemoryKioskPresenceStore store;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        published.clear();
        ClusterBroadcast bus = published::add;
        store = new InMemoryKioskPresenceStore();
        service = new NotificationService(new ObjectMapper(), bus, store);
    }

    @Test
    void onlineLocations_readSharedPresenceStore() {
        store.put("a", new PresenceRecord("a", "Gate 1", KioskGroup.MAIN_GATES, 12, System.currentTimeMillis()));
        store.put("b", new PresenceRecord("b", "Gate 1", KioskGroup.MAIN_GATES, 40, System.currentTimeMillis()));
        store.put("c", new PresenceRecord("c", "Library desk", KioskGroup.LIBRARY, null, 0L));

        assertEquals(List.of("Gate 1"), service.onlineGuardLocations());
        assertEquals(List.of("Library desk"), service.onlineKioskLocations(KioskGroup.LIBRARY));
        assertEquals(40, service.kioskPingsByGroup().get("MAIN_GATES").get("Gate 1"));
    }

    @Test
    void broadcastRaw_publishesOnce_subscriberDoesNotRepublish() {
        service.broadcastRaw("hello");
        assertEquals(List.of("hello"), published);

        service.onClusterBroadcast(new WsBroadcastEvent("hello"));
        assertEquals(List.of("hello"), published);
    }

    @Test
    void visiblePingMs_omitsStaleAndMissingPongs() {
        long now = 100_000L;
        PresenceRecord fresh = new PresenceRecord("s1", "Gate 1", KioskGroup.MAIN_GATES, 9, now - 1_000L);
        PresenceRecord stale = new PresenceRecord("s2", "Gate 2", KioskGroup.MAIN_GATES, 9, now - 9_000L);
        PresenceRecord waiting = new PresenceRecord("s3", "Gate 3", KioskGroup.MAIN_GATES, null, now);

        assertEquals(9, NotificationService.visiblePingMs(fresh, now));
        assertNull(NotificationService.visiblePingMs(stale, now));
        assertNull(NotificationService.visiblePingMs(waiting, now));
    }
}
