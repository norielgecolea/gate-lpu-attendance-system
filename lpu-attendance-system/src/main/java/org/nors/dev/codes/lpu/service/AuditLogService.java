package org.nors.dev.codes.lpu.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.dto.AuditLogPageResponse;
import org.nors.dev.codes.lpu.dto.AuditLogResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuditLogService {

    private final SessionFactory sessionFactory;

    public AuditLogService(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse page(String personType, LocalDate date, int offset, int limit) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }
        String type = personType == null ? "" : personType.trim().toUpperCase();
        if (!type.isEmpty() && !"STUDENT".equals(type) && !"EMPLOYEE".equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personType must be STUDENT or EMPLOYEE");
        }

        int size = Math.min(Math.max(limit, 1), 500);
        int from = Math.max(offset, 0);
        Session session = sessionFactory.getCurrentSession();

        String fromClause;
        if ("STUDENT".equals(type)) {
            fromClause = studentSelectSql();
        } else if ("EMPLOYEE".equals(type)) {
            fromClause = employeeSelectSql();
        } else {
            fromClause = studentSelectSql() + " UNION ALL " + employeeSelectSql();
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = session.createNativeQuery(
                        "SELECT * FROM (" + fromClause + ") logs ORDER BY created_at DESC, id DESC OFFSET :offset LIMIT :limit"
                )
                .setParameter("date", date)
                .setParameter("offset", from)
                .setParameter("limit", size)
                .getResultList();

        Object totalRaw = session.createNativeQuery(
                        "SELECT COUNT(*) FROM (" + fromClause + ") logs"
                )
                .setParameter("date", date)
                .getSingleResult();
        long total = totalRaw instanceof Number number ? number.longValue() : 0L;

        List<AuditLogResponse> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            items.add(mapRow(row));
        }
        return new AuditLogPageResponse(items, total);
    }

    private static String studentSelectSql() {
        return """
                SELECT
                    CAST(e.id AS text) AS id,
                    'STUDENT' AS person_type,
                    CAST(s.id AS text) AS person_id,
                    s.name AS person_name,
                    s.student_no AS person_no,
                    e.action AS action,
                    e.actor_user_id AS actor_user_id,
                    e.actor_username AS actor_username,
                    e.created_at AS created_at
                FROM student_audit_events e
                JOIN students s ON s.id = e.student_id
                WHERE CAST(e.created_at AT TIME ZONE 'Asia/Manila' AS date) = :date
                """;
    }

    private static String employeeSelectSql() {
        return """
                SELECT
                    CAST(e.id AS text) AS id,
                    'EMPLOYEE' AS person_type,
                    CAST(emp.id AS text) AS person_id,
                    emp.name AS person_name,
                    emp.employee_no AS person_no,
                    e.action AS action,
                    e.actor_user_id AS actor_user_id,
                    e.actor_username AS actor_username,
                    e.created_at AS created_at
                FROM employee_audit_events e
                JOIN employees emp ON emp.id = e.employee_id
                WHERE CAST(e.created_at AT TIME ZONE 'Asia/Manila' AS date) = :date
                """;
    }

    private static AuditLogResponse mapRow(Object[] row) {
        return new AuditLogResponse(
                String.valueOf(row[0]),
                String.valueOf(row[1]),
                String.valueOf(row[2]),
                row[3] != null ? String.valueOf(row[3]) : "",
                row[4] != null ? String.valueOf(row[4]) : "",
                String.valueOf(row[5]),
                row[6] == null ? null : ((Number) row[6]).longValue(),
                row[7] != null ? String.valueOf(row[7]) : null,
                toInstant(row[8])
        );
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        throw new IllegalStateException("Unsupported timestamp type: " + (value == null ? "null" : value.getClass()));
    }
}
