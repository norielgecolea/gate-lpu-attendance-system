package org.nors.dev.codes.lpu.repository;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.GateTone;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class GateToneRepository {

    private final SessionFactory sessionFactory;

    public GateToneRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public List<GateTone> findAllOrdered() {
        return currentSession()
                .createQuery("FROM GateTone t ORDER BY t.uploadedAt DESC, t.id DESC", GateTone.class)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<GateTone> findById(Long id) {
        return Optional.ofNullable(currentSession().find(GateTone.class, id));
    }

    @Transactional
    public void persist(GateTone tone) {
        Session session = currentSession();
        session.persist(tone);
        session.flush();
    }

    @Transactional
    public void delete(GateTone tone) {
        currentSession().remove(tone);
    }
}
