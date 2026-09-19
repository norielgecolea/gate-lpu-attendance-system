package org.nors.dev.codes.lpu.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.nors.dev.codes.lpu.model.KioskGroup;

class InMemoryKioskPresenceStoreTest {

    @Test
    void all_returnsPutRecordsAndSurvivesRemove() {
        InMemoryKioskPresenceStore store = new InMemoryKioskPresenceStore();
        PresenceRecord gate = new PresenceRecord("s1", "Gate 1", KioskGroup.MAIN_GATES, 12, 1_000L);
        PresenceRecord library = new PresenceRecord("s2", "Library", KioskGroup.LIBRARY, null, 0L);
        store.put("s1", gate);
        store.put("s2", library);

        List<PresenceRecord> all = store.all();
        assertEquals(2, all.size());
        assertTrue(all.contains(gate));
        assertTrue(all.contains(library));

        store.remove("s1");
        assertEquals(List.of(library), store.all());

        store.clearInstance();
        assertTrue(store.all().isEmpty());
    }
}
