package org.nors.dev.codes.lpu.controller;

import jakarta.validation.Valid;
import java.util.Map;
import org.nors.dev.codes.lpu.dto.ChangePasswordRequest;
import org.nors.dev.codes.lpu.dto.LoginRequest;
import org.nors.dev.codes.lpu.dto.LoginResponse;
import org.nors.dev.codes.lpu.dto.PasswordChangeResponse;
import org.nors.dev.codes.lpu.security.AuthenticatedUser;
import org.nors.dev.codes.lpu.security.WsTicketService;
import org.nors.dev.codes.lpu.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final WsTicketService wsTicketService;

    public AuthController(AuthService authService, WsTicketService wsTicketService) {
        this.authService = authService;
        this.wsTicketService = wsTicketService;
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

    @PostMapping("/change-password")
    public ResponseEntity<PasswordChangeResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(authService.changePassword(user, request));
    }

    @GetMapping("/ws-ticket")
    public ResponseEntity<Map<String, String>> wsTicket(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(Map.of("ticket", wsTicketService.issue(user)));
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
