package org.nors.dev.codes.lpu.dto;

import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.Employee;

public record EmployeeResponse(
        String id,
        String name,
        String employeeNo,
        String photo,
        String rfid,
        LocalDate birthdate,
        String department,
        String position
) {
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                String.valueOf(employee.getId()),
                employee.getName(),
                employee.getEmployeeNo(),
                employee.getPhoto(),
                employee.getRfid(),
                employee.getBirthdate(),
                employee.getDepartment(),
                employee.getPosition()
        );
    }
}
