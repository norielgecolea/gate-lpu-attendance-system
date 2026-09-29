package org.nors.dev.codes.lpu.service;

import java.time.Instant;
import org.nors.dev.codes.lpu.model.ApiRequestAuditEvent;
import org.nors.dev.codes.lpu.repository.ApiRequestAuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiRequestAuditService {

    static final String ACTOR = "ERP";

    private final ApiRequestAuditEventRepository apiRequestAuditEventRepository;

    public ApiRequestAuditService(ApiRequestAuditEventRepository apiRequestAuditEventRepository) {
        this.apiRequestAuditEventRepository = apiRequestAuditEventRepository;
    }

    @Transactional
    public void record(String method, String path, String queryString, int statusCode, String clientIp) {
        ApiRequestAuditEvent event = new ApiRequestAuditEvent();
        event.setMethod(truncate(method, 10));
        event.setPath(truncate(path, 200));
        event.setLabel(labelFor(path));
        event.setQueryString(blankToNull(truncate(queryString, 500)));
        event.setStatusCode(statusCode <= 0 ? 500 : statusCode);
        event.setClientIp(blankToNull(truncate(clientIp, 64)));
        event.setCreatedAt(Instant.now());
        apiRequestAuditEventRepository.persist(event);
    }

    static String labelFor(String path) {
        if (path == null) {
            return "Sync API";
        }
        if (path.endsWith("/employee-attendance")) {
            return "Employee attendance";
        }
        if (path.endsWith("/employees")) {
            return "Employee directory";
        }
        if (path.endsWith("/students")) {
            return "Student directory";
        }
        if (path.endsWith("/deletions")) {
            return "Directory deletions";
        }
        return "Sync API";
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
