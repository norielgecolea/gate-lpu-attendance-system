package org.nors.dev.codes.lpu.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record EmployeeRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Employee number is required") String employeeNo,
        String photo,
        String rfid,
        LocalDate birthdate,
        String lpuEmail,
        String department,
        String position
) {
}
