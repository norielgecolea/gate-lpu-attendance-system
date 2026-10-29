package org.nors.dev.codes.lpu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.nors.dev.codes.lpu.model.KioskGroups;
import org.nors.dev.codes.lpu.model.User;
import org.nors.dev.codes.lpu.repository.UserRepository;
import org.nors.dev.codes.lpu.service.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (jwtService.isTokenValid(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        Long userId = jwtService.extractUserId(token);
        if (userId == null) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isActive() || user.getSessionVersion() != jwtService.extractSessionVersion(token)) {
            return;
        }
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getLocation(),
                jwtService.isRememberMe(token),
                user.isPasswordChangeRequired() && !KioskGroups.isKioskRole(user.getRole()),
                user.getSessionVersion()
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
