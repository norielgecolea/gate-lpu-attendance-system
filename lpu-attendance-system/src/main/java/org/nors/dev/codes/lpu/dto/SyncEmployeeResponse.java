package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.Employee;

public record SyncEmployeeResponse(
        Long sourceId,
        String employeeNo,
        String name,
        String rfid,
        LocalDate birthdate,
        String lpuEmail,
        String department,
        String position,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {
    public static SyncEmployeeResponse from(Employee employee) {
        return new SyncEmployeeResponse(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getName(),
                employee.getRfid(),
                employee.getBirthdate(),
                employee.getLpuEmail(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.isDeleted(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
