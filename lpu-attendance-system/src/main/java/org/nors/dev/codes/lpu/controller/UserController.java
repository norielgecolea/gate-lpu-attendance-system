package org.nors.dev.codes.lpu.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.nors.dev.codes.lpu.dto.UserRequest;
import org.nors.dev.codes.lpu.dto.UserResponse;
import org.nors.dev.codes.lpu.model.Role;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list(@AuthenticationPrincipal AuthenticatedUser actingUser) {
        Role actingRole = requireUserManagerRole(actingUser);
        return ResponseEntity.ok(userService.list(actingRole));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody UserRequest request,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        Role actingRole = requireUserManagerRole(actingUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request, actingRole));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserRequest request,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        Role actingRole = requireUserManagerRole(actingUser);
        Long actingUserId = actingUser != null ? actingUser.getId() : null;
        return ResponseEntity.ok(userService.update(id, request, actingUserId, actingRole));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<UserResponse> activate(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        Role actingRole = requireUserManagerRole(actingUser);
        Long actingUserId = actingUser != null ? actingUser.getId() : null;
        return ResponseEntity.ok(userService.setActive(id, true, actingUserId, actingRole));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<UserResponse> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser actingUser
    ) {
        Role actingRole = requireUserManagerRole(actingUser);
        Long actingUserId = actingUser != null ? actingUser.getId() : null;
        return ResponseEntity.ok(userService.setActive(id, false, actingUserId, actingRole));
    }

    @GetMapping("/roles")
    public ResponseEntity<Map<String, List<String>>> roles(@AuthenticationPrincipal AuthenticatedUser actingUser) {
        Role actingRole = requireUserManagerRole(actingUser);
        return ResponseEntity.ok(Map.of("roles", userService.assignableRoles(actingRole)));
    }

    private static Role requireUserManagerRole(AuthenticatedUser actingUser) {
        if (actingUser == null || actingUser.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        }
        Role role = actingUser.getRole();
        if (role != Role.SUPERADMIN && role != Role.OSAS && role != Role.HR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User management not allowed");
        }
        return role;
    }
}
