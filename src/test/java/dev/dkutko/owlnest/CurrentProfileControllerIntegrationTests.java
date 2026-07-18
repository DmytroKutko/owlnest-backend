package dev.dkutko.owlnest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CurrentProfileControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void rejectsRequestWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/profile/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/profile/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void provisionsOneAccountAndProfileForRepeatedAuthenticatedRequests() throws Exception {
        var authenticatedRequest = get("/api/v1/profile/me")
                .with(jwt().jwt(token -> token
                        .subject("keycloak-profile-user")
                        .claim("email", "profile.user@example.com")
                        .claim("email_verified", true)
                        .claim("preferred_username", "profile.user@example.com")
                        .claim("given_name", "Profile")
                        .claim("family_name", "User")
                ));

        mockMvc.perform(authenticatedRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").isNotEmpty())
                .andExpect(jsonPath("$.username").value(org.hamcrest.Matchers.startsWith("user_")))
                .andExpect(jsonPath("$.displayName").value("OwlNest user"))
                .andExpect(jsonPath("$.email").value("profile.user@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.onboardingCompleted").value(false));

        mockMvc.perform(authenticatedRequest)
                .andExpect(status().isOk());

        Integer accountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_account WHERE external_subject = ?",
                Integer.class,
                "keycloak-profile-user"
        );
        Integer profileCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM profile p
                        JOIN identity_account a ON a.id = p.account_id
                        WHERE a.external_subject = ?
                        """,
                Integer.class,
                "keycloak-profile-user"
        );

        assertThat(accountCount).isEqualTo(1);
        assertThat(profileCount).isEqualTo(1);
    }

    @Test
    void completesProfileOnboarding() throws Exception {
        mockMvc.perform(put("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token
                                .subject("onboarding-user")
                                .claim("email", "onboarding.user@example.com")
                                .claim("email_verified", false)
                                .claim("given_name", "Onboarding")
                                .claim("family_name", "User")
                        ))
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "Onboarding.User",
                                  "displayName": "OwlNest Member",
                                  "bio": "Building a social profile",
                                  "birthDate": "1995-04-20",
                                  "gender": "PREFER_NOT_TO_SAY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("onboarding.user"))
                .andExpect(jsonPath("$.displayName").value("OwlNest Member"))
                .andExpect(jsonPath("$.bio").value("Building a social profile"))
                .andExpect(jsonPath("$.birthDate").value("1995-04-20"))
                .andExpect(jsonPath("$.gender").value("PREFER_NOT_TO_SAY"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));

        Boolean onboardingCompleted = jdbcTemplate.queryForObject(
                """
                        SELECT p.onboarding_completed
                        FROM profile p
                        JOIN identity_account a ON a.id = p.account_id
                        WHERE a.external_subject = ?
                        """,
                Boolean.class,
                "onboarding-user"
        );

        assertThat(onboardingCompleted).isTrue();
    }

    @Test
    void replacesCompletedProfileFields() throws Exception {
        var authenticatedUser = jwt().jwt(token -> token.subject("profile-edit-user"));

        mockMvc.perform(put("/api/v1/profile/me")
                        .with(authenticatedUser)
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "original.username",
                                  "displayName": "Original Name",
                                  "bio": "Original bio"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/profile/me")
                        .with(authenticatedUser)
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "updated.username",
                                  "displayName": "Updated Name",
                                  "bio": "Updated bio"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("updated.username"))
                .andExpect(jsonPath("$.displayName").value("Updated Name"))
                .andExpect(jsonPath("$.bio").value("Updated bio"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
    }

    @Test
    void returnsSafePublicProfileWithOfflinePresence() throws Exception {
        String subject = "public-profile-user";
        completeProfile(subject, "public.user", "Public User");
        UUID accountId = accountIdFor(subject);

        mockMvc.perform(get("/api/v1/profiles/{accountId}", accountId)
                        .with(jwt().jwt(token -> token.subject("public-profile-viewer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.username").value("public.user"))
                .andExpect(jsonPath("$.displayName").value("Public User"))
                .andExpect(jsonPath("$.bio").value("Public bio"))
                .andExpect(jsonPath("$.presenceStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.emailVerified").doesNotExist())
                .andExpect(jsonPath("$.birthDate").doesNotExist())
                .andExpect(jsonPath("$.gender").doesNotExist())
                .andExpect(jsonPath("$.onboardingCompleted").doesNotExist());
    }

    @Test
    void refreshesOnlinePresenceWithNinetySecondTtl() throws Exception {
        String subject = "online-profile-user";
        completeProfile(subject, "online.user", "Online User");
        UUID accountId = accountIdFor(subject);

        mockMvc.perform(post("/api/v1/presence/heartbeat")
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isNoContent());

        Long timeToLive = redisTemplate.getExpire("presence:account:" + accountId);
        assertThat(timeToLive).isBetween(1L, 90L);

        mockMvc.perform(get("/api/v1/profiles/{accountId}", accountId)
                        .with(jwt().jwt(token -> token.subject("online-profile-viewer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presenceStatus").value("ONLINE"));
    }

    @Test
    void hidesIncompleteProfile() throws Exception {
        String subject = "incomplete-public-profile-user";

        mockMvc.perform(get("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/profiles/{accountId}", accountIdFor(subject))
                        .with(jwt().jwt(token -> token.subject("incomplete-profile-viewer"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("profile.not_found"));
    }

    @Test
    void rejectsMissingPublicProfile() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/{accountId}", UUID.randomUUID())
                        .with(jwt().jwt(token -> token.subject("missing-profile-viewer"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("profile.not_found"));
    }

    @Test
    void rejectsPresenceHeartbeatWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/presence/heartbeat"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidProfileOnboardingInput() throws Exception {
        mockMvc.perform(put("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject("invalid-onboarding-user")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "invalid username",
                                  "displayName": "Invalid User",
                                  "birthDate": "2999-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUsernameAlreadyUsedByAnotherProfile() throws Exception {
        String onboardingBody = """
                {
                  "username": "shared.username",
                  "displayName": "Shared Username"
                }
                """;

        mockMvc.perform(put("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject("username-owner")))
                        .contentType("application/json")
                        .content(onboardingBody))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject("username-conflict-user")))
                        .contentType("application/json")
                        .content(onboardingBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("profile.username_conflict"));
    }

    private void completeProfile(String subject, String username, String displayName) throws Exception {
        mockMvc.perform(put("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token
                                .subject(subject)
                                .claim("email", subject + "@example.com")
                        ))
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "%s",
                                  "displayName": "%s",
                                  "bio": "Public bio"
                                }
                                """.formatted(username, displayName)))
                .andExpect(status().isOk());
    }

    private UUID accountIdFor(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM identity_account WHERE external_subject = ?",
                UUID.class,
                subject
        );
    }

}
