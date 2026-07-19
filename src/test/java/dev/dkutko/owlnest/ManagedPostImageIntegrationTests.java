package dev.dkutko.owlnest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, RecordingMediaTestStorageConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
class ManagedPostImageIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingMediaTestStorage storage;

    private ManagedMediaTestFixtures mediaFixtures;

    @BeforeEach
    void setUp() {
        mediaFixtures = new ManagedMediaTestFixtures(jdbcTemplate);
        storage.reset();
    }

    @Test
    void attachesDeliversPreservesOnIdempotentReplaceAndDetachesManagedImage() throws Exception {
        String owner = "managed-post-owner-" + UUID.randomUUID();
        provision(owner);
        UUID mediaId = mediaFixtures.insertReadyPostImage(accountIdFor(owner));

        MvcResult created = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("created", mediaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media[0].type").value("IMAGE"))
                .andExpect(jsonPath("$.media[0].url").value(nullValue()))
                .andExpect(jsonPath("$.media[0].managed.mediaId").value(mediaId.toString()))
                .andExpect(jsonPath("$.media[0].managed.deliveryUrl")
                        .value("/api/v1/media/" + mediaId + "/delivery"))
                .andReturn();
        UUID postId = locationId(created);
        assertThat(mediaFixtures.mediaRow(mediaId)).containsEntry("status", "ACTIVE");

        mockMvc.perform(post("/api/v1/media/{mediaId}/delivery", mediaId)
                        .with(jwt().jwt(token -> token.subject("managed-post-reader-" + UUID.randomUUID()))))
                .andExpect(status().isOk());
        assertThat(storage.readCalls()).hasSize(1);

        mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("same", mediaId)))
                .andExpect(status().isOk());
        assertThat(mediaFixtures.mediaRow(mediaId)).containsEntry("status", "ACTIVE");

        mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"detached\"}"))
                .andExpect(status().isOk());
        assertThat(mediaFixtures.mediaRow(mediaId))
                .containsEntry("status", "DELETION_PENDING")
                .containsEntry("deletion_reason", "DETACHED");
        mockMvc.perform(post("/api/v1/media/{mediaId}/delivery", mediaId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsForeignAndWrongPurposeWithoutPersistingPostOrChangingMedia() throws Exception {
        String owner = "managed-post-source-owner-" + UUID.randomUUID();
        String actor = "managed-post-foreign-" + UUID.randomUUID();
        provision(owner);
        provision(actor);
        UUID foreignImage = mediaFixtures.insertReadyPostImage(accountIdFor(owner));
        UUID avatar = mediaFixtures.insertReadyAvatar(accountIdFor(actor));
        int before = postCount();

        mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(actor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("foreign", foreignImage)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media.not_found"));
        mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(actor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("wrong purpose", avatar)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("media.purpose_mismatch"));

        assertThat(postCount()).isEqualTo(before);
        assertThat(mediaFixtures.mediaRow(foreignImage)).containsEntry("status", "READY");
        assertThat(mediaFixtures.mediaRow(avatar)).containsEntry("status", "READY");
    }

    @Test
    void softDeleteDetachesImageAndBlocksDelivery() throws Exception {
        String owner = "managed-post-delete-" + UUID.randomUUID();
        provision(owner);
        UUID mediaId = mediaFixtures.insertReadyPostImage(accountIdFor(owner));
        MvcResult created = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("delete", mediaId)))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(delete("/api/v1/posts/{postId}", locationId(created))
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isNoContent());
        assertThat(mediaFixtures.mediaRow(mediaId))
                .containsEntry("status", "DELETION_PENDING")
                .containsEntry("deletion_reason", "DETACHED");
        mockMvc.perform(post("/api/v1/media/{mediaId}/delivery", mediaId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isNotFound());
    }

    private void provision(String subject) throws Exception {
        mockMvc.perform(get("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isOk());
    }

    private UUID accountIdFor(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM identity_account WHERE external_subject = ?",
                UUID.class,
                subject
        );
    }

    private int postCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class);
    }

    private static UUID locationId(MvcResult result) {
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private static String postBody(String description, UUID mediaId) {
        return """
                {
                  "description": "%s",
                  "media": [{"type": "IMAGE", "mediaId": "%s"}]
                }
                """.formatted(description, mediaId);
    }
}
