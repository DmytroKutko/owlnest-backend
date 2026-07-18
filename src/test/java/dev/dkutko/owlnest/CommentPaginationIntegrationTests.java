package dev.dkutko.owlnest;

import com.jayway.jsonpath.JsonPath;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CommentPaginationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void defaultsToTwentyAndAcceptsMaximumOneHundredWithExactPageMetadata() throws Exception {
        String author = uniqueSubject("comment-page-author");
        UUID postId = createPost(author, "Page size target");
        insertSequentialComments(postId, accountIdFor(author), 101);

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(uniqueSubject("default-page-reader")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[0].text").value("Comment 000"))
                .andExpect(jsonPath("$.items[19].text").value("Comment 019"))
                .andExpect(jsonPath("$.page.limit").value(20))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andExpect(jsonPath("$.page.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.links.self").isNotEmpty())
                .andExpect(jsonPath("$.links.next").isNotEmpty())
                .andExpect(jsonPath("$.links.post").value("/api/v1/posts/" + postId));

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .queryParam("limit", "100")
                        .with(jwt().jwt(token -> token.subject(uniqueSubject("maximum-page-reader")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(100)))
                .andExpect(jsonPath("$.items[0].text").value("Comment 000"))
                .andExpect(jsonPath("$.items[99].text").value("Comment 099"))
                .andExpect(jsonPath("$.page.limit").value(100))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andExpect(jsonPath("$.page.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.links.next").isNotEmpty());
    }

    @Test
    void traversesOldestFirstByCreatedAtThenIdWithoutDuplicates() throws Exception {
        String author = uniqueSubject("ordered-comment-author");
        UUID postId = createPost(author, "Ordered comments target");
        UUID authorId = accountIdFor(author);
        Instant sameTimestamp = Instant.parse("2026-07-18T10:00:00Z");
        List<UUID> expectedOrder = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003")
        );
        for (int index = 0; index < expectedOrder.size(); index++) {
            insertComment(expectedOrder.get(index), postId, authorId, "Tie " + index, sameTimestamp);
        }
        reconcileCommentCounter(postId);

        String cursor = null;
        List<UUID> actualOrder = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < expectedOrder.size(); pageIndex++) {
            var request = get("/api/v1/posts/{postId}/comments", postId)
                    .queryParam("limit", "1")
                    .with(jwt().jwt(token -> token.subject(uniqueSubject("ordered-comment-reader"))));
            if (cursor != null) {
                request.queryParam("cursor", cursor);
            }
            MvcResult result = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.page.limit").value(1))
                    .andReturn();
            String response = result.getResponse().getContentAsString();
            actualOrder.add(UUID.fromString(JsonPath.read(response, "$.items[0].id")));
            boolean lastPage = pageIndex == expectedOrder.size() - 1;
            assertThat((Boolean) JsonPath.read(response, "$.page.hasMore")).isEqualTo(!lastPage);
            if (lastPage) {
                assertThat((Object) JsonPath.read(response, "$.page.nextCursor")).isNull();
                assertThat((Object) JsonPath.read(response, "$.links.next")).isNull();
            } else {
                cursor = JsonPath.read(response, "$.page.nextCursor");
                assertThat(cursor).isNotBlank();
                assertThat((String) JsonPath.read(response, "$.links.next"))
                        .startsWith("/api/v1/posts/" + postId + "/comments?");
            }
        }

        assertThat(actualOrder).containsExactlyElementsOf(expectedOrder).doesNotHaveDuplicates();
    }

    @Test
    void rejectsOutOfRangeLimits() throws Exception {
        UUID postId = createPost(uniqueSubject("invalid-limit-post-owner"), "Invalid limit target");
        String reader = uniqueSubject("invalid-limit-reader");

        for (String invalidLimit : new String[]{"0", "-1", "101"}) {
            mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                            .queryParam("limit", invalidLimit)
                            .with(jwt().jwt(token -> token.subject(reader))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("request.validation_failed"));
        }
    }

    @Test
    void rejectsUnknownAndRepeatedCommentListQueryParameters() throws Exception {
        UUID postId = createPost(uniqueSubject("invalid-query-post-owner"), "Invalid query target");
        String reader = uniqueSubject("invalid-query-reader");

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .queryParam("sort", "createdAt")
                        .with(jwt().jwt(token -> token.subject(reader))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .queryParam("limit", "1", "2")
                        .with(jwt().jwt(token -> token.subject(reader))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .queryParam("cursor", "a", "b")
                        .with(jwt().jwt(token -> token.subject(reader))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
    }

    @Test
    void rejectsMalformedOversizedVersionMismatchedAndCrossPostCursors() throws Exception {
        String author = uniqueSubject("cursor-comment-author");
        UUID firstPostId = createPost(author, "First cursor target");
        UUID secondPostId = createPost(author, "Second cursor target");
        UUID authorId = accountIdFor(author);
        insertSequentialComments(firstPostId, authorId, 2);
        insertSequentialComments(secondPostId, authorId, 2);
        String reader = uniqueSubject("cursor-comment-reader");

        MvcResult firstPage = mockMvc.perform(get("/api/v1/posts/{postId}/comments", firstPostId)
                        .queryParam("limit", "1")
                        .with(jwt().jwt(token -> token.subject(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.nextCursor").isNotEmpty())
                .andReturn();
        String validCursor = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.page.nextCursor");
        assertThat(validCursor).startsWith("v1.");
        byte[] decodedCursor = Base64.getUrlDecoder().decode(validCursor.substring("v1.".length()));
        decodedCursor[0] = (byte) (decodedCursor[0] + 1);
        String wrongVersionCursor = "v1."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(decodedCursor);

        for (String invalidCursor : new String[]{"%%%", "A".repeat(4_096), wrongVersionCursor}) {
            assertInvalidCursor(firstPostId, reader, invalidCursor);
        }
        assertInvalidCursor(secondPostId, reader, validCursor);
    }

    @Test
    void keepsPagesStrictlyIsolatedByPost() throws Exception {
        String author = uniqueSubject("isolated-comment-author");
        UUID firstPostId = createPost(author, "First isolated post");
        UUID secondPostId = createPost(author, "Second isolated post");
        UUID authorId = accountIdFor(author);
        insertComment(UUID.randomUUID(), firstPostId, authorId, "Only first post", Instant.parse("2026-01-01T00:00:00Z"));
        insertComment(UUID.randomUUID(), secondPostId, authorId, "Only second post", Instant.parse("2026-01-01T00:00:00Z"));
        reconcileCommentCounter(firstPostId);
        reconcileCommentCounter(secondPostId);

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", firstPostId)
                        .with(jwt().jwt(token -> token.subject(uniqueSubject("isolated-comment-reader")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].postId").value(firstPostId.toString()))
                .andExpect(jsonPath("$.items[0].text").value("Only first post"))
                .andExpect(jsonPath("$.items[?(@.postId == '" + secondPostId + "')]").isEmpty());
    }

    private void assertInvalidCursor(UUID postId, String reader, String cursor) throws Exception {
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .queryParam("limit", "1")
                        .queryParam("cursor", cursor)
                        .with(jwt().jwt(token -> token.subject(reader))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
    }

    private UUID createPost(String subject, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotBlank();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private void insertSequentialComments(UUID postId, UUID authorId, int count) {
        Instant baseTimestamp = Instant.parse("2026-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            insertComment(
                    UUID.randomUUID(),
                    postId,
                    authorId,
                    "Comment %03d".formatted(index),
                    baseTimestamp.plusSeconds(index)
            );
        }
        reconcileCommentCounter(postId);
    }

    private void insertComment(UUID id, UUID postId, UUID authorId, String text, Instant createdAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO post_comment (id, post_id, author_id, text_content, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                id,
                postId,
                authorId,
                text,
                Timestamp.from(createdAt)
        );
    }

    private void reconcileCommentCounter(UUID postId) {
        jdbcTemplate.update(
                """
                        UPDATE post
                        SET comment_count = (
                            SELECT COUNT(*) FROM post_comment comment_record WHERE comment_record.post_id = post.id
                        )
                        WHERE id = ?
                        """,
                postId
        );
    }

    private UUID accountIdFor(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM identity_account WHERE external_subject = ?",
                UUID.class,
                subject
        );
    }

    private String uniqueSubject(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
