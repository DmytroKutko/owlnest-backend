package dev.dkutko.owlnest.identity.application;

import java.util.Objects;

public record AuthenticatedIdentity(
        String provider,
        String subject,
        String email,
        boolean emailVerified,
        String preferredUsername,
        String givenName,
        String familyName
) {

    public AuthenticatedIdentity {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
    }

}
