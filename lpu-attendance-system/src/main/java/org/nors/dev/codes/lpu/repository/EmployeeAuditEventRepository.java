package org.nors.dev.codes.lpu.repository;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.EmployeeAuditEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EmployeeAuditEventRepository {

    private final SessionFactory sessionFactory;

    public EmployeeAuditEventRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional
    public void persist(EmployeeAuditEvent event) {
        Session session = currentSession();
        session.persist(event);
        session.flush();
    }

    @Transactional(readOnly = true)
    public List<EmployeeAuditEvent> findByEmployeeId(Long employeeId) {
        return currentSession()
                .createQuery(
                        "FROM EmployeeAuditEvent e WHERE e.employee.id = :employeeId ORDER BY e.createdAt DESC, e.id DESC",
                        EmployeeAuditEvent.class
                )
                .setParameter("employeeId", employeeId)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeAuditEvent> findLatestCreatedByEmployeeId(Long employeeId) {
        return currentSession()
                .createQuery(
                        "FROM EmployeeAuditEvent e WHERE e.employee.id = :employeeId AND e.action = 'CREATED'"
                                + " ORDER BY e.createdAt DESC, e.id DESC",
                        EmployeeAuditEvent.class
                )
                .setParameter("employeeId", employeeId)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public List<EmployeeAuditEvent> findByManilaDate(java.time.LocalDate date, int offset, int limit) {
        return currentSession()
                .createNativeQuery(
                        """
                        SELECT e.*
                        FROM employee_audit_events e
                        WHERE CAST(e.created_at AT TIME ZONE 'Asia/Manila' AS date) = :date
                        ORDER BY e.created_at DESC, e.id DESC
                        OFFSET :offset LIMIT :limit
                        """,
                        EmployeeAuditEvent.class
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
                        FROM employee_audit_events e
                        WHERE CAST(e.created_at AT TIME ZONE 'Asia/Manila' AS date) = :date
                        """
                )
                .setParameter("date", date)
                .getSingleResult();
        return count instanceof Number number ? number.longValue() : 0L;
    }
}
