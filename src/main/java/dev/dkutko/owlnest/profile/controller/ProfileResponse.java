package dev.dkutko.owlnest.profile.controller;

import dev.dkutko.owlnest.profile.domain.Gender;
import dev.dkutko.owlnest.profile.service.CurrentProfile;
import dev.dkutko.owlnest.media.controller.MediaReferenceResponse;

import java.time.LocalDate;
import java.util.UUID;

public record ProfileResponse(
        UUID accountId,
        String username,
        String displayName,
        String bio,
        LocalDate birthDate,
        Gender gender,
        boolean onboardingCompleted,
        MediaReferenceResponse avatar,
        String email,
        boolean emailVerified
) {

    public static ProfileResponse from(CurrentProfile profile) {
        return new ProfileResponse(
                profile.accountId(),
                profile.username(),
                profile.displayName(),
                profile.bio(),
                profile.birthDate(),
                profile.gender(),
                profile.onboardingCompleted(),
                profile.avatarMediaId() == null ? null : MediaReferenceResponse.from(profile.avatarMediaId()),
                profile.email(),
                profile.emailVerified()
        );
    }

}
