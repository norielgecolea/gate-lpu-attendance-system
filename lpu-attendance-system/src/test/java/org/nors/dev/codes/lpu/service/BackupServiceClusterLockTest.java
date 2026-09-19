package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.nors.dev.codes.lpu.cluster.ClusterLock;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BackupServiceClusterLockTest {

    @Test
    void startDownload_conflictsWhenClusterLockIsHeld() {
        AtomicBoolean released = new AtomicBoolean(false);
        ClusterLock lock = new ClusterLock() {
            @Override
            public boolean tryAcquire(String key, Duration ttl) {
                assertEquals(BackupService.BACKUP_LOCK_KEY, key);
                return false;
            }

            @Override
            public void release(String key) {
                released.set(true);
            }
        };

        BackupService service = new BackupService(null, null, new ObjectMapper(), lock);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, service::startDownload);
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(false, released.get());
    }
}
