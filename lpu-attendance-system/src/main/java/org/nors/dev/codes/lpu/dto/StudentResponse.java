package org.nors.dev.codes.lpu.dto;

import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.Student;

public record StudentResponse(
        String id,
        String name,
        String studentNo,
        String photo,
        String rfid,
        LocalDate birthdate,
        String department,
        String course,
        String school,
        boolean financeTagged
) {
    public static StudentResponse from(Student student) {
        return new StudentResponse(
                String.valueOf(student.getId()),
                student.getName(),
                student.getStudentNo(),
                student.getPhoto(),
                student.getRfid(),
                student.getBirthdate(),
                student.getDepartment(),
                student.getCourse(),
                student.getSchool(),
                student.isFinanceTagged()
        );
    }
}
