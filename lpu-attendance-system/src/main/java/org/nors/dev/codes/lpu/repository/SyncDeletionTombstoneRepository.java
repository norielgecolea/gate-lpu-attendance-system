package org.nors.dev.codes.lpu.repository;

import java.time.Instant;
import java.util.List;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.SyncDeletionTombstone;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SyncDeletionTombstoneRepository {

    private final SessionFactory sessionFactory;

    public SyncDeletionTombstoneRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Transactional
    public void persist(SyncDeletionTombstone tombstone) {
        sessionFactory.getCurrentSession().persist(tombstone);
    }

    @Transactional(readOnly = true)
    public List<SyncDeletionTombstone> findDeletedAfter(Instant deletedAt, Long id, int limit) {
        return sessionFactory.getCurrentSession()
                .createQuery(
                        "FROM SyncDeletionTombstone t WHERE t.deletedAt > :deletedAt "
                                + "OR (t.deletedAt = :deletedAt AND t.id > :id) "
                                + "ORDER BY t.deletedAt ASC, t.id ASC",
                        SyncDeletionTombstone.class
                )
                .setParameter("deletedAt", deletedAt)
                .setParameter("id", id)
                .setMaxResults(limit)
                .getResultList();
    }
}
