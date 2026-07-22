package org.nors.dev.codes.lpu.service;

import java.util.HashSet;
import java.util.Set;
import org.nors.dev.codes.lpu.repository.EmployeeRepository;
import org.nors.dev.codes.lpu.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ensures an RFID can belong to at most one active student or employee.
 */
@Service
public class RfidUniquenessService {

    public enum OwnerType {
        STUDENT,
        EMPLOYEE
    }

    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    public RfidUniquenessService(
            StudentRepository studentRepository,
            EmployeeRepository employeeRepository
    ) {
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Rejects when the RFID is already assigned to a different active person.
     * {@code excludeId} is the current record's id when updating; pass null on create.
     */
    public void assertAvailable(String rfid, OwnerType ownerType, Long excludeId) {
        String conflict = findConflictMessage(rfid, ownerType, excludeId);
        if (conflict != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict);
        }
    }

    /**
     * @return conflict message when the RFID is taken, otherwise null
     */
    public String findConflictMessage(String rfid, OwnerType ownerType, Long excludeId) {
        if (rfid == null || rfid.isBlank()) {
            return null;
        }
        String normalized = rfid.trim();

        var student = studentRepository.findByRfid(normalized);
        if (student.isPresent()) {
            boolean sameOwner =
                    ownerType == OwnerType.STUDENT
                            && excludeId != null
                            && student.get().getId().equals(excludeId);
            if (!sameOwner) {
                return "RFID already assigned to student " + student.get().getName();
            }
        }

        var employee = employeeRepository.findByRfid(normalized);
        if (employee.isPresent()) {
            boolean sameOwner =
                    ownerType == OwnerType.EMPLOYEE
                            && excludeId != null
                            && employee.get().getId().equals(excludeId);
            if (!sameOwner) {
                return "RFID already assigned to employee " + employee.get().getName();
            }
        }

        return null;
    }

    /** Active RFIDs already assigned to any student or employee (for bulk import checks). */
    public Set<String> findAllActiveRfids() {
        Set<String> rfids = new HashSet<>(studentRepository.findAllActiveRfids());
        rfids.addAll(employeeRepository.findAllActiveRfids());
        return rfids;
    }
}
