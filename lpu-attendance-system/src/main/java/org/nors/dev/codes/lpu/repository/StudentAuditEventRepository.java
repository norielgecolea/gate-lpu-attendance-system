package org.nors.dev.codes.lpu.repository;

import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.StudentAuditEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StudentAuditEventRepository {

    private final SessionFactory sessionFactory;

    public StudentAuditEventRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional
    public void persist(StudentAuditEvent event) {
        Session session = currentSession();
        session.persist(event);
        session.flush();
    }

    @Transactional(readOnly = true)
    public List<StudentAuditEvent> findByStudentId(Long studentId) {
        return currentSession()
                .createQuery(
                        "FROM StudentAuditEvent e WHERE e.student.id = :studentId ORDER BY e.createdAt DESC, e.id DESC",
                        StudentAuditEvent.class
                )
                .setParameter("studentId", studentId)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<StudentAuditEvent> findLatestCreatedByStudentId(Long studentId) {
        return currentSession()
                .createQuery(
                        "FROM StudentAuditEvent e WHERE e.student.id = :studentId AND e.action = 'CREATED'"
                                + " ORDER BY e.createdAt DESC, e.id DESC",
                        StudentAuditEvent.class
                )
                .setParameter("studentId", studentId)
                .setMaxResults(1)
                .uniqueResultOptional();
    }
}
