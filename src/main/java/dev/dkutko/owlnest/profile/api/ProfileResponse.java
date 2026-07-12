package dev.dkutko.owlnest.profile.api;

import dev.dkutko.owlnest.profile.application.CurrentProfile;

import java.util.UUID;

public record ProfileResponse(
        UUID accountId,
        String username,
        String displayName,
        String bio,
        String email,
        boolean emailVerified
) {

    public static ProfileResponse from(CurrentProfile profile) {
        return new ProfileResponse(
                profile.accountId(),
                profile.username(),
                profile.displayName(),
                profile.bio(),
                profile.email(),
                profile.emailVerified()
        );
    }

}
