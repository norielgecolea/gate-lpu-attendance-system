package org.nors.dev.codes.lpu.dto;

/**
 * Result of an admin RFID/ID checker lookup.
 * Exactly one of {@code student} / {@code employee} is populated when found.
 * Created-audit fields are set only when a matching CREATED audit row exists.
 */
public record RfidLookupResponse(
        boolean found,
        String personType,
        StudentResponse student,
        EmployeeResponse employee,
        StudentAuditEventResponse studentCreatedAudit,
        EmployeeAuditEventResponse employeeCreatedAudit,
        String message
) {
    public static RfidLookupResponse student(
            StudentResponse student,
            StudentAuditEventResponse studentCreatedAudit
    ) {
        return new RfidLookupResponse(true, "STUDENT", student, null, studentCreatedAudit, null, null);
    }

    public static RfidLookupResponse employee(
            EmployeeResponse employee,
            EmployeeAuditEventResponse employeeCreatedAudit
    ) {
        return new RfidLookupResponse(true, "EMPLOYEE", null, employee, null, employeeCreatedAudit, null);
    }

    public static RfidLookupResponse notFound(String message) {
        return new RfidLookupResponse(false, null, null, null, null, null, message);
    }
}
