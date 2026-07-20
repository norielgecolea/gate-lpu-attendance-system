package org.nors.dev.codes.lpu.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.Employee;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EmployeeRepository {

    private final SessionFactory sessionFactory;

    public EmployeeRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public List<Employee> findAllActive() {
        return currentSession()
                .createQuery(
                        "FROM Employee e WHERE e.deleted = false ORDER BY e.name ASC",
                        Employee.class
                )
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<Employee> findAllInactive() {
        return currentSession()
                .createQuery(
                        "FROM Employee e WHERE e.deleted = true ORDER BY e.name ASC",
                        Employee.class
                )
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<Employee> findById(Long id) {
        return Optional.ofNullable(currentSession().find(Employee.class, id));
    }

    @Transactional(readOnly = true)
    public Optional<Employee> findActiveById(Long id) {
        return currentSession()
                .createQuery(
                        "FROM Employee e WHERE e.id = :id AND e.deleted = false",
                        Employee.class
                )
                .setParameter("id", id)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Optional<Employee> findByEmployeeNo(String employeeNo) {
        return currentSession()
                .createQuery(
                        "FROM Employee e WHERE e.employeeNo = :employeeNo AND e.deleted = false",
                        Employee.class
                )
                .setParameter("employeeNo", employeeNo)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Set<String> findAllEmployeeNumbers() {
        return currentSession()
                .createQuery("SELECT e.employeeNo FROM Employee e", String.class)
                .getResultStream()
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Optional<Employee> findByRfidOrEmployeeNo(String identifier) {
        return currentSession()
                .createQuery(
                        "FROM Employee e WHERE e.deleted = false "
                                + "AND (e.rfid = :identifier OR e.employeeNo = :identifier)",
                        Employee.class
                )
                .setParameter("identifier", identifier)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public boolean existsByEmployeeNoExcludingId(String employeeNo, Long excludeId) {
        Long count = currentSession()
                .createQuery(
                        "SELECT COUNT(e.id) FROM Employee e WHERE e.employeeNo = :employeeNo "
                                + "AND e.deleted = false AND e.id <> :excludeId",
                        Long.class
                )
                .setParameter("employeeNo", employeeNo)
                .setParameter("excludeId", excludeId)
                .uniqueResult();
        return count != null && count > 0;
    }

    @Transactional
    public Employee save(Employee employee) {
        return currentSession().merge(employee);
    }

    @Transactional
    public void persist(Employee employee) {
        Session session = currentSession();
        session.persist(employee);
        session.flush();
    }

    @Transactional
    public void persistBatch(List<Employee> employees) {
        Session session = currentSession();
        for (int i = 0; i < employees.size(); i++) {
            session.persist(employees.get(i));
            if ((i + 1) % 100 == 0) {
                session.flush();
            }
        }
        session.flush();
    }

    @Transactional
    public void delete(Employee employee) {
        currentSession().remove(employee);
    }
}
