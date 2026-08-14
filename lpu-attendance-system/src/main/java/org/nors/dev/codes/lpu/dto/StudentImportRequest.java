package org.nors.dev.codes.lpu.dto;

import java.time.LocalDate;

/**
 * CSV import fields are optional for matching records. New records still require
 * the fields enforced by {@link StudentRequest}.
 */
public record StudentImportRequest(
        String name,
        String studentNo,
        String photo,
        String rfid,
        LocalDate birthdate,
        String lpuEmail,
        String department,
        String course,
        String school
) {
    public StudentRequest asCreateRequest() {
        return new StudentRequest(
                name, studentNo, photo, rfid, birthdate, lpuEmail, department, course, school
        );
    }
}
