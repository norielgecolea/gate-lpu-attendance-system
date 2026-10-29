package org.nors.dev.codes.lpu.dto;

import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.Student;
import org.nors.dev.codes.lpu.security.MediaUrlSigner;

public record StudentResponse(
        String id,
        String name,
        String studentNo,
        String photo,
        String rfid,
        LocalDate birthdate,
        String lpuEmail,
        String department,
        String course,
        String school,
        boolean financeTagged,
        boolean alarmMarked
) {
    public static StudentResponse from(Student student) {
        return new StudentResponse(
                String.valueOf(student.getId()),
                student.getName(),
                student.getStudentNo(),
                MediaUrlSigner.signStored(student.getPhoto()),
                student.getRfid(),
                student.getBirthdate(),
                student.getLpuEmail(),
                student.getDepartment(),
                student.getCourse(),
                student.getSchool(),
                student.isFinanceTagged(),
                student.isAlarmMarked()
        );
    }

    /** Gate screens need the person and the finance/alarm flags, not the card value or contact details. */
    public StudentResponse forKiosk() {
        return new StudentResponse(
                id, name, studentNo, photo, null, null, null, department, course, school, financeTagged, alarmMarked
        );
    }
}
