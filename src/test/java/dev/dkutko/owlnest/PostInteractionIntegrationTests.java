package dev.dkutko.owlnest;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PostInteractionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void setsAndClearsLikeIdempotentlyWithOneRelationAndConsistentCounter() throws Exception {
        UUID postId = createPost(uniqueSubject("like-owner"));
        String actor = uniqueSubject("like-actor");

        putInteraction(postId, "likes", actor);
        putInteraction(postId, "likes", actor);

        UUID actorAccountId = accountIdFor(actor);
        assertThat(interactionCount("post_like", postId, actorAccountId)).isEqualTo(1);
        assertThat(postCounter("like_count", postId)).isEqualTo(1L);

        deleteInteraction(postId, "likes", actor);
        deleteInteraction(postId, "likes", actor);

        assertThat(interactionCount("post_like", postId, actorAccountId)).isZero();
        assertThat(postCounter("like_count", postId)).isZero();
    }

    @Test
    void setsAndClearsBookmarkIdempotentlyWithOnePrivateRelation() throws Exception {
        UUID postId = createPost(uniqueSubject("bookmark-owner"));
        String actor = uniqueSubject("bookmark-actor");

        putInteraction(postId, "bookmark", actor);
        putInteraction(postId, "bookmark", actor);

        UUID actorAccountId = accountIdFor(actor);
        assertThat(interactionCount("post_bookmark", postId, actorAccountId)).isEqualTo(1);

        deleteInteraction(postId, "bookmark", actor);
        deleteInteraction(postId, "bookmark", actor);

        assertThat(interactionCount("post_bookmark", postId, actorAccountId)).isZero();
    }

    @Test
    void setsAndClearsRepostIdempotentlyWithOneRelationAndConsistentCounter() throws Exception {
        UUID postId = createPost(uniqueSubject("repost-owner"));
        String actor = uniqueSubject("repost-actor");

        putInteraction(postId, "repost", actor);
        putInteraction(postId, "repost", actor);

        UUID actorAccountId = accountIdFor(actor);
        assertThat(interactionCount("post_repost", postId, actorAccountId)).isEqualTo(1);
        assertThat(postCounter("repost_count", postId)).isEqualTo(1L);

        deleteInteraction(postId, "repost", actor);
        deleteInteraction(postId, "repost", actor);

        assertThat(interactionCount("post_repost", postId, actorAccountId)).isZero();
        assertThat(postCounter("repost_count", postId)).isZero();
    }

    @Test
    void isolatesViewerStateWhileKeepingPublicCountersShared() throws Exception {
        UUID postId = createPost(uniqueSubject("viewer-state-owner"));
        String interactingViewer = uniqueSubject("interacting-viewer");
        String otherViewer = uniqueSubject("other-viewer");

        putInteraction(postId, "likes", interactingViewer);
        putInteraction(postId, "bookmark", interactingViewer);
        putInteraction(postId, "repost", interactingViewer);

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(interactingViewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counters.likes").value(1))
                .andExpect(jsonPath("$.counters.reposts").value(1))
                .andExpect(jsonPath("$.counters.bookmarks").doesNotExist())
                .andExpect(jsonPath("$.viewerState.liked").value(true))
                .andExpect(jsonPath("$.viewerState.bookmarked").value(true))
                .andExpect(jsonPath("$.viewerState.reposted").value(true));

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(otherViewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counters.likes").value(1))
                .andExpect(jsonPath("$.counters.reposts").value(1))
                .andExpect(jsonPath("$.viewerState.liked").value(false))
                .andExpect(jsonPath("$.viewerState.bookmarked").value(false))
                .andExpect(jsonPath("$.viewerState.reposted").value(false));
    }

    @Test
    void rejectsInteractionsForMissingPostWithStableNotFoundCode() throws Exception {
        UUID missingPostId = UUID.randomUUID();
        String actor = uniqueSubject("missing-interaction-actor");

        assertAllInteractionsNotFound(missingPostId, actor);
    }

    @Test
    void rejectsInteractionsForDeletedPostWithStableNotFoundCode() throws Exception {
        String owner = uniqueSubject("deleted-interaction-owner");
        UUID postId = createPost(owner);
        mockMvc.perform(delete("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isNoContent());

        assertAllInteractionsNotFound(postId, uniqueSubject("deleted-interaction-actor"));
    }

    @Test
    void requiresAuthenticationForEveryInteractionWrite() throws Exception {
        UUID postId = UUID.randomUUID();

        for (String interaction : new String[]{"likes", "bookmark", "repost"}) {
            mockMvc.perform(put("/api/v1/posts/{id}/{interaction}", postId, interaction))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/v1/posts/{id}/{interaction}", postId, interaction))
                    .andExpect(status().isUnauthorized());
        }
    }

    private void assertAllInteractionsNotFound(UUID postId, String actor) throws Exception {
        for (String interaction : new String[]{"likes", "bookmark", "repost"}) {
            mockMvc.perform(put("/api/v1/posts/{id}/{interaction}", postId, interaction)
                            .with(jwt().jwt(token -> token.subject(actor))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("post.not_found"));
            mockMvc.perform(delete("/api/v1/posts/{id}/{interaction}", postId, interaction)
                            .with(jwt().jwt(token -> token.subject(actor))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("post.not_found"));
        }
    }

    private void putInteraction(UUID postId, String interaction, String subject) throws Exception {
        mockMvc.perform(put("/api/v1/posts/{id}/{interaction}", postId, interaction)
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private void deleteInteraction(UUID postId, String interaction, String subject) throws Exception {
        mockMvc.perform(delete("/api/v1/posts/{id}/{interaction}", postId, interaction)
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private UUID createPost(String subject) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Interaction target\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotBlank();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private UUID accountIdFor(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM identity_account WHERE external_subject = ?",
                UUID.class,
                subject
        );
    }

    private int interactionCount(String table, UUID postId, UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE post_id = ? AND account_id = ?",
                Integer.class,
                postId,
                accountId
        );
    }

    private long postCounter(String column, UUID postId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM post WHERE id = ?",
                Long.class,
                postId
        );
    }

    private String uniqueSubject(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
