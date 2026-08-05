package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nors.dev.codes.lpu.dto.StudentRequest;
import org.nors.dev.codes.lpu.model.Student;
import org.nors.dev.codes.lpu.model.StudentAuditEvent;
import org.nors.dev.codes.lpu.repository.StudentAuditEventRepository;
import org.nors.dev.codes.lpu.repository.StudentRepository;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentAuditEventRepository studentAuditEventRepository;

    @Mock
    private PhotoStorageService photoStorageService;

    @Mock
    private RfidUniquenessService rfidUniquenessService;

    @InjectMocks
    private StudentService studentService;

    @Test
    void create_persistsAuditEventWithActorAndTimestamp() {
        StudentRequest request = new StudentRequest(
                "Dela Cruz, Juan A.",
                "2026-0001",
                null,
                "RFID-123",
                LocalDate.of(2004, 1, 12),
                "juan.delacruz@lpu.edu.ph",
                "CCS",
                "BSIT",
                "LPL"
        );
        when(studentRepository.findByStudentNo("2026-0001")).thenReturn(Optional.empty());
        doNothing().when(rfidUniquenessService).assertAvailable(
                "RFID-123",
                RfidUniquenessService.OwnerType.STUDENT,
                null
        );

        studentService.create(request, 42L, "admin.user");

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).persist(studentCaptor.capture());
        Student persistedStudent = studentCaptor.getValue();
        assertEquals("2026-0001", persistedStudent.getStudentNo());
        assertEquals("juan.delacruz@lpu.edu.ph", persistedStudent.getLpuEmail());

        ArgumentCaptor<StudentAuditEvent> auditCaptor = ArgumentCaptor.forClass(StudentAuditEvent.class);
        verify(studentAuditEventRepository).persist(auditCaptor.capture());
        StudentAuditEvent auditEvent = auditCaptor.getValue();
        assertEquals("CREATED", auditEvent.getAction());
        assertEquals(42L, auditEvent.getActorUserId());
        assertEquals("admin.user", auditEvent.getActorUsername());
        assertNotNull(auditEvent.getCreatedAt());
        assertEquals(persistedStudent, auditEvent.getStudent());
    }
}
