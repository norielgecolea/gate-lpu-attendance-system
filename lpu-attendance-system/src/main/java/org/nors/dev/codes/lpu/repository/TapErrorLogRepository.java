package org.nors.dev.codes.lpu.repository;

import java.time.Instant;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.KioskGroup;
import org.nors.dev.codes.lpu.model.TapErrorLog;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TapErrorLogRepository {

    private final SessionFactory sessionFactory;

    public TapErrorLogRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public List<TapErrorLog> findAllNewestFirst(int limit, KioskGroup kioskGroup) {
        return currentSession()
                .createQuery(
                        "FROM TapErrorLog t WHERE t.kioskGroup = :kioskGroup ORDER BY t.tappedAt DESC, t.id DESC",
                        TapErrorLog.class
                )
                .setParameter("kioskGroup", kioskGroup)
                .setMaxResults(clampLimit(limit))
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<TapErrorLog> findByRangeNewestFirst(
            Instant startInclusive,
            Instant endExclusive,
            int limit,
            KioskGroup kioskGroup
    ) {
        return currentSession()
                .createQuery(
                        "FROM TapErrorLog t WHERE t.tappedAt >= :start AND t.tappedAt < :end "
                                + "AND t.kioskGroup = :kioskGroup "
                                + "ORDER BY t.tappedAt DESC, t.id DESC",
                        TapErrorLog.class
                )
                .setParameter("start", startInclusive)
                .setParameter("end", endExclusive)
                .setParameter("kioskGroup", kioskGroup)
                .setMaxResults(clampLimit(limit))
                .getResultList();
    }

    @Transactional(readOnly = true)
    public long countAll(KioskGroup kioskGroup) {
        Long count = currentSession()
                .createQuery(
                        "SELECT COUNT(t.id) FROM TapErrorLog t WHERE t.kioskGroup = :kioskGroup",
                        Long.class
                )
                .setParameter("kioskGroup", kioskGroup)
                .uniqueResult();
        return count != null ? count : 0;
    }

    @Transactional(readOnly = true)
    public long countByRange(Instant startInclusive, Instant endExclusive, KioskGroup kioskGroup) {
        Long count = currentSession()
                .createQuery(
                        "SELECT COUNT(t.id) FROM TapErrorLog t "
                                + "WHERE t.tappedAt >= :start AND t.tappedAt < :end "
                                + "AND t.kioskGroup = :kioskGroup",
                        Long.class
                )
                .setParameter("start", startInclusive)
                .setParameter("end", endExclusive)
                .setParameter("kioskGroup", kioskGroup)
                .uniqueResult();
        return count != null ? count : 0;
    }

    @Transactional
    public void persist(TapErrorLog log) {
        Session session = currentSession();
        session.persist(log);
        session.flush();
    }

    @Transactional
    public int deleteAll(KioskGroup kioskGroup) {
        return currentSession()
                .createMutationQuery("DELETE FROM TapErrorLog t WHERE t.kioskGroup = :kioskGroup")
                .setParameter("kioskGroup", kioskGroup)
                .executeUpdate();
    }

    @Transactional
    public int deleteByRange(Instant startInclusive, Instant endExclusive, KioskGroup kioskGroup) {
        return currentSession()
                .createMutationQuery(
                        "DELETE FROM TapErrorLog t WHERE t.tappedAt >= :start AND t.tappedAt < :end "
                                + "AND t.kioskGroup = :kioskGroup"
                )
                .setParameter("start", startInclusive)
                .setParameter("end", endExclusive)
                .setParameter("kioskGroup", kioskGroup)
                .executeUpdate();
    }

    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), 5_000);
    }
}
