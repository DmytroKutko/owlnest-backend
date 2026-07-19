package dev.dkutko.owlnest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "owlnest.media.r2.enabled=false")
@AutoConfigureMockMvc
class ManagedMediaDeliveryDisabledStorageIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void disabledStorageReturnsSanitizedUnavailableWithoutChangingActiveAvatar() throws Exception {
        String owner = "disabled-delivery-owner-" + UUID.randomUUID();
        mockMvc.perform(get("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isOk());
        UUID accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM identity_account WHERE external_subject = ?",
                UUID.class,
                owner
        );
        ManagedMediaTestFixtures mediaFixtures = new ManagedMediaTestFixtures(jdbcTemplate);
        UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
        mediaFixtures.activateAvatar(accountId, mediaId);
        Map<String, Object> before = mediaFixtures.mediaRow(mediaId);

        String response = mockMvc.perform(post("/api/v1/media/{mediaId}/delivery", mediaId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("media.storage_unavailable"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain((String) before.get("object_key"));
        assertThat(mediaFixtures.currentAvatarId(accountId)).isEqualTo(mediaId);
        assertThat(mediaFixtures.mediaRow(mediaId)).isEqualTo(before);
    }
}
