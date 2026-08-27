package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nors.dev.codes.lpu.model.Role;
import org.nors.dev.codes.lpu.model.User;
import org.nors.dev.codes.lpu.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void changeOwnPassword_updatesHashWhenCurrentMatches() {
        User user = user(1L, "hashed-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pass", "hashed-old")).thenReturn(true);
        when(passwordEncoder.matches("new-pass-1", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode("new-pass-1")).thenReturn("hashed-new");

        userService.changeOwnPassword(1L, "current-pass", "new-pass-1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed-new", captor.getValue().getPasswordHash());
    }

    @Test
    void changeOwnPassword_rejectsIncorrectCurrentPassword() {
        User user = user(1L, "hashed-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-old")).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.changeOwnPassword(1L, "wrong", "new-pass-1")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Current password is incorrect", ex.getReason());
        verify(userRepository, never()).save(user);
    }

    @Test
    void changeOwnPassword_rejectsShortNewPassword() {
        User user = user(1L, "hashed-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pass", "hashed-old")).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.changeOwnPassword(1L, "current-pass", "short")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(userRepository, never()).save(user);
    }

    @Test
    void changeOwnPassword_rejectsUnchangedPassword() {
        User user = user(1L, "hashed-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pass", "hashed-old")).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.changeOwnPassword(1L, "current-pass", "current-pass")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("New password must be different from the current password", ex.getReason());
        verify(userRepository, never()).save(user);
    }

    private static User user(Long id, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setUsername("guard.one");
        user.setPasswordHash(passwordHash);
        user.setRole(Role.GUARD);
        user.setActive(true);
        return user;
    }
}
