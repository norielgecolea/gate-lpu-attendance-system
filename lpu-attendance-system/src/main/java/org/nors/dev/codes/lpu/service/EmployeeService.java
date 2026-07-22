package org.nors.dev.codes.lpu.service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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
import org.nors.dev.codes.lpu.dto.PhotoBulkUploadResponse;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployeeService {

    private static final Logger log = LogManager.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;
    private final PhotoStorageService photoStorageService;
    private final RfidUniquenessService rfidUniquenessService;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            PhotoStorageService photoStorageService,
            RfidUniquenessService rfidUniquenessService
    ) {
        this.employeeRepository = employeeRepository;
        this.photoStorageService = photoStorageService;
        this.rfidUniquenessService = rfidUniquenessService;
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
        rfidUniquenessService.assertAvailable(
                normalizeOptional(request.rfid()),
                RfidUniquenessService.OwnerType.EMPLOYEE,
                null
        );

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
     * or repeated inside the same CSV, are skipped. Rows whose RFID is already assigned
     * to any active student/employee (or repeated in the CSV) are also skipped.
     * RFID, department, position, and birthdate may be blank.
     */
    @Transactional
    public EmployeeImportResponse importEmployees(List<EmployeeRequest> requests) {
        if (requests.size() > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import is limited to 10,000 rows");
        }

        Set<String> knownNumbers = employeeRepository.findAllEmployeeNumbers().stream()
                .map(number -> number.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Set<String> knownRfids = rfidUniquenessService.findAllActiveRfids();
        List<Employee> employees = new ArrayList<>();
        int skippedDuplicates = 0;
        Instant now = Instant.now();

        for (EmployeeRequest request : requests) {
            String employeeNo = normalizeRequired(request.employeeNo(), "Employee number");
            String numberKey = employeeNo.toLowerCase(java.util.Locale.ROOT);
            if (knownNumbers.contains(numberKey)) {
                skippedDuplicates++;
                continue;
            }

            String rfid = normalizeOptional(request.rfid());
            if (rfid != null && knownRfids.contains(rfid)) {
                skippedDuplicates++;
                continue;
            }

            knownNumbers.add(numberKey);
            if (rfid != null) {
                knownRfids.add(rfid);
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
        rfidUniquenessService.assertAvailable(
                normalizeOptional(request.rfid()),
                RfidUniquenessService.OwnerType.EMPLOYEE,
                id
        );

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

    /**
     * Applies photos whose filenames (without extension) match active employee numbers.
     */
    @Transactional
    public PhotoBulkUploadResponse bulkUploadPhotos(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one photo file is required");
        }
        if (files.size() > 5_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bulk photo upload is limited to 5,000 files");
        }

        int updated = 0;
        int notFound = 0;
        int skippedInvalid = 0;
        Instant now = Instant.now();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                skippedInvalid++;
                continue;
            }
            if (!photoStorageService.isAllowedImage(file)) {
                skippedInvalid++;
                continue;
            }
            String employeeNo = PhotoStorageService.personNumberFromFilename(file.getOriginalFilename());
            if (employeeNo == null) {
                skippedInvalid++;
                continue;
            }
            Employee employee = employeeRepository.findByEmployeeNo(employeeNo).orElse(null);
            if (employee == null) {
                notFound++;
                continue;
            }
            String photoPath = photoStorageService.store(file);
            employee.setPhoto(photoPath);
            employee.setUpdatedAt(now);
            employeeRepository.save(employee);
            updated++;
        }

        log.info("Bulk employee photos updated={} notFound={} skippedInvalid={}", updated, notFound, skippedInvalid);
        return new PhotoBulkUploadResponse(updated, notFound, skippedInvalid);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv() {
        List<Employee> employees = employeeRepository.findAllActive();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.println("Name,ID Number,RFID,Department,Position,Birthday");
            for (Employee employee : employees) {
                writer.printf(
                        "%s,%s,%s,%s,%s,%s%n",
                        csv(employee.getName()),
                        csv(employee.getEmployeeNo()),
                        csv(employee.getRfid()),
                        csv(employee.getDepartment()),
                        csv(employee.getPosition()),
                        employee.getBirthdate() == null ? "" : employee.getBirthdate().toString()
                );
            }
        }
        return baos.toByteArray();
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

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
