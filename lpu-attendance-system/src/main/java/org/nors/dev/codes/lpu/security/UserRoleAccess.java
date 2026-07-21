package org.nors.dev.codes.lpu.security;

import java.util.EnumSet;
import java.util.Set;
import org.nors.dev.codes.lpu.model.Role;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Which user accounts each role may list, create, edit, or deactivate. */
public final class UserRoleAccess {

    private UserRoleAccess() {
    }

    public static Set<Role> manageableRoles(Role acting) {
        return switch (acting) {
            case SUPERADMIN -> EnumSet.allOf(Role.class);
            case OSAS -> EnumSet.of(Role.OSAS);
            case HR -> EnumSet.of(Role.HR);
            default -> Set.of();
        };
    }

    public static boolean canManage(Role acting, Role target) {
        return manageableRoles(acting).contains(target);
    }

    public static void ensureCanManage(Role acting, Role target) {
        if (!canManage(acting, target)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot manage users with role " + target.name()
            );
        }
    }
}
