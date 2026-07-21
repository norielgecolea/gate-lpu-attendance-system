package org.nors.dev.codes.lpu.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.UserRequest;
import org.nors.dev.codes.lpu.dto.UserResponse;
import org.nors.dev.codes.lpu.model.Role;
import org.nors.dev.codes.lpu.model.User;
import org.nors.dev.codes.lpu.repository.UserRepository;
import org.nors.dev.codes.lpu.security.UserRoleAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private static final Logger log = LogManager.getLogger(UserService.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(Role actingRole) {
        return userRepository.findAll().stream()
                .filter(user -> UserRoleAccess.canManage(actingRole, user.getRole()))
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> assignableRoles(Role actingRole) {
        return UserRoleAccess.manageableRoles(actingRole).stream()
                .map(Role::name)
                .sorted()
                .toList();
    }

    @Transactional
    public UserResponse create(UserRequest request, Role actingRole) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsernameExcludingId(username, null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        String password = request.password() == null ? "" : request.password();
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters"
            );
        }

        Role newRole = parseRole(request.role());
        UserRoleAccess.ensureCanManage(actingRole, newRole);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(newRole);
        user.setLocation(blankToNull(request.location()));
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.persist(user);

        log.info("Created user username={} role={}", user.getUsername(), user.getRole());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(Long id, UserRequest request, Long actingUserId, Role actingRole) {
        User user = requireUser(id);
        UserRoleAccess.ensureCanManage(actingRole, user.getRole());
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsernameExcludingId(username, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        Role newRole = parseRole(request.role());
        UserRoleAccess.ensureCanManage(actingRole, newRole);
        if (user.getRole() == Role.SUPERADMIN && newRole != Role.SUPERADMIN
                && userRepository.countActiveSuperadminsExcluding(id) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one active Superadmin is required");
        }
        if (id.equals(actingUserId) && newRole != user.getRole()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot change your own role");
        }

        user.setUsername(username);
        user.setRole(newRole);
        user.setLocation(blankToNull(request.location()));
        String password = request.password();
        if (password != null && !password.isBlank()) {
            if (password.length() < MIN_PASSWORD_LENGTH) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Password must be at least " + MIN_PASSWORD_LENGTH + " characters"
                );
            }
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Updated user id={} username={} role={}", id, user.getUsername(), user.getRole());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setActive(Long id, boolean active, Long actingUserId, Role actingRole) {
        User user = requireUser(id);
        UserRoleAccess.ensureCanManage(actingRole, user.getRole());
        if (!active) {
            if (id.equals(actingUserId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot deactivate your own account");
            }
            if (user.getRole() == Role.SUPERADMIN
                    && userRepository.countActiveSuperadminsExcluding(id) == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one active Superadmin is required");
            }
        }
        user.setActive(active);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("{} user id={} username={}", active ? "Activated" : "Deactivated", id, user.getUsername());
        return UserResponse.from(user);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        return username.trim();
    }

    private static Role parseRole(String role) {
        try {
            return Role.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
