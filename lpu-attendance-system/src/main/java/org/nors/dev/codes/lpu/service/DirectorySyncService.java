package org.nors.dev.codes.lpu.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.nors.dev.codes.lpu.dto.SyncDeletionResponse;
import org.nors.dev.codes.lpu.dto.SyncEmployeeResponse;
import org.nors.dev.codes.lpu.dto.SyncPageResponse;
import org.nors.dev.codes.lpu.dto.SyncStudentResponse;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.model.Student;
import org.nors.dev.codes.lpu.model.SyncDeletionTombstone;
import org.nors.dev.codes.lpu.repository.EmployeeRepository;
import org.nors.dev.codes.lpu.repository.StudentRepository;
import org.nors.dev.codes.lpu.repository.SyncDeletionTombstoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DirectorySyncService {

    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final int MAX_PAGE_SIZE = 1_000;
    private static final Instant START_OF_TIME = Instant.EPOCH;

    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final SyncDeletionTombstoneRepository tombstoneRepository;

    public DirectorySyncService(
            StudentRepository studentRepository,
            EmployeeRepository employeeRepository,
            SyncDeletionTombstoneRepository tombstoneRepository
    ) {
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
        this.tombstoneRepository = tombstoneRepository;
    }

    @Transactional(readOnly = true)
    public SyncPageResponse<SyncStudentResponse> students(String cursor, Integer limit) {
        Cursor checkpoint = decode(cursor);
        int pageSize = pageSize(limit);
        List<Student> source = studentRepository.findUpdatedAfter(
                checkpoint.timestamp(), checkpoint.id(), pageSize + 1
        );
        boolean hasMore = source.size() > pageSize;
        List<Student> page = hasMore ? source.subList(0, pageSize) : source;
        String nextCursor = page.isEmpty() ? null : encode(page.getLast().getUpdatedAt(), page.getLast().getId());
        return new SyncPageResponse<>(
                page.stream().map(SyncStudentResponse::from).toList(),
                nextCursor,
                hasMore
        );
    }

    @Transactional(readOnly = true)
    public SyncPageResponse<SyncEmployeeResponse> employees(String cursor, Integer limit) {
        Cursor checkpoint = decode(cursor);
        int pageSize = pageSize(limit);
        List<Employee> source = employeeRepository.findUpdatedAfter(
                checkpoint.timestamp(), checkpoint.id(), pageSize + 1
        );
        boolean hasMore = source.size() > pageSize;
        List<Employee> page = hasMore ? source.subList(0, pageSize) : source;
        String nextCursor = page.isEmpty() ? null : encode(page.getLast().getUpdatedAt(), page.getLast().getId());
        return new SyncPageResponse<>(
                page.stream().map(SyncEmployeeResponse::from).toList(),
                nextCursor,
                hasMore
        );
    }

    @Transactional(readOnly = true)
    public SyncPageResponse<SyncDeletionResponse> deletions(String cursor, Integer limit) {
        Cursor checkpoint = decode(cursor);
        int pageSize = pageSize(limit);
        List<SyncDeletionTombstone> source = tombstoneRepository.findDeletedAfter(
                checkpoint.timestamp(), checkpoint.id(), pageSize + 1
        );
        boolean hasMore = source.size() > pageSize;
        List<SyncDeletionTombstone> page = hasMore ? source.subList(0, pageSize) : source;
        String nextCursor = page.isEmpty() ? null : encode(page.getLast().getDeletedAt(), page.getLast().getId());
        return new SyncPageResponse<>(
                page.stream().map(SyncDeletionResponse::from).toList(),
                nextCursor,
                hasMore
        );
    }

    private int pageSize(Integer requestedLimit) {
        int value = requestedLimit == null ? DEFAULT_PAGE_SIZE : requestedLimit;
        if (value < 1 || value > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and " + MAX_PAGE_SIZE
            );
        }
        return value;
    }

    private Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(START_OF_TIME, 0L);
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = decoded.lastIndexOf('|');
            if (separator < 1 || separator == decoded.length() - 1) {
                throw new IllegalArgumentException();
            }
            return new Cursor(
                    Instant.parse(decoded.substring(0, separator)),
                    Long.parseLong(decoded.substring(separator + 1))
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sync cursor");
        }
    }

    private String encode(Instant timestamp, Long id) {
        String value = timestamp + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Cursor(Instant timestamp, Long id) {
    }
}
