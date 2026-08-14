package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nors.dev.codes.lpu.dto.EmployeeRequest;
import org.nors.dev.codes.lpu.dto.EmployeeImportRequest;
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
                "ana.reyes@lpu.edu.ph",
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
        assertEquals("ana.reyes@lpu.edu.ph", persisted.getLpuEmail());

        ArgumentCaptor<EmployeeAuditEvent> auditCaptor = ArgumentCaptor.forClass(EmployeeAuditEvent.class);
        verify(employeeAuditEventRepository).persist(auditCaptor.capture());
        EmployeeAuditEvent auditEvent = auditCaptor.getValue();
        assertEquals("CREATED", auditEvent.getAction());
        assertEquals(9L, auditEvent.getActorUserId());
        assertEquals("hr.admin", auditEvent.getActorUsername());
        assertNotNull(auditEvent.getCreatedAt());
        assertEquals(persisted, auditEvent.getEmployee());
    }

    @Test
    void import_updatesExistingEmployeeWithOnlyEmployeeNumberAndLpuEmail() {
        Employee existing = new Employee();
        existing.setId(8L);
        existing.setName("Existing Employee");
        existing.setEmployeeNo("EMP-0002");
        existing.setDepartment("HR");
        existing.setPosition("Officer");
        existing.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(employeeRepository.findAllByEmployeeNoKey()).thenReturn(Map.of("emp-0002", existing));
        when(rfidUniquenessService.findAllActiveRfids()).thenReturn(Set.of());

        var result = employeeService.importEmployees(
                java.util.List.of(new EmployeeImportRequest(
                        null, "EMP-0002", null, null, null, "employee@lpu.edu.ph", null, null
                )),
                9L,
                "hr.admin"
        );

        assertEquals(0, result.imported());
        assertEquals(1, result.updated());
        assertEquals("employee@lpu.edu.ph", existing.getLpuEmail());
        assertEquals("Existing Employee", existing.getName());
        verify(employeeRepository).save(existing);
    }

    @Test
    void import_skipsUnknownEmployeeWhenNameIsMissing() {
        when(employeeRepository.findAllByEmployeeNoKey()).thenReturn(Map.of());
        when(rfidUniquenessService.findAllActiveRfids()).thenReturn(Set.of());

        var result = employeeService.importEmployees(
                java.util.List.of(new EmployeeImportRequest(
                        null, "EMP-0999", null, null, null, "unknown@lpu.edu.ph", null, null
                )),
                9L,
                "hr.admin"
        );

        assertEquals(0, result.imported());
        assertEquals(0, result.updated());
        assertEquals(1, result.skippedIncomplete());
    }
}
