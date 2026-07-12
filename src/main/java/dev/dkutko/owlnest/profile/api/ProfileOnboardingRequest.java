package dev.dkutko.owlnest.profile.api;

import dev.dkutko.owlnest.profile.application.CompleteProfileOnboardingCommand;
import dev.dkutko.owlnest.profile.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProfileOnboardingRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._]+$", message = "must contain only letters, digits, dots, or underscores")
        String username,
        @NotBlank
        @Size(max = 100)
        String displayName,
        @Size(max = 500)
        String bio,
        @Past
        LocalDate birthDate,
        Gender gender
) {

    CompleteProfileOnboardingCommand toCommand() {
        return new CompleteProfileOnboardingCommand(username, displayName, bio, birthDate, gender);
    }

}
