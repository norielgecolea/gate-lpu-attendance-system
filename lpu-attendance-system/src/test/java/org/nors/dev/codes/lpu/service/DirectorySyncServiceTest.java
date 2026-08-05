package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nors.dev.codes.lpu.model.Student;
import org.nors.dev.codes.lpu.repository.EmployeeRepository;
import org.nors.dev.codes.lpu.repository.StudentRepository;
import org.nors.dev.codes.lpu.repository.SyncDeletionTombstoneRepository;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DirectorySyncServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SyncDeletionTombstoneRepository tombstoneRepository;

    @InjectMocks
    private DirectorySyncService directorySyncService;

    @Test
    void students_returnsAnOpaqueCheckpointForEachPage() {
        Student first = student(10L, "2025-01-01T00:00:00Z", "2025-0001");
        Student second = student(11L, "2025-01-01T00:00:01Z", "2025-0002");
        when(studentRepository.findUpdatedAfter(any(), any(), anyInt()))
                .thenReturn(List.of(first, second));

        var page = directorySyncService.students(null, 1);

        assertTrue(page.hasMore());
        assertEquals(1, page.records().size());
        assertEquals("2025-0001", page.records().getFirst().studentNo());
        assertFalse(page.nextCursor().isBlank());
    }

    @Test
    void students_rejectsMalformedCursor() {
        assertThrows(
                ResponseStatusException.class,
                () -> directorySyncService.students("not-a-cursor", 10)
        );
    }

    private Student student(Long id, String updatedAt, String studentNo) {
        Student student = new Student();
        student.setId(id);
        student.setName("Test Student");
        student.setStudentNo(studentNo);
        student.setDepartment("CCS");
        student.setCourse("BSIT");
        student.setSchool("LPL");
        student.setCreatedAt(Instant.parse(updatedAt));
        student.setUpdatedAt(Instant.parse(updatedAt));
        return student;
    }
}
