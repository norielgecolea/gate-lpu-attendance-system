package org.nors.dev.codes.lpu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.service.ApiRequestAuditService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes each machine sync request to the administration audit trail.
 * The API key header is never stored.
 */
@Component
public class SyncApiAuditFilter extends OncePerRequestFilter {

    private static final Logger log = LogManager.getLogger(SyncApiAuditFilter.class);

    private final ApiRequestAuditService apiRequestAuditService;

    public SyncApiAuditFilter(ApiRequestAuditService apiRequestAuditService) {
        this.apiRequestAuditService = apiRequestAuditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path == null || !path.startsWith("/api/sync/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            record(request, response.getStatus());
        }
    }

    private void record(HttpServletRequest request, int status) {
        try {
            apiRequestAuditService.record(
                    request.getMethod(),
                    request.getServletPath(),
                    request.getQueryString(),
                    status,
                    clientIp(request)
            );
        } catch (Exception ex) {
            log.warn("Failed to write sync API request to the audit trail", ex);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
