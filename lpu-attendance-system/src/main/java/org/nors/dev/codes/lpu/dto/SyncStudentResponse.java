package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.Student;

public record SyncStudentResponse(
        Long sourceId,
        String studentNo,
        String name,
        String rfid,
        LocalDate birthdate,
        String lpuEmail,
        String department,
        String course,
        String school,
        boolean financeTagged,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {
    public static SyncStudentResponse from(Student student) {
        return new SyncStudentResponse(
                student.getId(),
                student.getStudentNo(),
                student.getName(),
                student.getRfid(),
                student.getBirthdate(),
                student.getLpuEmail(),
                student.getDepartment(),
                student.getCourse(),
                student.getSchool(),
                student.isFinanceTagged(),
                student.isDeleted(),
                student.getCreatedAt(),
                student.getUpdatedAt()
        );
    }
}
