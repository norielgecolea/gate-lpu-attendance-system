package org.nors.dev.codes.lpu.dto;

/**
 * Result of an admin RFID/ID checker lookup.
 * Exactly one of {@code student} / {@code employee} is populated when found.
 */
public record RfidLookupResponse(
        boolean found,
        String personType,
        StudentResponse student,
        EmployeeResponse employee,
        String message
) {
    public static RfidLookupResponse student(StudentResponse student) {
        return new RfidLookupResponse(true, "STUDENT", student, null, null);
    }

    public static RfidLookupResponse employee(EmployeeResponse employee) {
        return new RfidLookupResponse(true, "EMPLOYEE", null, employee, null);
    }

    public static RfidLookupResponse notFound(String message) {
        return new RfidLookupResponse(false, null, null, null, message);
    }
}
