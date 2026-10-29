package org.nors.dev.codes.lpu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Accounts still using a published seed password may only change it or sign out. */
@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return true;
        }
        if (!user.isPasswordChangeRequired()) {
            return true;
        }
        return isAllowed(request.getMethod(), RequestPaths.withinContext(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Password change required\"}"
        );
    }

    private static boolean isAllowed(String method, String path) {
        if ("POST".equalsIgnoreCase(method) && "/api/auth/change-password".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/auth/logout".equals(path)) {
            return true;
        }
        return "GET".equalsIgnoreCase(method) && "/api/auth/me".equals(path);
    }
}
