package org.nors.dev.codes.lpu.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.EmployeeImportResponse;
import org.nors.dev.codes.lpu.dto.EmployeeRequest;
import org.nors.dev.codes.lpu.dto.EmployeeResponse;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployeeService {

    private static final Logger log = LogManager.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> listActive() {
        return employeeRepository.findAllActive().stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> listInactive() {
        return employeeRepository.findAllInactive().stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        return EmployeeResponse.from(requireActive(id));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        String employeeNo = normalizeRequired(request.employeeNo(), "Employee number");
        if (employeeRepository.findByEmployeeNo(employeeNo).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee number already exists");
        }

        Employee employee = new Employee();
        applyRequest(employee, request, employeeNo);
        employee.setCreatedAt(Instant.now());
        employee.setUpdatedAt(Instant.now());
        employee.setDeleted(false);
        employeeRepository.persist(employee);

        log.info("Created employee employeeNo={}", employee.getEmployeeNo());
        return EmployeeResponse.from(employee);
    }

    /**
     * Imports new employees only. Employee numbers already present (active or inactive),
     * or repeated inside the same CSV, are skipped. RFID, department, position, and
     * birthdate may be blank.
     */
    @Transactional
    public EmployeeImportResponse importEmployees(List<EmployeeRequest> requests) {
        if (requests.size() > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import is limited to 10,000 rows");
        }

        Set<String> knownNumbers = employeeRepository.findAllEmployeeNumbers().stream()
                .map(number -> number.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        List<Employee> employees = new ArrayList<>();
        int skippedDuplicates = 0;
        Instant now = Instant.now();

        for (EmployeeRequest request : requests) {
            String employeeNo = normalizeRequired(request.employeeNo(), "Employee number");
            if (!knownNumbers.add(employeeNo.toLowerCase(java.util.Locale.ROOT))) {
                skippedDuplicates++;
                continue;
            }

            Employee employee = new Employee();
            applyRequest(employee, request, employeeNo);
            employee.setCreatedAt(now);
            employee.setUpdatedAt(now);
            employee.setDeleted(false);
            employees.add(employee);
        }

        if (!employees.isEmpty()) {
            employeeRepository.persistBatch(employees);
        }
        log.info("Imported employees imported={} skippedDuplicates={}", employees.size(), skippedDuplicates);
        return new EmployeeImportResponse(employees.size(), skippedDuplicates);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = requireActive(id);
        String employeeNo = normalizeRequired(request.employeeNo(), "Employee number");

        if (employeeRepository.existsByEmployeeNoExcludingId(employeeNo, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee number already exists");
        }

        applyRequest(employee, request, employeeNo);
        employee.setUpdatedAt(Instant.now());
        employeeRepository.save(employee);

        log.info("Updated employee id={} employeeNo={}", id, employee.getEmployeeNo());
        return EmployeeResponse.from(employee);
    }

    /** "Delete" only deactivates — the record stays and can be restored. */
    @Transactional
    public void delete(Long id) {
        Employee employee = requireActive(id);
        employee.setDeleted(true);
        employee.setUpdatedAt(Instant.now());
        employeeRepository.save(employee);
        log.info("Deactivated employee id={} employeeNo={}", id, employee.getEmployeeNo());
    }

    @Transactional
    public EmployeeResponse restore(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (!employee.isDeleted()) {
            return EmployeeResponse.from(employee);
        }
        if (employeeRepository.existsByEmployeeNoExcludingId(employee.getEmployeeNo(), id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An active employee already uses employee number " + employee.getEmployeeNo()
            );
        }
        employee.setDeleted(false);
        employee.setUpdatedAt(Instant.now());
        employeeRepository.save(employee);
        log.info("Restored employee id={} employeeNo={}", id, employee.getEmployeeNo());
        return EmployeeResponse.from(employee);
    }

    private Employee requireActive(Long id) {
        return employeeRepository.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private void applyRequest(Employee employee, EmployeeRequest request, String employeeNo) {
        employee.setName(normalizeRequired(request.name(), "Name"));
        employee.setEmployeeNo(employeeNo);
        employee.setPhoto(normalizeOptional(request.photo()));
        employee.setRfid(normalizeOptional(request.rfid()));
        employee.setBirthdate(request.birthdate());
        employee.setDepartment(normalizeOptional(request.department()));
        employee.setPosition(normalizeOptional(request.position()));
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
