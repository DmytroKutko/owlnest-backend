package dev.dkutko.owlnest.profile.application;

import java.util.UUID;

public record CurrentProfile(
        UUID accountId,
        String username,
        String displayName,
        String bio,
        String email,
        boolean emailVerified
) {
}
