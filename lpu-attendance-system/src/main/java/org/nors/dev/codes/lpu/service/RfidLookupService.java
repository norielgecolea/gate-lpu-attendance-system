package org.nors.dev.codes.lpu.service;

import org.nors.dev.codes.lpu.dto.EmployeeAuditEventResponse;
import org.nors.dev.codes.lpu.dto.EmployeeResponse;
import org.nors.dev.codes.lpu.dto.RfidLookupResponse;
import org.nors.dev.codes.lpu.dto.StudentAuditEventResponse;
import org.nors.dev.codes.lpu.dto.StudentResponse;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.model.Role;
import org.nors.dev.codes.lpu.model.Student;
import org.nors.dev.codes.lpu.repository.EmployeeAuditEventRepository;
import org.nors.dev.codes.lpu.repository.EmployeeRepository;
import org.nors.dev.codes.lpu.repository.StudentAuditEventRepository;
import org.nors.dev.codes.lpu.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Role-scoped RFID / ID number lookup for the admin RFID Checker page.
 * OSAS sees students only; HR sees employees only; Superadmin sees either.
 */
@Service
public class RfidLookupService {

    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final StudentAuditEventRepository studentAuditEventRepository;
    private final EmployeeAuditEventRepository employeeAuditEventRepository;

    public RfidLookupService(
            StudentRepository studentRepository,
            EmployeeRepository employeeRepository,
            StudentAuditEventRepository studentAuditEventRepository,
            EmployeeAuditEventRepository employeeAuditEventRepository
    ) {
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
        this.studentAuditEventRepository = studentAuditEventRepository;
        this.employeeAuditEventRepository = employeeAuditEventRepository;
    }

    @Transactional(readOnly = true)
    public RfidLookupResponse lookup(String rawIdentifier, Role role) {
        String identifier = rawIdentifier == null ? "" : rawIdentifier.trim();
        if (identifier.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifier is required");
        }
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        return switch (role) {
            case OSAS -> lookupStudentOnly(identifier);
            case HR -> lookupEmployeeOnly(identifier);
            case SUPERADMIN -> lookupAny(identifier);
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        };
    }

    private RfidLookupResponse lookupStudentOnly(String identifier) {
        return studentRepository.findByRfidOrStudentNo(identifier)
                .map(this::studentLookup)
                .orElseGet(() -> RfidLookupResponse.notFound(
                        "No student record found for \"" + identifier + "\"."
                ));
    }

    private RfidLookupResponse lookupEmployeeOnly(String identifier) {
        return employeeRepository.findByRfidOrEmployeeNo(identifier)
                .map(this::employeeLookup)
                .orElseGet(() -> RfidLookupResponse.notFound(
                        "No employee record found for \"" + identifier + "\"."
                ));
    }

    private RfidLookupResponse lookupAny(String identifier) {
        var student = studentRepository.findByRfidOrStudentNo(identifier);
        if (student.isPresent()) {
            return studentLookup(student.get());
        }
        var employee = employeeRepository.findByRfidOrEmployeeNo(identifier);
        if (employee.isPresent()) {
            return employeeLookup(employee.get());
        }
        return RfidLookupResponse.notFound(
                "No student or employee record found for \"" + identifier + "\"."
        );
    }

    private RfidLookupResponse studentLookup(Student student) {
        StudentAuditEventResponse createdAudit = studentAuditEventRepository
                .findLatestCreatedByStudentId(student.getId())
                .map(StudentAuditEventResponse::from)
                .orElse(null);
        return RfidLookupResponse.student(StudentResponse.from(student), createdAudit);
    }

    private RfidLookupResponse employeeLookup(Employee employee) {
        EmployeeAuditEventResponse createdAudit = employeeAuditEventRepository
                .findLatestCreatedByEmployeeId(employee.getId())
                .map(EmployeeAuditEventResponse::from)
                .orElse(null);
        return RfidLookupResponse.employee(EmployeeResponse.from(employee), createdAudit);
    }
}
