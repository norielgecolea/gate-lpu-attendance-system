package org.nors.dev.codes.lpu.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.nors.dev.codes.lpu.model.User;
import org.nors.dev.codes.lpu.repository.UserRepository;
import org.springframework.stereotype.Service;

/** Short-lived socket tickets so the long-lived JWT is not placed in the URL. */
@Service
public class WsTicketService {

    private static final long TTL_MS = 120_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();

    public WsTicketService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String issue(AuthenticatedUser user) {
        purgeExpired();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Ticket(user.getId(), user.getSessionVersion(), System.currentTimeMillis() + TTL_MS));
        return ticket;
    }

    public Optional<User> authenticate(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        Ticket stored = tickets.get(ticket);
        if (stored == null || stored.expiresAtMs() < System.currentTimeMillis()) {
            if (stored != null) {
                tickets.remove(ticket);
            }
            return Optional.empty();
        }
        return userRepository.findById(stored.userId())
                .filter(User::isActive)
                .filter(user -> !user.isPasswordChangeRequired())
                .filter(user -> user.getSessionVersion() == stored.sessionVersion());
    }

    private void purgeExpired() {
        if (tickets.size() < 2_000) {
            return;
        }
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAtMs() < now);
    }

    private record Ticket(Long userId, int sessionVersion, long expiresAtMs) {
    }
}
