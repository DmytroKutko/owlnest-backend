package dev.dkutko.owlnest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void rejectsRequestWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/profile/me"))
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
                .andExpect(jsonPath("$.displayName").value("Profile User"))
                .andExpect(jsonPath("$.email").value("profile.user@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true));

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

}
