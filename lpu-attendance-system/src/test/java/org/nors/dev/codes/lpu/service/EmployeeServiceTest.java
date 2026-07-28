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
import org.nors.dev.codes.lpu.dto.EmployeeRequest;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.model.EmployeeAuditEvent;
import org.nors.dev.codes.lpu.repository.EmployeeAuditEventRepository;
import org.nors.dev.codes.lpu.repository.EmployeeRepository;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeAuditEventRepository employeeAuditEventRepository;

    @Mock
    private PhotoStorageService photoStorageService;

    @Mock
    private RfidUniquenessService rfidUniquenessService;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    void create_persistsAuditEventWithActorAndTimestamp() {
        EmployeeRequest request = new EmployeeRequest(
                "Reyes, Ana B.",
                "EMP-0001",
                null,
                "RFID-456",
                LocalDate.of(1990, 5, 20),
                "HR",
                "Specialist"
        );
        when(employeeRepository.findByEmployeeNo("EMP-0001")).thenReturn(Optional.empty());
        doNothing().when(rfidUniquenessService).assertAvailable(
                "RFID-456",
                RfidUniquenessService.OwnerType.EMPLOYEE,
                null
        );

        employeeService.create(request, 9L, "hr.admin");

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).persist(employeeCaptor.capture());
        Employee persisted = employeeCaptor.getValue();
        assertEquals("EMP-0001", persisted.getEmployeeNo());

        ArgumentCaptor<EmployeeAuditEvent> auditCaptor = ArgumentCaptor.forClass(EmployeeAuditEvent.class);
        verify(employeeAuditEventRepository).persist(auditCaptor.capture());
        EmployeeAuditEvent auditEvent = auditCaptor.getValue();
        assertEquals("CREATED", auditEvent.getAction());
        assertEquals(9L, auditEvent.getActorUserId());
        assertEquals("hr.admin", auditEvent.getActorUsername());
        assertNotNull(auditEvent.getCreatedAt());
        assertEquals(persisted, auditEvent.getEmployee());
    }
}
