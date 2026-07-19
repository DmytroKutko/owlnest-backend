package dev.dkutko.owlnest.profile.service;

import dev.dkutko.owlnest.presence.service.PresenceStatus;

import java.util.UUID;

public record PublicProfile(
        UUID accountId,
        String username,
        String displayName,
        String bio,
        UUID avatarMediaId,
        PresenceStatus presenceStatus
) {
}
