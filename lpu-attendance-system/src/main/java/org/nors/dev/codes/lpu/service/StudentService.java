package org.nors.dev.codes.lpu.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.nors.dev.codes.lpu.dto.StudentFinanceTagImportResponse;
import org.nors.dev.codes.lpu.dto.StudentImportResponse;
import org.nors.dev.codes.lpu.dto.StudentPageResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.StudentRequest;
import org.nors.dev.codes.lpu.dto.StudentResponse;
import org.nors.dev.codes.lpu.model.Student;
import org.nors.dev.codes.lpu.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentService {

    private static final Logger log = LogManager.getLogger(StudentService.class);

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> listActive() {
        return studentRepository.findAllActive().stream()
                .map(StudentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentResponse getById(Long id) {
        return StudentResponse.from(requireActive(id));
    }

    @Transactional
    public StudentResponse create(StudentRequest request) {
        String studentNo = normalizeRequired(request.studentNo(), "Student number");
        if (studentRepository.findByStudentNo(studentNo).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student number already exists");
        }

        Student student = new Student();
        applyRequest(student, request, studentNo);
        student.setCreatedAt(Instant.now());
        student.setUpdatedAt(Instant.now());
        student.setDeleted(false);
        studentRepository.persist(student);

        log.info("Created student studentNo={}", student.getStudentNo());
        return StudentResponse.from(student);
    }

    /**
     * Imports new students only. Student numbers already present in the database
     * (active or inactive), or repeated inside the same CSV, are skipped.
     */
    @Transactional
    public StudentImportResponse importStudents(List<StudentRequest> requests) {
        if (requests.size() > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import is limited to 10,000 rows");
        }

        Set<String> knownNumbers = studentRepository.findAllStudentNumbers().stream()
                .map(number -> number.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        List<Student> students = new ArrayList<>();
        int skippedDuplicates = 0;
        Instant now = Instant.now();

        for (StudentRequest request : requests) {
            String studentNo = normalizeRequired(request.studentNo(), "Student number");
            if (!knownNumbers.add(studentNo.toLowerCase(java.util.Locale.ROOT))) {
                skippedDuplicates++;
                continue;
            }

            Student student = new Student();
            applyRequest(student, request, studentNo);
            student.setCreatedAt(now);
            student.setUpdatedAt(now);
            student.setDeleted(false);
            students.add(student);
        }

        if (!students.isEmpty()) {
            studentRepository.persistBatch(students);
        }
        log.info("Imported students imported={} skippedDuplicates={}", students.size(), skippedDuplicates);
        return new StudentImportResponse(students.size(), skippedDuplicates);
    }

    @Transactional
    public StudentResponse update(Long id, StudentRequest request) {
        Student student = requireActive(id);
        String studentNo = normalizeRequired(request.studentNo(), "Student number");

        if (studentRepository.existsByStudentNoExcludingId(studentNo, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student number already exists");
        }

        applyRequest(student, request, studentNo);
        student.setUpdatedAt(Instant.now());
        studentRepository.save(student);

        log.info("Updated student id={} studentNo={}", id, student.getStudentNo());
        return StudentResponse.from(student);
    }

    @Transactional(readOnly = true)
    public StudentPageResponse page(String search, int offset, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        int from = Math.max(offset, 0);
        List<StudentResponse> items = studentRepository.searchActive(search, from, size).stream()
                .map(StudentResponse::from)
                .toList();
        return new StudentPageResponse(items, studentRepository.countActive(search));
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> listInactive() {
        return studentRepository.findAllInactive().stream()
                .map(StudentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> listFinanceTagged() {
        return studentRepository.findActiveFinanceTagged().stream()
                .map(StudentResponse::from)
                .toList();
    }

    /** "Delete" only deactivates — the record stays and can be restored. */
    @Transactional
    public void delete(Long id) {
        Student student = requireActive(id);
        student.setDeleted(true);
        student.setUpdatedAt(Instant.now());
        studentRepository.save(student);
        log.info("Deactivated student id={} studentNo={}", id, student.getStudentNo());
    }

    @Transactional
    public StudentResponse restore(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        if (!student.isDeleted()) {
            return StudentResponse.from(student);
        }
        if (studentRepository.existsByStudentNoExcludingId(student.getStudentNo(), id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An active student already uses student number " + student.getStudentNo()
            );
        }
        student.setDeleted(false);
        student.setUpdatedAt(Instant.now());
        studentRepository.save(student);
        log.info("Restored student id={} studentNo={}", id, student.getStudentNo());
        return StudentResponse.from(student);
    }

    @Transactional
    public StudentResponse setFinanceTagged(Long id, boolean tagged) {
        Student student = requireActive(id);
        student.setFinanceTagged(tagged);
        student.setUpdatedAt(Instant.now());
        studentRepository.save(student);
        log.info("{} finance tag student id={} studentNo={}",
                tagged ? "Applied" : "Removed", id, student.getStudentNo());
        return StudentResponse.from(student);
    }

    /**
     * Tags students with unsettled finance accounts using student numbers.
     * Unknown student numbers are reported to the caller.
     */
    @Transactional
    public StudentFinanceTagImportResponse importFinanceTags(List<String> studentNumbers) {
        if (studentNumbers == null || studentNumbers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one student number is required");
        }
        if (studentNumbers.size() > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import is limited to 10,000 rows");
        }
        Set<String> unique = new HashSet<>();
        int tagged = 0;
        int alreadyTagged = 0;
        int notFound = 0;
        Instant now = Instant.now();

        for (String raw : studentNumbers) {
            String studentNo = normalizeRequired(raw, "Student number");
            String key = studentNo.toLowerCase(java.util.Locale.ROOT);
            if (!unique.add(key)) {
                continue;
            }
            Student student = studentRepository.findByStudentNoAnyStatus(studentNo).orElse(null);
            if (student == null || student.isDeleted()) {
                notFound++;
                continue;
            }
            if (student.isFinanceTagged()) {
                alreadyTagged++;
                continue;
            }
            student.setFinanceTagged(true);
            student.setUpdatedAt(now);
            studentRepository.save(student);
            tagged++;
        }

        log.info("Imported finance tags tagged={} alreadyTagged={} notFound={}", tagged, alreadyTagged, notFound);
        return new StudentFinanceTagImportResponse(tagged, alreadyTagged, notFound);
    }

    private Student requireActive(Long id) {
        return studentRepository.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    private void applyRequest(Student student, StudentRequest request, String studentNo) {
        student.setName(normalizeRequired(request.name(), "Name"));
        student.setStudentNo(studentNo);
        student.setPhoto(normalizeOptional(request.photo()));
        student.setRfid(normalizeOptional(request.rfid()));
        student.setBirthdate(request.birthdate());
        student.setDepartment(normalizeRequired(request.department(), "Department"));
        student.setCourse(normalizeRequired(request.course(), "Course"));
        student.setSchool(normalizeRequired(request.school(), "School"));
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    /** Blank or missing values become null (photo and rfid may be null). */
    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
