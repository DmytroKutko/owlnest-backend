package dev.dkutko.owlnest.profile.controller;

import dev.dkutko.owlnest.presence.service.PresenceStatus;
import dev.dkutko.owlnest.profile.service.PublicProfile;

import java.util.UUID;

public record PublicProfileResponse(
        UUID accountId,
        String username,
        String displayName,
        String bio,
        PresenceStatus presenceStatus
) {

    public static PublicProfileResponse from(PublicProfile profile) {
        return new PublicProfileResponse(
                profile.accountId(),
                profile.username(),
                profile.displayName(),
                profile.bio(),
                profile.presenceStatus()
        );
    }

}
