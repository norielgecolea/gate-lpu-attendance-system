package org.nors.dev.codes.lpu.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.AuthEventMessage;
import org.nors.dev.codes.lpu.dto.LoginRequest;
import org.nors.dev.codes.lpu.dto.LoginResponse;
import org.nors.dev.codes.lpu.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Logger log = LogManager.getLogger(AuthService.class);

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public AuthService(
            UserService userService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService
    ) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();

        User user = userService.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed for username={}", username);
            notificationService.broadcast(
                    AuthEventMessage.of("AUTH_LOGIN_FAILURE", username, "Invalid credentials")
            );
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        if (!user.isActive()) {
            log.warn("Login blocked for inactive username={}", username);
            notificationService.broadcast(
                    AuthEventMessage.of("AUTH_LOGIN_FAILURE", username, "Account is inactive")
            );
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is inactive");
        }

        // All defined roles may sign in; what each role can access is enforced per endpoint.
        boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
        String token = jwtService.generateToken(user, rememberMe);
        log.info("Login success for username={} role={} rememberMe={}", username, user.getRole(), rememberMe);
        notificationService.broadcast(
                AuthEventMessage.of(
                        "AUTH_LOGIN_SUCCESS",
                        username,
                        user.getRole().name() + " signed in"
                )
        );

        return new LoginResponse(
                token,
                "Bearer",
                user.getUsername(),
                user.getRole().name(),
                user.getLocation(),
                jwtService.getExpirationMs(rememberMe)
        );
    }

    public void logout(String username) {
        log.info("Logout for username={}", username);
        notificationService.broadcast(
                AuthEventMessage.of("AUTH_LOGOUT", username, "Superadmin signed out")
        );
    }
}
