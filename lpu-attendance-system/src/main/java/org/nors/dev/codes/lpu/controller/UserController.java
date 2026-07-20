package org.nors.dev.codes.lpu.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.nors.dev.codes.lpu.dto.UserRequest;
import org.nors.dev.codes.lpu.dto.UserResponse;
import org.nors.dev.codes.lpu.security.AuthenticatedUser;
import org.nors.dev.codes.lpu.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(userService.list());
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserRequest request,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        Long actingUserId = actingUser != null ? actingUser.getId() : null;
        return ResponseEntity.ok(userService.update(id, request, actingUserId));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<UserResponse> activate(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        Long actingUserId = actingUser != null ? actingUser.getId() : null;
        return ResponseEntity.ok(userService.setActive(id, true, actingUserId));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<UserResponse> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        Long actingUserId = actingUser != null ? actingUser.getId() : null;
        return ResponseEntity.ok(userService.setActive(id, false, actingUserId));
    }

    @GetMapping("/roles")
    public ResponseEntity<Map<String, List<String>>> roles() {
        return ResponseEntity.ok(Map.of(
                "roles", List.of("SUPERADMIN", "OSAS", "HR", "MONITORING", "GUARD")
        ));
    }
}
