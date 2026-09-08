package org.nors.dev.codes.lpu.repository;

import java.time.Instant;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
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
        Query<TapErrorLog> query = currentSession().createQuery(
                kioskGroup == null
                        ? "FROM TapErrorLog t ORDER BY t.tappedAt DESC, t.id DESC"
                        : "FROM TapErrorLog t WHERE t.kioskGroup = :kioskGroup ORDER BY t.tappedAt DESC, t.id DESC",
                TapErrorLog.class
        );
        bindGroup(query, kioskGroup);
        return query.setMaxResults(clampLimit(limit)).getResultList();
    }

    @Transactional(readOnly = true)
    public List<TapErrorLog> findByRangeNewestFirst(
            Instant startInclusive,
            Instant endExclusive,
            int limit,
            KioskGroup kioskGroup
    ) {
        Query<TapErrorLog> query = currentSession().createQuery(
                kioskGroup == null
                        ? "FROM TapErrorLog t WHERE t.tappedAt >= :start AND t.tappedAt < :end "
                                + "ORDER BY t.tappedAt DESC, t.id DESC"
                        : "FROM TapErrorLog t WHERE t.tappedAt >= :start AND t.tappedAt < :end "
                                + "AND t.kioskGroup = :kioskGroup "
                                + "ORDER BY t.tappedAt DESC, t.id DESC",
                TapErrorLog.class
        );
        query.setParameter("start", startInclusive).setParameter("end", endExclusive);
        bindGroup(query, kioskGroup);
        return query.setMaxResults(clampLimit(limit)).getResultList();
    }

    @Transactional(readOnly = true)
    public long countAll(KioskGroup kioskGroup) {
        Query<Long> query = currentSession().createQuery(
                kioskGroup == null
                        ? "SELECT COUNT(t.id) FROM TapErrorLog t"
                        : "SELECT COUNT(t.id) FROM TapErrorLog t WHERE t.kioskGroup = :kioskGroup",
                Long.class
        );
        bindGroup(query, kioskGroup);
        Long count = query.uniqueResult();
        return count != null ? count : 0;
    }

    @Transactional(readOnly = true)
    public long countByRange(Instant startInclusive, Instant endExclusive, KioskGroup kioskGroup) {
        Query<Long> query = currentSession().createQuery(
                kioskGroup == null
                        ? "SELECT COUNT(t.id) FROM TapErrorLog t WHERE t.tappedAt >= :start AND t.tappedAt < :end"
                        : "SELECT COUNT(t.id) FROM TapErrorLog t WHERE t.tappedAt >= :start AND t.tappedAt < :end "
                                + "AND t.kioskGroup = :kioskGroup",
                Long.class
        );
        query.setParameter("start", startInclusive).setParameter("end", endExclusive);
        bindGroup(query, kioskGroup);
        Long count = query.uniqueResult();
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
        var query = kioskGroup == null
                ? currentSession().createMutationQuery("DELETE FROM TapErrorLog t")
                : currentSession()
                        .createMutationQuery("DELETE FROM TapErrorLog t WHERE t.kioskGroup = :kioskGroup")
                        .setParameter("kioskGroup", kioskGroup);
        return query.executeUpdate();
    }

    @Transactional
    public int deleteByRange(Instant startInclusive, Instant endExclusive, KioskGroup kioskGroup) {
        var query = kioskGroup == null
                ? currentSession().createMutationQuery(
                        "DELETE FROM TapErrorLog t WHERE t.tappedAt >= :start AND t.tappedAt < :end"
                )
                : currentSession().createMutationQuery(
                        "DELETE FROM TapErrorLog t WHERE t.tappedAt >= :start AND t.tappedAt < :end "
                                + "AND t.kioskGroup = :kioskGroup"
                );
        query.setParameter("start", startInclusive).setParameter("end", endExclusive);
        if (kioskGroup != null) {
            query.setParameter("kioskGroup", kioskGroup);
        }
        return query.executeUpdate();
    }

    private static void bindGroup(Query<?> query, KioskGroup kioskGroup) {
        if (kioskGroup != null) {
            query.setParameter("kioskGroup", kioskGroup);
        }
    }

    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), 5_000);
    }
}
