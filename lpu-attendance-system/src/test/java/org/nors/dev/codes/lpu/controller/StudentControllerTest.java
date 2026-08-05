package org.nors.dev.codes.lpu.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nors.dev.codes.lpu.dto.StudentAuditEventResponse;
import org.nors.dev.codes.lpu.dto.StudentRequest;
import org.nors.dev.codes.lpu.dto.StudentResponse;
import org.nors.dev.codes.lpu.model.Role;
import org.nors.dev.codes.lpu.security.AuthenticatedUser;
import org.nors.dev.codes.lpu.service.AttendanceService;
import org.nors.dev.codes.lpu.service.PhotoStorageService;
import org.nors.dev.codes.lpu.service.StudentService;

@ExtendWith(MockitoExtension.class)
class StudentControllerTest {

    @Mock
    private StudentService studentService;

    @Mock
    private PhotoStorageService photoStorageService;

    @Mock
    private AttendanceService attendanceService;

    @Test
    void create_passesAuthenticatedActorToService() {
        StudentController controller = new StudentController(studentService, photoStorageService, attendanceService);
        StudentRequest request = new StudentRequest(
                "Santos, Maria C.",
                "2026-0002",
                null,
                null,
                LocalDate.of(2004, 3, 8),
                null,
                "CCS",
                "BSCS",
                "LPL"
        );
        StudentResponse response = new StudentResponse(
                "1",
                "Santos, Maria C.",
                "2026-0002",
                null,
                null,
                LocalDate.of(2004, 3, 8),
                null,
                "CCS",
                "BSCS",
                "LPL",
                false
        );
        AuthenticatedUser user = new AuthenticatedUser(7L, "osas.admin", Role.OSAS, "Main Gate");
        when(studentService.create(request, 7L, "osas.admin")).thenReturn(response);

        StudentResponse body = controller.create(request, user).getBody();

        verify(studentService).create(request, 7L, "osas.admin");
        assertEquals(response, body);
    }

    @Test
    void audit_returnsStudentAuditEvents() {
        StudentController controller = new StudentController(studentService, photoStorageService, attendanceService);
        List<StudentAuditEventResponse> events = List.of(
                new StudentAuditEventResponse("10", "CREATED", 7L, "osas.admin", Instant.parse("2026-07-28T08:00:00Z"))
        );
        when(studentService.listAuditEvents(123L)).thenReturn(events);

        List<StudentAuditEventResponse> body = controller.audit(123L).getBody();

        verify(studentService).listAuditEvents(123L);
        assertEquals(events, body);
    }
}
