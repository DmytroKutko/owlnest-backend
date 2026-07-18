package dev.dkutko.owlnest.identity.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringSecurityIdentityProviderTest {

    private final SpringSecurityIdentityProvider provider = new SpringSecurityIdentityProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsValidatedJwtClaimsToProviderNeutralIdentity() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("keycloak-user-id")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("email", "user@example.com")
                .claim("email_verified", true)
                .claim("preferred_username", "user@example.com")
                .claim("given_name", "Ada")
                .claim("family_name", "Lovelace")
                .build();
        var authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        var identity = provider.getCurrentIdentity();

        assertThat(identity.provider()).isEqualTo("KEYCLOAK");
        assertThat(identity.subject()).isEqualTo("keycloak-user-id");
        assertThat(identity.email()).isEqualTo("user@example.com");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.givenName()).isEqualTo("Ada");
        assertThat(identity.familyName()).isEqualTo("Lovelace");
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(provider::getCurrentIdentity)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

}
