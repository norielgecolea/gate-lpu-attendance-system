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
import org.nors.dev.codes.lpu.dto.PhotoBulkUploadResponse;
import org.nors.dev.codes.lpu.dto.StudentAuditEventResponse;
import org.nors.dev.codes.lpu.dto.StudentFinanceTagImportResponse;
import org.nors.dev.codes.lpu.dto.StudentImportResponse;
import org.nors.dev.codes.lpu.dto.StudentImportRequest;
import org.nors.dev.codes.lpu.dto.StudentPageResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.StudentRequest;
import org.nors.dev.codes.lpu.dto.StudentResponse;
import org.nors.dev.codes.lpu.model.StudentAuditEvent;
import org.nors.dev.codes.lpu.model.Student;
import org.nors.dev.codes.lpu.repository.StudentAuditEventRepository;
import org.nors.dev.codes.lpu.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentService {

    private static final Logger log = LogManager.getLogger(StudentService.class);

    private final StudentRepository studentRepository;
    private final StudentAuditEventRepository studentAuditEventRepository;
    private final PhotoStorageService photoStorageService;
    private final RfidUniquenessService rfidUniquenessService;

    public StudentService(
            StudentRepository studentRepository,
            StudentAuditEventRepository studentAuditEventRepository,
            PhotoStorageService photoStorageService,
            RfidUniquenessService rfidUniquenessService
    ) {
        this.studentRepository = studentRepository;
        this.studentAuditEventRepository = studentAuditEventRepository;
        this.photoStorageService = photoStorageService;
        this.rfidUniquenessService = rfidUniquenessService;
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
    public StudentResponse create(StudentRequest request, Long actorUserId, String actorUsername) {
        String studentNo = normalizeRequired(request.studentNo(), "Student number");
        if (studentRepository.findByStudentNo(studentNo).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student number already exists");
        }
        rfidUniquenessService.assertAvailable(
                normalizeOptional(request.rfid()),
                RfidUniquenessService.OwnerType.STUDENT,
                null
        );

        Student student = new Student();
        applyRequest(student, request, studentNo);
        student.setCreatedAt(Instant.now());
        student.setUpdatedAt(Instant.now());
        student.setDeleted(false);
        studentRepository.persist(student);
        persistAuditEvent(student, "CREATED", actorUserId, actorUsername);

        log.info("Created student studentNo={}", student.getStudentNo());
        return StudentResponse.from(student);
    }

    @Transactional(readOnly = true)
    public List<StudentAuditEventResponse> listAuditEvents(Long studentId) {
        requireActive(studentId);
        return studentAuditEventRepository.findByStudentId(studentId).stream()
                .map(StudentAuditEventResponse::from)
                .toList();
    }

    /**
     * Upserts students from CSV. Matching student numbers update the existing
     * record; new numbers are inserted. Matching records can contain only a student
     * number plus the fields to change. Incomplete new rows are skipped. Rows whose
     * RFID is already assigned to a different person (or repeated for a different
     * number in the CSV) are also skipped.
     */
    @Transactional
    public StudentImportResponse importStudents(
            List<StudentImportRequest> requests,
            Long actorUserId,
            String actorUsername
    ) {
        if (requests.size() > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import is limited to 10,000 rows");
        }

        java.util.Map<String, Student> existingByNo = studentRepository.findAllByStudentNoKey();
        Set<String> knownRfids = rfidUniquenessService.findAllActiveRfids();
        List<Student> toInsert = new ArrayList<>();
        int imported = 0;
        int updated = 0;
        int skippedDuplicates = 0;
        int skippedIncomplete = 0;
        Instant now = Instant.now();

        for (StudentImportRequest request : requests) {
            String studentNo = normalizeRequired(request.studentNo(), "Student number");
            String numberKey = studentNo.toLowerCase(java.util.Locale.ROOT);
            String rfid = normalizeOptional(request.rfid());
            Student existing = existingByNo.get(numberKey);

            if (existing != null) {
                if (rfidConflicts(rfid, existing.getRfid(), knownRfids)) {
                    skippedDuplicates++;
                    continue;
                }
                String previousRfid = existing.getRfid();
                String previousPhoto = existing.getPhoto();
                applyImportUpdate(existing, request, studentNo);
                photoStorageService.deleteIfReplaced(previousPhoto, existing.getPhoto());
                existing.setUpdatedAt(now);
                if (existing.getId() != null) {
                    studentRepository.save(existing);
                    updated++;
                    persistAuditEvent(existing, "UPDATED", actorUserId, actorUsername);
                }
                releaseAndClaimRfid(knownRfids, previousRfid, existing.getRfid());
                continue;
            }

            if (!hasRequiredNewStudentFields(request)) {
                skippedIncomplete++;
                continue;
            }

            if (rfidConflicts(rfid, null, knownRfids)) {
                skippedDuplicates++;
                continue;
            }

            Student student = new Student();
            applyRequest(student, request.asCreateRequest(), studentNo);
            student.setCreatedAt(now);
            student.setUpdatedAt(now);
            student.setDeleted(false);
            toInsert.add(student);
            existingByNo.put(numberKey, student);
            if (rfid != null) {
                knownRfids.add(rfid);
            }
            imported++;
        }

        if (!toInsert.isEmpty()) {
            studentRepository.persistBatch(toInsert);
            for (Student student : toInsert) {
                persistAuditEvent(student, "CREATED", actorUserId, actorUsername);
            }
        }
        log.info(
                "Imported students imported={} updated={} skippedDuplicates={} skippedIncomplete={}",
                imported,
                updated,
                skippedDuplicates,
                skippedIncomplete
        );
        return new StudentImportResponse(imported, updated, skippedDuplicates, skippedIncomplete);
    }

    @Transactional
    public StudentResponse update(Long id, StudentRequest request, Long actorUserId, String actorUsername) {
        Student student = requireActive(id);
        String studentNo = normalizeRequired(request.studentNo(), "Student number");

        if (studentRepository.existsByStudentNoExcludingId(studentNo, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student number already exists");
        }
        rfidUniquenessService.assertAvailable(
                normalizeOptional(request.rfid()),
                RfidUniquenessService.OwnerType.STUDENT,
                id
        );

        String previousPhoto = student.getPhoto();
        applyRequest(student, request, studentNo);
        photoStorageService.deleteIfReplaced(previousPhoto, student.getPhoto());
        student.setUpdatedAt(Instant.now());
        studentRepository.save(student);
        persistAuditEvent(student, "UPDATED", actorUserId, actorUsername);

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
    public void delete(Long id, Long actorUserId, String actorUsername) {
        Student student = requireActive(id);
        student.setDeleted(true);
        student.setUpdatedAt(Instant.now());
        studentRepository.save(student);
        persistAuditEvent(student, "DELETED", actorUserId, actorUsername);
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

    /**
     * Applies photos whose filenames (without extension) match active student numbers.
     */
    @Transactional
    public PhotoBulkUploadResponse bulkUploadPhotos(
            List<MultipartFile> files,
            Long actorUserId,
            String actorUsername
    ) {
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
            String studentNo = PhotoStorageService.personNumberFromFilename(file.getOriginalFilename());
            if (studentNo == null) {
                skippedInvalid++;
                continue;
            }
            Student student = studentRepository.findByStudentNo(studentNo).orElse(null);
            if (student == null) {
                notFound++;
                continue;
            }
            String previousPhoto = student.getPhoto();
            String photoPath = photoStorageService.store(file);
            student.setPhoto(photoPath);
            photoStorageService.deleteIfReplaced(previousPhoto, photoPath);
            student.setUpdatedAt(now);
            studentRepository.save(student);
            persistAuditEvent(student, "PHOTO_UPDATED", actorUserId, actorUsername);
            updated++;
        }

        log.info("Bulk student photos updated={} notFound={} skippedInvalid={}", updated, notFound, skippedInvalid);
        return new PhotoBulkUploadResponse(updated, notFound, skippedInvalid);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv() {
        List<Student> students = studentRepository.findAllActive();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.println("Name,ID Number,RFID,Department,Course,School,Birthday,LPU Email");
            for (Student student : students) {
                writer.printf(
                        "%s,%s,%s,%s,%s,%s,%s,%s%n",
                        csv(student.getName()),
                        csv(student.getStudentNo()),
                        csv(student.getRfid()),
                        csv(student.getDepartment()),
                        csv(student.getCourse()),
                        csv(student.getSchool()),
                        student.getBirthdate() == null ? "" : student.getBirthdate().toString(),
                        csv(student.getLpuEmail())
                );
            }
        }
        return baos.toByteArray();
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
        student.setLpuEmail(normalizeOptional(request.lpuEmail()));
        student.setDepartment(normalizeRequired(request.department(), "Department"));
        student.setCourse(normalizeRequired(request.course(), "Course"));
        student.setSchool(normalizeRequired(request.school(), "School"));
    }

    /** CSV upsert: omitted or blank fields keep the matching record's values. */
    private void applyImportUpdate(Student student, StudentImportRequest request, String studentNo) {
        student.setStudentNo(studentNo);
        setWhenPresent(request.name(), student::setName);
        setWhenPresent(request.photo(), student::setPhoto);
        setWhenPresent(request.rfid(), student::setRfid);
        if (request.birthdate() != null) {
            student.setBirthdate(request.birthdate());
        }
        setWhenPresent(request.lpuEmail(), student::setLpuEmail);
        setWhenPresent(request.department(), student::setDepartment);
        setWhenPresent(request.course(), student::setCourse);
        setWhenPresent(request.school(), student::setSchool);
    }

    private static void setWhenPresent(String value, java.util.function.Consumer<String> setter) {
        String normalized = normalizeOptional(value);
        if (normalized != null) {
            setter.accept(normalized);
        }
    }

    private static boolean hasRequiredNewStudentFields(StudentImportRequest request) {
        return normalizeOptional(request.name()) != null
                && normalizeOptional(request.department()) != null
                && normalizeOptional(request.course()) != null
                && normalizeOptional(request.school()) != null;
    }

    private static boolean rfidConflicts(String rfid, String currentRfid, Set<String> knownRfids) {
        return rfid != null && !rfid.equals(currentRfid) && knownRfids.contains(rfid);
    }

    private static void releaseAndClaimRfid(Set<String> knownRfids, String previous, String next) {
        if (previous != null && !previous.equals(next)) {
            knownRfids.remove(previous);
        }
        if (next != null) {
            knownRfids.add(next);
        }
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

    private void persistAuditEvent(Student student, String action, Long actorUserId, String actorUsername) {
        StudentAuditEvent event = new StudentAuditEvent();
        event.setStudent(student);
        event.setAction(action);
        event.setActorUserId(actorUserId);
        event.setActorUsername(normalizeOptional(actorUsername));
        event.setCreatedAt(Instant.now());
        studentAuditEventRepository.persist(event);
    }
}
