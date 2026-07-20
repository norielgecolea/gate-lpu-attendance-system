package org.nors.dev.codes.lpu.service;

import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.TapErrorLogResponse;
import org.nors.dev.codes.lpu.model.TapErrorLog;
import org.nors.dev.codes.lpu.repository.TapErrorLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TapErrorLogService {

    private static final Logger log = LogManager.getLogger(TapErrorLogService.class);

    private final TapErrorLogRepository tapErrorLogRepository;

    public TapErrorLogService(TapErrorLogRepository tapErrorLogRepository) {
        this.tapErrorLogRepository = tapErrorLogRepository;
    }

    /** Commits in its own transaction so a NOT_FOUND from tap() cannot roll it back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TapErrorLogResponse record(String identifier, String location) {
        TapErrorLog entry = new TapErrorLog();
        entry.setIdentifier(identifier == null ? "" : identifier.trim());
        entry.setLocation(location);
        entry.setTappedAt(Instant.now());
        tapErrorLogRepository.persist(entry);
        log.info("Recorded tap error identifier={} location={}", entry.getIdentifier(), entry.getLocation());
        return TapErrorLogResponse.from(entry);
    }

    @Transactional(readOnly = true)
    public List<TapErrorLogResponse> list(int limit) {
        return tapErrorLogRepository.findAllNewestFirst(limit).stream()
                .map(TapErrorLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count() {
        return tapErrorLogRepository.countAll();
    }

    @Transactional
    public int clearAll() {
        int deleted = tapErrorLogRepository.deleteAll();
        log.info("Cleared tap error logs deleted={}", deleted);
        return deleted;
    }
}
