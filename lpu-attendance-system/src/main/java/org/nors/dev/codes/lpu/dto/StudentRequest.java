package org.nors.dev.codes.lpu.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record StudentRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Student number is required") String studentNo,
        String photo,
        String rfid,
        LocalDate birthdate,
        @NotBlank(message = "Department is required") String department,
        @NotBlank(message = "Course is required") String course,
        @NotBlank(message = "School is required") String school
) {
}
