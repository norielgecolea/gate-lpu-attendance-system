package org.nors.dev.codes.lpu.controller;

import jakarta.validation.Valid;
import java.util.Map;
import org.nors.dev.codes.lpu.dto.LoginRequest;
import org.nors.dev.codes.lpu.dto.LoginResponse;
import org.nors.dev.codes.lpu.security.AuthenticatedUser;
import org.nors.dev.codes.lpu.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@AuthenticationPrincipal AuthenticatedUser user) {
        String username = user != null ? user.getUsername() : "unknown";
        authService.logout(username);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(@AuthenticationPrincipal AuthenticatedUser user) {
        java.util.LinkedHashMap<String, String> body = new java.util.LinkedHashMap<>();
        body.put("username", user.getUsername());
        body.put("role", user.getRole().name());
        if (user.getLocation() != null) {
            body.put("location", user.getLocation());
        }
        return ResponseEntity.ok(body);
    }
}
