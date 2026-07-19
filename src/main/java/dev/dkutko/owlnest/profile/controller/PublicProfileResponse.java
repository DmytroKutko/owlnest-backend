package dev.dkutko.owlnest.profile.controller;

import dev.dkutko.owlnest.presence.service.PresenceStatus;
import dev.dkutko.owlnest.profile.service.PublicProfile;
import dev.dkutko.owlnest.media.controller.MediaReferenceResponse;

import java.util.UUID;

public record PublicProfileResponse(
        UUID accountId,
        String username,
        String displayName,
        String bio,
        MediaReferenceResponse avatar,
        PresenceStatus presenceStatus
) {

    public static PublicProfileResponse from(PublicProfile profile) {
        return new PublicProfileResponse(
                profile.accountId(),
                profile.username(),
                profile.displayName(),
                profile.bio(),
                profile.avatarMediaId() == null ? null : MediaReferenceResponse.from(profile.avatarMediaId()),
                profile.presenceStatus()
        );
    }

}
