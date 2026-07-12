package dev.dkutko.owlnest.profile.application;

import dev.dkutko.owlnest.profile.domain.Gender;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

public record CompleteProfileOnboardingCommand(
        String username,
        String displayName,
        String bio,
        LocalDate birthDate,
        Gender gender
) {

    public CompleteProfileOnboardingCommand {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        username = username.trim().toLowerCase(Locale.ROOT);
        displayName = displayName.trim();
        bio = bio == null || bio.isBlank() ? null : bio.trim();
    }

}
