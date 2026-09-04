package org.nors.dev.codes.lpu.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.TapErrorLogResponse;
import org.nors.dev.codes.lpu.model.KioskGroup;
import org.nors.dev.codes.lpu.model.TapErrorLog;
import org.nors.dev.codes.lpu.repository.TapErrorLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TapErrorLogService {

    private static final Logger log = LogManager.getLogger(TapErrorLogService.class);
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Manila");

    private final TapErrorLogRepository tapErrorLogRepository;

    public TapErrorLogService(TapErrorLogRepository tapErrorLogRepository) {
        this.tapErrorLogRepository = tapErrorLogRepository;
    }

    /** Commits in its own transaction so a NOT_FOUND from tap() cannot roll it back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TapErrorLogResponse record(String identifier, String location, KioskGroup kioskGroup) {
        TapErrorLog entry = new TapErrorLog();
        entry.setIdentifier(identifier == null ? "" : identifier.trim());
        entry.setLocation(location);
        entry.setKioskGroup(kioskGroup != null ? kioskGroup : KioskGroup.MAIN_GATES);
        entry.setTappedAt(Instant.now());
        tapErrorLogRepository.persist(entry);
        log.info(
                "Recorded tap error identifier={} location={} kioskGroup={}",
                entry.getIdentifier(),
                entry.getLocation(),
                entry.getKioskGroup()
        );
        return TapErrorLogResponse.from(entry);
    }

    @Transactional(readOnly = true)
    public List<TapErrorLogResponse> list(int limit, LocalDate date, KioskGroup kioskGroup) {
        if (date == null) {
            return tapErrorLogRepository.findAllNewestFirst(limit, kioskGroup).stream()
                    .map(TapErrorLogResponse::from)
                    .toList();
        }
        Instant start = dayStart(date);
        Instant end = dayStart(date.plusDays(1));
        return tapErrorLogRepository.findByRangeNewestFirst(start, end, limit, kioskGroup).stream()
                .map(TapErrorLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count(LocalDate date, KioskGroup kioskGroup) {
        if (date == null) {
            return tapErrorLogRepository.countAll(kioskGroup);
        }
        return tapErrorLogRepository.countByRange(dayStart(date), dayStart(date.plusDays(1)), kioskGroup);
    }

    @Transactional
    public int clearAll(KioskGroup kioskGroup) {
        int deleted = tapErrorLogRepository.deleteAll(kioskGroup);
        log.info("Cleared tap error logs deleted={} kioskGroup={}", deleted, kioskGroup);
        return deleted;
    }

    @Transactional
    public int clearDate(LocalDate date, KioskGroup kioskGroup) {
        int deleted = tapErrorLogRepository.deleteByRange(dayStart(date), dayStart(date.plusDays(1)), kioskGroup);
        log.info("Cleared tap error logs for date={} deleted={} kioskGroup={}", date, deleted, kioskGroup);
        return deleted;
    }

    private static Instant dayStart(LocalDate date) {
        return date.atStartOfDay(CAMPUS_ZONE).toInstant();
    }
}
