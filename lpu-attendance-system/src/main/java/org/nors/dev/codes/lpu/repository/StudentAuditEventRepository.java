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

    @Transactional(readOnly = true)
    public List<StudentAuditEvent> findByManilaDate(java.time.LocalDate date, int offset, int limit) {
        return currentSession()
                .createNativeQuery(
                        """
                        SELECT e.*
                        FROM student_audit_events e
                        WHERE CAST(e.created_at AT TIME ZONE 'Asia/Manila' AS date) = :date
                        ORDER BY e.created_at DESC, e.id DESC
                        OFFSET :offset LIMIT :limit
                        """,
                        StudentAuditEvent.class
                )
                .setParameter("date", date)
                .setParameter("offset", Math.max(offset, 0))
                .setParameter("limit", Math.max(limit, 1))
                .getResultList();
    }

    @Transactional(readOnly = true)
    public long countByManilaDate(java.time.LocalDate date) {
        Object count = currentSession()
                .createNativeQuery(
                        """
                        SELECT COUNT(*)
                        FROM student_audit_events e
                        WHERE CAST(e.created_at AT TIME ZONE 'Asia/Manila' AS date) = :date
                        """
                )
                .setParameter("date", date)
                .getSingleResult();
        return count instanceof Number number ? number.longValue() : 0L;
    }
}
