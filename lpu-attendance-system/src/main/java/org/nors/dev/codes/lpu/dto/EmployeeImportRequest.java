package org.nors.dev.codes.lpu.dto;

import java.time.LocalDate;

/**
 * CSV import fields are optional for matching records. New records still require
 * the fields enforced by {@link EmployeeRequest}.
 */
public record EmployeeImportRequest(
        String name,
        String employeeNo,
        String photo,
        String rfid,
        LocalDate birthdate,
        String lpuEmail,
        String department,
        String position
) {
    public EmployeeRequest asCreateRequest() {
        return new EmployeeRequest(
                name, employeeNo, photo, rfid, birthdate, lpuEmail, department, position
        );
    }
}
