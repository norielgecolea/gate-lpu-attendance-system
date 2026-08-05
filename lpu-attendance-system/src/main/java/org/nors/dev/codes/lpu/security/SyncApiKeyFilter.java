package org.nors.dev.codes.lpu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.nors.dev.codes.lpu.config.SyncApiProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SyncApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Sync-Api-Key";

    private final SyncApiProperties syncApiProperties;

    public SyncApiKeyFilter(SyncApiProperties syncApiProperties) {
        this.syncApiProperties = syncApiProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/sync/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String configuredKey = syncApiProperties.getApiKey();
        String suppliedKey = request.getHeader(API_KEY_HEADER);

        if (configuredKey != null && !configuredKey.isBlank()
                && suppliedKey != null
                && MessageDigest.isEqual(
                        configuredKey.getBytes(StandardCharsets.UTF_8),
                        suppliedKey.getBytes(StandardCharsets.UTF_8)
                )
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            var authority = new SimpleGrantedAuthority("ROLE_SYNC");
            var authentication = new UsernamePasswordAuthenticationToken(
                    "sync-api",
                    null,
                    List.of(authority)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
