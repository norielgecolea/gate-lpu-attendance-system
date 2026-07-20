package org.nors.dev.codes.lpu.repository;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.GuardVideo;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class GuardVideoRepository {

    private final SessionFactory sessionFactory;

    public GuardVideoRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public List<GuardVideo> findAllOrdered() {
        return currentSession()
                .createQuery("FROM GuardVideo v ORDER BY v.uploadedAt ASC, v.id ASC", GuardVideo.class)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<GuardVideo> findById(Long id) {
        return Optional.ofNullable(currentSession().find(GuardVideo.class, id));
    }

    @Transactional
    public void persist(GuardVideo video) {
        Session session = currentSession();
        session.persist(video);
        session.flush();
    }

    @Transactional
    public void delete(GuardVideo video) {
        currentSession().remove(video);
    }
}
