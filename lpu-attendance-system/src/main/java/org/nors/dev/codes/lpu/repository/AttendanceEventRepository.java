package org.nors.dev.codes.lpu.repository;

import java.time.LocalDate;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.nors.dev.codes.lpu.model.AttendanceEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AttendanceEventRepository {

    private final SessionFactory sessionFactory;

    public AttendanceEventRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional
    public void persist(AttendanceEvent event) {
        Session session = currentSession();
        session.persist(event);
        session.flush();
    }

    /**
     * Tap counts per campus-local hour and action for one date. Rows: [hour, action, count].
     */
    @Transactional(readOnly = true)
    public List<Object[]> countByHour(LocalDate date) {
        return currentSession()
                .createNativeQuery(
                        "SELECT CAST(EXTRACT(HOUR FROM tapped_at AT TIME ZONE 'Asia/Manila') AS int) AS h,"
                                + " action, COUNT(*)"
                                + " FROM attendance_events WHERE attendance_date = :date"
                                + " GROUP BY 1, 2 ORDER BY 1",
                        Object[].class
                )
                .setParameter("date", date)
                .getResultList();
    }

    @Transactional
    public int deleteByPerson(String personType, Long personId) {
        String personField = "EMPLOYEE".equalsIgnoreCase(personType) ? "employee.id" : "student.id";
        return currentSession()
                .createMutationQuery("DELETE FROM AttendanceEvent e WHERE e." + personField + " = :personId")
                .setParameter("personId", personId)
                .executeUpdate();
    }

    @Transactional(readOnly = true)
    public List<AttendanceEvent> search(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String location,
            String action,
            String sortDir,
            int offset,
            int limit
    ) {
        StringBuilder hql = new StringBuilder(
                "SELECT e FROM AttendanceEvent e LEFT JOIN FETCH e.student s LEFT JOIN FETCH e.employee emp WHERE 1=1"
        );
        appendFilters(hql, personType, personId, startDate, endDate, search, location, action);
        boolean asc = "asc".equalsIgnoreCase(sortDir);
        hql.append(asc ? " ORDER BY e.tappedAt ASC, e.id ASC" : " ORDER BY e.tappedAt DESC, e.id DESC");

        Query<AttendanceEvent> query = currentSession().createQuery(hql.toString(), AttendanceEvent.class);
        bindFilters(query, personType, personId, startDate, endDate, search, location, action);
        return query.setFirstResult(Math.max(offset, 0)).setMaxResults(Math.max(limit, 1)).getResultList();
    }

    @Transactional(readOnly = true)
    public long count(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String location,
            String action
    ) {
        StringBuilder hql = new StringBuilder(
                "SELECT COUNT(e.id) FROM AttendanceEvent e LEFT JOIN e.student s LEFT JOIN e.employee emp WHERE 1=1"
        );
        appendFilters(hql, personType, personId, startDate, endDate, search, location, action);
        Query<Long> query = currentSession().createQuery(hql.toString(), Long.class);
        bindFilters(query, personType, personId, startDate, endDate, search, location, action);
        Long count = query.uniqueResult();
        return count != null ? count : 0;
    }

    private static void appendFilters(
            StringBuilder hql,
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String location,
            String action
    ) {
        if ("STUDENT".equalsIgnoreCase(personType)) {
            hql.append(" AND e.student IS NOT NULL");
        } else if ("EMPLOYEE".equalsIgnoreCase(personType)) {
            hql.append(" AND e.employee IS NOT NULL");
        }
        if (personId != null) {
            if ("EMPLOYEE".equalsIgnoreCase(personType)) {
                hql.append(" AND e.employee.id = :personId");
            } else {
                hql.append(" AND e.student.id = :personId");
            }
        }
        if (startDate != null) {
            hql.append(" AND e.attendanceDate >= :startDate");
        }
        if (endDate != null) {
            hql.append(" AND e.attendanceDate <= :endDate");
        }
        if (location != null && !location.isBlank()) {
            hql.append(" AND lower(coalesce(e.location, '')) LIKE :location");
        }
        if (action != null && !action.isBlank()) {
            hql.append(" AND e.action = :action");
        }
        if (search != null && !search.isBlank()) {
            hql.append(
                    " AND (:search = '' OR lower(coalesce(s.name, emp.name, '')) LIKE :search"
                            + " OR lower(coalesce(s.studentNo, emp.employeeNo, '')) LIKE :search"
                            + " OR lower(coalesce(s.department, emp.department, '')) LIKE :search"
                            + " OR lower(coalesce(s.course, '')) LIKE :search"
                            + " OR lower(coalesce(emp.position, '')) LIKE :search)"
            );
        }
    }

    private static void bindFilters(
            Query<?> query,
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String location,
            String action
    ) {
        if (personId != null) {
            query.setParameter("personId", personId);
        }
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }
        if (location != null && !location.isBlank()) {
            query.setParameter("location", "%" + location.trim().toLowerCase() + "%");
        }
        if (action != null && !action.isBlank()) {
            query.setParameter("action", action.trim().toUpperCase());
        }
        if (search != null && !search.isBlank()) {
            query.setParameter("search", "%" + search.trim().toLowerCase() + "%");
        }
    }
}
