package org.nors.dev.codes.lpu.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nors.dev.codes.lpu.config.SyncApiProperties;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class SyncApiKeyFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlyAConfiguredKeyOnSyncRoutes() throws Exception {
        SyncApiProperties properties = new SyncApiProperties();
        properties.setApiKey("a-long-random-sync-key");
        SyncApiKeyFilter filter = new SyncApiKeyFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sync/students");
        request.setServletPath("/api/sync/students");
        request.addHeader("X-Sync-Api-Key", "a-long-random-sync-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, chain);

        assertTrue(SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SYNC")));
        verify(chain).doFilter(request, response);
    }
}
