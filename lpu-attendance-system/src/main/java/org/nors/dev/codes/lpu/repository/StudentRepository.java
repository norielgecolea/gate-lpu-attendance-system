package org.nors.dev.codes.lpu.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.Student;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StudentRepository {

    private final SessionFactory sessionFactory;

    public StudentRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    private static final String ACTIVE_SEARCH_WHERE =
            " WHERE s.deleted = false AND (:term = '' OR lower(s.name) LIKE :term"
                    + " OR lower(s.studentNo) LIKE :term OR lower(coalesce(s.rfid, '')) LIKE :term"
                    + " OR lower(s.department) LIKE :term OR lower(s.course) LIKE :term"
                    + " OR lower(s.school) LIKE :term)";

    @Transactional(readOnly = true)
    public List<Student> findAllActive() {
        return currentSession()
                .createQuery(
                        "FROM Student s WHERE s.deleted = false ORDER BY s.name ASC",
                        Student.class
                )
                .getResultList();
    }

    /** Paged + searched on the database side so the UI never loads the full table. */
    @Transactional(readOnly = true)
    public List<Student> searchActive(String term, int offset, int limit) {
        return currentSession()
                .createQuery("FROM Student s" + ACTIVE_SEARCH_WHERE + " ORDER BY s.name ASC", Student.class)
                .setParameter("term", likeTerm(term))
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public long countActive(String term) {
        Long count = currentSession()
                .createQuery("SELECT COUNT(s.id) FROM Student s" + ACTIVE_SEARCH_WHERE, Long.class)
                .setParameter("term", likeTerm(term))
                .uniqueResult();
        return count != null ? count : 0;
    }

    private static String likeTerm(String term) {
        if (term == null || term.isBlank()) {
            return "";
        }
        return "%" + term.trim().toLowerCase() + "%";
    }

    @Transactional(readOnly = true)
    public List<Student> findAllInactive() {
        return currentSession()
                .createQuery(
                        "FROM Student s WHERE s.deleted = true ORDER BY s.name ASC",
                        Student.class
                )
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<Student> findById(Long id) {
        return Optional.ofNullable(currentSession().find(Student.class, id));
    }

    @Transactional(readOnly = true)
    public Optional<Student> findActiveById(Long id) {
        return currentSession()
                .createQuery(
                        "FROM Student s WHERE s.id = :id AND s.deleted = false",
                        Student.class
                )
                .setParameter("id", id)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Optional<Student> findByStudentNo(String studentNo) {
        return currentSession()
                .createQuery(
                        "FROM Student s WHERE s.studentNo = :studentNo AND s.deleted = false",
                        Student.class
                )
                .setParameter("studentNo", studentNo)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Set<String> findAllStudentNumbers() {
        return currentSession()
                .createQuery("SELECT s.studentNo FROM Student s", String.class)
                .getResultStream()
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Optional<Student> findByRfid(String rfid) {
        return currentSession()
                .createQuery(
                        "FROM Student s WHERE s.rfid = :rfid AND s.deleted = false",
                        Student.class
                )
                .setParameter("rfid", rfid)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Optional<Student> findByRfidOrStudentNo(String identifier) {
        return currentSession()
                .createQuery(
                        "FROM Student s WHERE s.deleted = false "
                                + "AND (s.rfid = :identifier OR s.studentNo = :identifier)",
                        Student.class
                )
                .setParameter("identifier", identifier)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Optional<Student> findByStudentNoAnyStatus(String studentNo) {
        return currentSession()
                .createQuery("FROM Student s WHERE s.studentNo = :studentNo", Student.class)
                .setParameter("studentNo", studentNo)
                .uniqueResultOptional();
    }

    /** Lowercased student number → entity (active and inactive), for CSV upsert. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Student> findAllByStudentNoKey() {
        return currentSession()
                .createQuery("FROM Student s", Student.class)
                .getResultStream()
                .collect(Collectors.toMap(
                        s -> s.getStudentNo().toLowerCase(java.util.Locale.ROOT),
                        s -> s,
                        (a, b) -> a
                ));
    }

    @Transactional(readOnly = true)
    public List<Student> findActiveFinanceTagged() {
        return currentSession()
                .createQuery(
                        "FROM Student s WHERE s.deleted = false AND s.financeTagged = true ORDER BY s.name ASC",
                        Student.class
                )
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Set<String> findAllActiveRfids() {
        return currentSession()
                .createQuery(
                        "SELECT s.rfid FROM Student s WHERE s.deleted = false AND s.rfid IS NOT NULL",
                        String.class
                )
                .getResultStream()
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean existsByStudentNoExcludingId(String studentNo, Long excludeId) {
        Long count = currentSession()
                .createQuery(
                        "SELECT COUNT(s.id) FROM Student s WHERE s.studentNo = :studentNo "
                                + "AND s.deleted = false AND s.id <> :excludeId",
                        Long.class
                )
                .setParameter("studentNo", studentNo)
                .setParameter("excludeId", excludeId)
                .uniqueResult();
        return count != null && count > 0;
    }

    @Transactional
    public Student save(Student student) {
        return currentSession().merge(student);
    }

    @Transactional
    public void persist(Student student) {
        Session session = currentSession();
        session.persist(student);
        session.flush();
    }

    @Transactional
    public void persistBatch(List<Student> students) {
        Session session = currentSession();
        for (int i = 0; i < students.size(); i++) {
            session.persist(students.get(i));
            if ((i + 1) % 100 == 0) {
                session.flush();
            }
        }
        session.flush();
    }

    @Transactional
    public void delete(Student student) {
        currentSession().remove(student);
    }
}
