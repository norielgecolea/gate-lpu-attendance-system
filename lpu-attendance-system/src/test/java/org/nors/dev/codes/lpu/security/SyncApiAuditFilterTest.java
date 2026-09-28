package org.nors.dev.codes.lpu.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.nors.dev.codes.lpu.service.ApiRequestAuditService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SyncApiAuditFilterTest {

    @Test
    void recordsSyncRequestsAfterTheResponseStatusIsKnown() throws Exception {
        RecordingAuditService audit = new RecordingAuditService();
        SyncApiAuditFilter filter = new SyncApiAuditFilter(audit);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/attendance-system/api/sync/employee-attendance"
        );
        request.setServletPath("/api/sync/employee-attendance");
        request.setQueryString("startDate=2026-09-01&endDate=2026-09-28");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        request.addHeader("X-Sync-Api-Key", "secret-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

        filter.doFilter(request, response, chain);

        assertEquals("GET", audit.method);
        assertEquals("/api/sync/employee-attendance", audit.path);
        assertEquals("startDate=2026-09-01&endDate=2026-09-28", audit.queryString);
        assertEquals(200, audit.statusCode);
        assertEquals("203.0.113.10", audit.clientIp);
    }

    @Test
    void ignoresRoutesOutsideTheSyncApi() throws Exception {
        RecordingAuditService audit = new RecordingAuditService();
        SyncApiAuditFilter filter = new SyncApiAuditFilter(audit);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/audit-logs");
        request.setServletPath("/api/audit-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        assertEquals(0, audit.calls);
    }

    private static final class RecordingAuditService extends ApiRequestAuditService {
        private int calls;
        private String method;
        private String path;
        private String queryString;
        private int statusCode;
        private String clientIp;

        private RecordingAuditService() {
            super(null);
        }

        @Override
        public void record(String method, String path, String queryString, int statusCode, String clientIp) {
            calls++;
            this.method = method;
            this.path = path;
            this.queryString = queryString;
            this.statusCode = statusCode;
            this.clientIp = clientIp;
        }
    }
}
