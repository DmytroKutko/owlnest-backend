package dev.dkutko.owlnest.identity.infrastructure.security;

import dev.dkutko.owlnest.identity.application.AuthenticatedIdentity;
import dev.dkutko.owlnest.identity.application.CurrentIdentityProvider;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityIdentityProvider implements CurrentIdentityProvider {

    private static final String PROVIDER = "KEYCLOAK";

    @Override
    public AuthenticatedIdentity getCurrentIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("An authenticated JWT is required");
        }

        var jwt = jwtAuthentication.getToken();
        return new AuthenticatedIdentity(
                PROVIDER,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name")
        );
    }

}
