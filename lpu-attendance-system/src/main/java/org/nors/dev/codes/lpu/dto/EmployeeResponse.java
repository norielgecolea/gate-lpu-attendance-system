package org.nors.dev.codes.lpu.dto;

import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.security.MediaUrlSigner;

public record EmployeeResponse(
        String id,
        String name,
        String employeeNo,
        String photo,
        String rfid,
        LocalDate birthdate,
        String lpuEmail,
        String department,
        String position,
        boolean alarmMarked
) {
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                String.valueOf(employee.getId()),
                employee.getName(),
                employee.getEmployeeNo(),
                MediaUrlSigner.signStored(employee.getPhoto()),
                employee.getRfid(),
                employee.getBirthdate(),
                employee.getLpuEmail(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.isAlarmMarked()
        );
    }

    public EmployeeResponse forKiosk() {
        return new EmployeeResponse(
                id, name, employeeNo, photo, null, null, null, department, position, alarmMarked
        );
    }
}
