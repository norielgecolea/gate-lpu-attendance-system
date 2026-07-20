package org.nors.dev.codes.lpu.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.nors.dev.codes.lpu.model.AttendanceLog;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AttendanceLogRepository {

    private final SessionFactory sessionFactory;

    public AttendanceLogRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceLog> findByStudentAndDate(Long studentId, LocalDate date) {
        return currentSession()
                .createQuery(
                        "FROM AttendanceLog a WHERE a.student.id = :studentId AND a.attendanceDate = :date",
                        AttendanceLog.class
                )
                .setParameter("studentId", studentId)
                .setParameter("date", date)
                .uniqueResultOptional();
    }

    @Transactional
    public Optional<AttendanceLog> findByStudentAndDateForUpdate(Long studentId, LocalDate date) {
        return currentSession()
                .createQuery(
                        "FROM AttendanceLog a WHERE a.student.id = :studentId AND a.attendanceDate = :date",
                        AttendanceLog.class
                )
                .setParameter("studentId", studentId)
                .setParameter("date", date)
                .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceLog> findByEmployeeAndDate(Long employeeId, LocalDate date) {
        return currentSession()
                .createQuery(
                        "FROM AttendanceLog a WHERE a.employee.id = :employeeId AND a.attendanceDate = :date",
                        AttendanceLog.class
                )
                .setParameter("employeeId", employeeId)
                .setParameter("date", date)
                .uniqueResultOptional();
    }

    @Transactional
    public Optional<AttendanceLog> findByEmployeeAndDateForUpdate(Long employeeId, LocalDate date) {
        return currentSession()
                .createQuery(
                        "FROM AttendanceLog a WHERE a.employee.id = :employeeId AND a.attendanceDate = :date",
                        AttendanceLog.class
                )
                .setParameter("employeeId", employeeId)
                .setParameter("date", date)
                .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public List<AttendanceLog> findRecentByDate(LocalDate date, int offset, int limit) {
        return currentSession()
                .createQuery(
                        "FROM AttendanceLog a WHERE a.attendanceDate = :date ORDER BY a.updatedAt DESC",
                        AttendanceLog.class
                )
                .setParameter("date", date)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceLog> searchDaily(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String department,
            String location,
            String status,
            String sortBy,
            String sortDir,
            int offset,
            int limit
    ) {
        StringBuilder hql = new StringBuilder(
                "SELECT a FROM AttendanceLog a LEFT JOIN FETCH a.student s LEFT JOIN FETCH a.employee e WHERE 1=1"
        );
        appendDailyFilters(hql, personType, personId, startDate, endDate, search, department, location, status);
        hql.append(orderByClause(sortBy, sortDir));

        Query<AttendanceLog> query = currentSession().createQuery(hql.toString(), AttendanceLog.class);
        bindDailyFilters(query, personType, personId, startDate, endDate, search, department, location, status);
        return query.setFirstResult(Math.max(offset, 0)).setMaxResults(Math.max(limit, 1)).getResultList();
    }

    @Transactional(readOnly = true)
    public long countDaily(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String department,
            String location,
            String status
    ) {
        StringBuilder hql = new StringBuilder(
                "SELECT COUNT(a.id) FROM AttendanceLog a LEFT JOIN a.student s LEFT JOIN a.employee e WHERE 1=1"
        );
        appendDailyFilters(hql, personType, personId, startDate, endDate, search, department, location, status);
        Query<Long> query = currentSession().createQuery(hql.toString(), Long.class);
        bindDailyFilters(query, personType, personId, startDate, endDate, search, department, location, status);
        Long count = query.uniqueResult();
        return count != null ? count : 0;
    }

    @Transactional(readOnly = true)
    public Object[] summarizeDaily(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String department,
            String location,
            String status
    ) {
        StringBuilder hql = new StringBuilder(
                "SELECT"
                        + " COUNT(DISTINCT COALESCE(s.id, e.id)),"
                        + " SUM(CASE WHEN a.timeOut IS NOT NULL THEN 1 ELSE 0 END),"
                        + " SUM(CASE WHEN a.timeOut IS NULL THEN 1 ELSE 0 END),"
                        + " COALESCE(SUM(a.tapCount), 0),"
                        // Re-entries keep the earlier timeOut, so "inside now" must use lastAction.
                        + " SUM(CASE WHEN a.lastAction = 'TIME_IN' THEN 1 ELSE 0 END)"
                        + " FROM AttendanceLog a LEFT JOIN a.student s LEFT JOIN a.employee e WHERE 1=1"
        );
        appendDailyFilters(hql, personType, personId, startDate, endDate, search, department, location, status);
        Query<Object[]> query = currentSession().createQuery(hql.toString(), Object[].class);
        bindDailyFilters(query, personType, personId, startDate, endDate, search, department, location, status);
        Object[] row = query.uniqueResult();
        return row != null ? row : new Object[]{0L, 0L, 0L, 0L, 0L};
    }

    /**
     * Unique people present, grouped by department, ordered by count descending.
     */
    @Transactional(readOnly = true)
    public List<Object[]> countByDepartment(String personType, LocalDate startDate, LocalDate endDate) {
        StringBuilder hql = new StringBuilder(
                "SELECT coalesce(nullif(trim(coalesce(s.department, e.department, '')), ''), 'Unassigned'),"
                        + " COUNT(DISTINCT COALESCE(s.id, e.id))"
                        + " FROM AttendanceLog a LEFT JOIN a.student s LEFT JOIN a.employee e WHERE 1=1"
        );
        appendDailyFilters(hql, personType, null, startDate, endDate, null, null, null, null);
        hql.append(
                " GROUP BY coalesce(nullif(trim(coalesce(s.department, e.department, '')), ''), 'Unassigned')"
                        + " ORDER BY COUNT(DISTINCT COALESCE(s.id, e.id)) DESC"
        );
        Query<Object[]> query = currentSession().createQuery(hql.toString(), Object[].class);
        bindDailyFilters(query, personType, null, startDate, endDate, null, null, null, null);
        return query.list();
    }

    @Transactional(readOnly = true)
    public Object[] summarizePerson(String personType, Long personId, LocalDate startDate, LocalDate endDate) {
        String personClause = "EMPLOYEE".equalsIgnoreCase(personType)
                ? " a.employee.id = :personId"
                : " a.student.id = :personId";
        StringBuilder hql = new StringBuilder(
                "SELECT"
                        + " COUNT(a.id),"
                        + " SUM(CASE WHEN a.timeOut IS NOT NULL THEN 1 ELSE 0 END),"
                        + " SUM(CASE WHEN a.timeOut IS NULL THEN 1 ELSE 0 END),"
                        + " COALESCE(SUM(a.tapCount), 0),"
                        + " MIN(a.attendanceDate),"
                        + " MAX(a.attendanceDate)"
                        + " FROM AttendanceLog a WHERE"
                        + personClause
        );
        if (startDate != null) {
            hql.append(" AND a.attendanceDate >= :startDate");
        }
        if (endDate != null) {
            hql.append(" AND a.attendanceDate <= :endDate");
        }
        Query<Object[]> query = currentSession().createQuery(hql.toString(), Object[].class);
        query.setParameter("personId", personId);
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }
        Object[] row = query.uniqueResult();
        return row != null ? row : new Object[]{0L, 0L, 0L, 0L, null, null};
    }

    @Transactional(readOnly = true)
    public List<String> distinctLocations(String personType, LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT loc FROM ("
                        + " SELECT time_in_location AS loc FROM attendance_logs WHERE time_in_location IS NOT NULL"
        );
        if ("STUDENT".equalsIgnoreCase(personType)) {
            sql.append(" AND student_id IS NOT NULL");
        } else if ("EMPLOYEE".equalsIgnoreCase(personType)) {
            sql.append(" AND employee_id IS NOT NULL");
        }
        if (startDate != null) {
            sql.append(" AND attendance_date >= :startDate");
        }
        if (endDate != null) {
            sql.append(" AND attendance_date <= :endDate");
        }
        sql.append(" UNION SELECT time_out_location AS loc FROM attendance_logs WHERE time_out_location IS NOT NULL");
        if ("STUDENT".equalsIgnoreCase(personType)) {
            sql.append(" AND student_id IS NOT NULL");
        } else if ("EMPLOYEE".equalsIgnoreCase(personType)) {
            sql.append(" AND employee_id IS NOT NULL");
        }
        if (startDate != null) {
            sql.append(" AND attendance_date >= :startDate");
        }
        if (endDate != null) {
            sql.append(" AND attendance_date <= :endDate");
        }
        sql.append(") t WHERE loc IS NOT NULL AND btrim(loc) <> '' ORDER BY loc ASC");

        var query = currentSession().createNativeQuery(sql.toString(), String.class);
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }
        return query.getResultList();
    }

    @Transactional
    public void persist(AttendanceLog log) {
        Session session = currentSession();
        session.persist(log);
        session.flush();
    }

    @Transactional
    public AttendanceLog save(AttendanceLog log) {
        return currentSession().merge(log);
    }

    @Transactional
    public int deleteByPerson(String personType, Long personId) {
        String personField = "EMPLOYEE".equalsIgnoreCase(personType) ? "employee.id" : "student.id";
        return currentSession()
                .createMutationQuery("DELETE FROM AttendanceLog a WHERE a." + personField + " = :personId")
                .setParameter("personId", personId)
                .executeUpdate();
    }

    private static void appendDailyFilters(
            StringBuilder hql,
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String department,
            String location,
            String status
    ) {
        if ("STUDENT".equalsIgnoreCase(personType)) {
            hql.append(" AND a.student IS NOT NULL");
        } else if ("EMPLOYEE".equalsIgnoreCase(personType)) {
            hql.append(" AND a.employee IS NOT NULL");
        }
        if (personId != null) {
            if ("EMPLOYEE".equalsIgnoreCase(personType)) {
                hql.append(" AND a.employee.id = :personId");
            } else {
                hql.append(" AND a.student.id = :personId");
            }
        }
        if (startDate != null) {
            hql.append(" AND a.attendanceDate >= :startDate");
        }
        if (endDate != null) {
            hql.append(" AND a.attendanceDate <= :endDate");
        }
        if (department != null && !department.isBlank()) {
            hql.append(" AND lower(coalesce(s.department, e.department, '')) = :department");
        }
        if (location != null && !location.isBlank()) {
            hql.append(
                    " AND (lower(coalesce(a.timeInLocation, '')) LIKE :location"
                            + " OR lower(coalesce(a.timeOutLocation, '')) LIKE :location)"
            );
        }
        if ("COMPLETE".equalsIgnoreCase(status)) {
            hql.append(" AND a.timeOut IS NOT NULL");
        } else if ("OPEN".equalsIgnoreCase(status)) {
            hql.append(" AND a.timeOut IS NULL");
        }
        if (search != null && !search.isBlank()) {
            hql.append(
                    " AND (:search = '' OR lower(coalesce(s.name, e.name, '')) LIKE :search"
                            + " OR lower(coalesce(s.studentNo, e.employeeNo, '')) LIKE :search"
                            + " OR lower(coalesce(s.department, e.department, '')) LIKE :search"
                            + " OR lower(coalesce(s.course, '')) LIKE :search"
                            + " OR lower(coalesce(s.school, '')) LIKE :search"
                            + " OR lower(coalesce(e.position, '')) LIKE :search"
                            + " OR lower(coalesce(s.rfid, e.rfid, '')) LIKE :search)"
            );
        }
    }

    private static void bindDailyFilters(
            Query<?> query,
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String department,
            String location,
            String status
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
        if (department != null && !department.isBlank()) {
            query.setParameter("department", department.trim().toLowerCase());
        }
        if (location != null && !location.isBlank()) {
            query.setParameter("location", "%" + location.trim().toLowerCase() + "%");
        }
        if (search != null && !search.isBlank()) {
            query.setParameter("search", "%" + search.trim().toLowerCase() + "%");
        }
    }

    private static String orderByClause(String sortBy, String sortDir) {
        boolean asc = "asc".equalsIgnoreCase(sortDir);
        String direction = asc ? "ASC" : "DESC";
        return switch (sortBy == null ? "" : sortBy) {
            case "name" -> " ORDER BY coalesce(s.name, e.name) " + direction + ", a.id DESC";
            case "timeIn" -> " ORDER BY a.timeIn " + direction + ", a.id DESC";
            case "timeOut" -> " ORDER BY a.timeOut " + direction + " NULLS LAST, a.id DESC";
            case "tapCount" -> " ORDER BY a.tapCount " + direction + ", a.id DESC";
            default -> " ORDER BY a.attendanceDate " + direction + ", a.updatedAt DESC, a.id DESC";
        };
    }
}
