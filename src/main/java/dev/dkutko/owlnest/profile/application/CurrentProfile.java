package dev.dkutko.owlnest.profile.application;

import dev.dkutko.owlnest.profile.domain.Gender;

import java.time.LocalDate;
import java.util.UUID;

public record CurrentProfile(
        UUID accountId,
        String username,
        String displayName,
        String bio,
        LocalDate birthDate,
        Gender gender,
        boolean onboardingCompleted,
        String email,
        boolean emailVerified
) {
}
