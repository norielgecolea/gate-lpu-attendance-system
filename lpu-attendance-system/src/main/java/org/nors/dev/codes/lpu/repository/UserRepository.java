package org.nors.dev.codes.lpu.repository;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.User;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserRepository {

    private final SessionFactory sessionFactory;

    public UserRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return currentSession()
                .createQuery("FROM User u WHERE u.username = :username", User.class)
                .setParameter("username", username)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return currentSession()
                .createQuery("FROM User u WHERE u.id = :id", User.class)
                .setParameter("id", id)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return currentSession()
                .createQuery("FROM User u ORDER BY u.username ASC", User.class)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public boolean existsByUsernameExcludingId(String username, Long excludeId) {
        Long count = currentSession()
                .createQuery(
                        "SELECT COUNT(u.id) FROM User u WHERE lower(u.username) = lower(:username)"
                                + " AND (:excludeId IS NULL OR u.id <> :excludeId)",
                        Long.class
                )
                .setParameter("username", username)
                .setParameter("excludeId", excludeId)
                .uniqueResult();
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public long countActiveSuperadminsExcluding(Long excludeId) {
        Long count = currentSession()
                .createQuery(
                        "SELECT COUNT(u.id) FROM User u WHERE u.role = org.nors.dev.codes.lpu.model.Role.SUPERADMIN"
                                + " AND u.active = true AND u.id <> :excludeId",
                        Long.class
                )
                .setParameter("excludeId", excludeId)
                .uniqueResult();
        return count != null ? count : 0;
    }

    @Transactional
    public void persist(User user) {
        Session session = currentSession();
        session.persist(user);
        session.flush();
    }

    @Transactional
    public User save(User user) {
        return currentSession().merge(user);
    }
}
