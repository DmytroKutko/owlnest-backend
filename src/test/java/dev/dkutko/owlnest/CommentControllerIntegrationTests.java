package dev.dkutko.owlnest;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CommentControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsCommentWithServerOwnedIdentityAuthorTimestampLocationAndLinks() throws Exception {
        String postOwner = uniqueSubject("comment-post-owner");
        UUID postId = createPost(postOwner, "Comment creation target");
        String commenter = uniqueSubject("comment-author");
        completeProfile(commenter, "comment.author", "Comment Author");
        UUID commenterAccountId = accountIdFor(commenter);
        UUID suppliedCommentId = UUID.randomUUID();
        UUID suppliedPostId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(commenter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "%s",
                                  "postId": "%s",
                                  "text": "A server-owned comment",
                                  "author": {
                                    "accountId": "%s",
                                    "nickname": "attacker",
                                    "displayName": "Attacker"
                                  },
                                  "createdAt": "2000-01-01T00:00:00Z",
                                  "links": {"self": "https://attacker.example/comment"},
                                  "unknownField": "ignored"
                                }
                                """.formatted(suppliedCommentId, suppliedPostId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(suppliedCommentId.toString())))
                .andExpect(jsonPath("$.postId").value(postId.toString()))
                .andExpect(jsonPath("$.text").value("A server-owned comment"))
                .andExpect(jsonPath("$.author.accountId").value(commenterAccountId.toString()))
                .andExpect(jsonPath("$.author.nickname").value("comment.author"))
                .andExpect(jsonPath("$.author.displayName").value("Comment Author"))
                .andExpect(jsonPath("$.author.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.birthDate").doesNotExist())
                .andExpect(jsonPath("$.author.gender").doesNotExist())
                .andExpect(jsonPath("$.author.onboardingCompleted").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt")
                        .value(org.hamcrest.Matchers.not("2000-01-01T00:00:00Z")))
                .andExpect(jsonPath("$.unknownField").doesNotExist())
                .andReturn();

        UUID commentId = commentIdFromLocation(result.getResponse().getHeader(HttpHeaders.LOCATION));
        String collection = "/api/v1/posts/" + postId + "/comments";
        String self = collection + "/" + commentId;
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(self);
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(uniqueSubject("comment-reader")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(commentId.toString()))
                .andExpect(jsonPath("$.items[0].postId").value(postId.toString()))
                .andExpect(jsonPath("$.items[0].links.self").value(self))
                .andExpect(jsonPath("$.items[0].links.post").value("/api/v1/posts/" + postId))
                .andExpect(jsonPath("$.items[0].links.collection").value(collection));
    }

    @Test
    void returnsExactPageShapeForActivePostWithoutComments() throws Exception {
        UUID postId = createPost(uniqueSubject("empty-comment-post-owner"), "No comments yet");

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(uniqueSubject("empty-comment-reader")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.limit").value(20))
                .andExpect(jsonPath("$.page.hasMore").value(false))
                .andExpect(jsonPath("$.page.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.links.self").isNotEmpty())
                .andExpect(jsonPath("$.links.next").value(nullValue()))
                .andExpect(jsonPath("$.links.post").value("/api/v1/posts/" + postId));
    }

    @Test
    void preservesUkrainianEmojiHtmlLookingAndInteriorControlTextExactly() throws Exception {
        UUID postId = createPost(uniqueSubject("exact-comment-post-owner"), "Exact text target");
        String commenter = uniqueSubject("exact-comment-author");
        String text = "  Привіт, сово! 🦉 <script>alert('не HTML')</script>\n\t\u0001  ";
        String requestBody = commentRequest(text);

        MvcResult result = createComment(postId, commenter, requestBody);
        UUID commentId = commentIdFromLocation(result.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.text"))
                .isEqualTo(text);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT text_content FROM post_comment WHERE id = ?",
                String.class,
                commentId
        )).isEqualTo(text);
    }

    @Test
    void acceptsExactlyFiveThousandAstralCodePointsAndRejectsFiveThousandOne() throws Exception {
        UUID postId = createPost(uniqueSubject("astral-comment-post-owner"), "Astral text target");
        String commenter = uniqueSubject("astral-comment-author");
        String exactLimitText = "😀".repeat(5_000);

        MvcResult accepted = createComment(
                postId,
                commenter,
                commentRequest(exactLimitText)
        );
        assertThat((String) JsonPath.read(accepted.getResponse().getContentAsString(), "$.text"))
                .isEqualTo(exactLimitText);

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(commenter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentRequest("😀".repeat(5_001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
        assertThat(commentRowCount(postId)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCommentRequests")
    void rejectsInvalidCommentText(String scenario, String requestBody) throws Exception {
        UUID postId = createPost(uniqueSubject("invalid-comment-post-owner-" + scenario), "Validation target");

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(uniqueSubject("invalid-commenter-" + scenario))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
        assertThat(commentRowCount(postId)).isZero();
        assertThat(postCommentCounter(postId)).isZero();
    }

    @Test
    void allowsCrossUserCommentingAndUsesPrivacySafeDefaultsForIncompleteProfile() throws Exception {
        String postOwner = uniqueSubject("cross-user-comment-post-owner");
        completeProfile(postOwner, "comment.post.owner", "Comment Post Owner");
        UUID postId = createPost(postOwner, "Cross-user comments allowed");
        String commenter = uniqueSubject("incomplete-commenter");
        var privateClaimsJwt = jwt().jwt(token -> token
                .subject(commenter)
                .claim("email", "private.commenter@example.com")
                .claim("preferred_username", "private.commenter")
                .claim("given_name", "Private")
                .claim("family_name", "Commenter"));

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .with(privateClaimsJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Cross-user incomplete-profile comment\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.nickname").value(org.hamcrest.Matchers.startsWith("user_")))
                .andExpect(jsonPath("$.author.displayName").value("OwlNest user"))
                .andExpect(jsonPath("$.author.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andReturn();
        assertThat(createResult.getResponse().getContentAsString())
                .doesNotContain("private.commenter@example.com", "private.commenter", "Private", "Commenter");

        String reader = uniqueSubject("cross-user-comment-reader");
        MvcResult listResult = mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].author.nickname")
                        .value(org.hamcrest.Matchers.startsWith("user_")))
                .andExpect(jsonPath("$.items[0].author.displayName").value("OwlNest user"))
                .andExpect(jsonPath("$.items[0].author.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.items[0].author.email").doesNotExist())
                .andReturn();
        assertThat(listResult.getResponse().getContentAsString())
                .doesNotContain("private.commenter@example.com", "private.commenter", "Private", "Commenter");
        assertThat(accountCount(reader)).isZero();
    }

    @Test
    void createsOneRowAndCounterIncrementPerOrdinaryRepeatedPost() throws Exception {
        UUID postId = createPost(uniqueSubject("repeated-comment-post-owner"), "Repeated POST target");
        String commenter = uniqueSubject("repeated-commenter");

        MvcResult first = createComment(postId, commenter, "{\"text\":\"Repeated text\"}");
        MvcResult second = createComment(postId, commenter, "{\"text\":\"Repeated text\"}");

        assertThat(commentIdFromLocation(first.getResponse().getHeader(HttpHeaders.LOCATION)))
                .isNotEqualTo(commentIdFromLocation(second.getResponse().getHeader(HttpHeaders.LOCATION)));
        assertThat(commentRowCount(postId)).isEqualTo(2);
        assertThat(postCommentCounter(postId)).isEqualTo(2L);
    }

    @Test
    void exposesRealCommentCounterAndPreservesItAcrossFullPostReplacement() throws Exception {
        String postOwner = uniqueSubject("comment-counter-post-owner");
        UUID postId = createPost(postOwner, "Counter target");
        createComment(postId, uniqueSubject("comment-counter-author"), "{\"text\":\"Count me\"}");

        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .with(jwt().jwt(token -> token.subject(uniqueSubject("comment-counter-reader")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counters.comments").value(1));

        mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                        .with(jwt().jwt(token -> token.subject(postOwner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Counter-preserving replacement",
                                  "counters": {"comments": 99}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Counter-preserving replacement"))
                .andExpect(jsonPath("$.counters.comments").value(1));
        assertThat(postCommentCounter(postId)).isEqualTo(1L);
    }

    @Test
    void returnsNotFoundForMissingAndSoftDeletedPostWithoutCreatingComments() throws Exception {
        String commenter = uniqueSubject("missing-commenter");
        UUID missingPostId = UUID.randomUUID();
        assertCommentCreateAndListNotFound(missingPostId, commenter);

        String postOwner = uniqueSubject("deleted-comment-post-owner");
        UUID deletedPostId = createPost(postOwner, "Deleted comments target");
        mockMvc.perform(delete("/api/v1/posts/{postId}", deletedPostId)
                        .with(jwt().jwt(token -> token.subject(postOwner))))
                .andExpect(status().isNoContent());
        assertCommentCreateAndListNotFound(deletedPostId, commenter);
        assertThat(commentRowCount(deletedPostId)).isZero();
    }

    @Test
    void requiresAuthenticationAndDoesNotExposeCommentMutationOrReplyRoutes() throws Exception {
        UUID postId = createPost(uniqueSubject("comment-route-post-owner"), "Route target");
        UUID commentId = commentIdFromLocation(createComment(
                postId,
                uniqueSubject("comment-route-author"),
                "{\"text\":\"Immutable comment\"}"
        ).getResponse().getHeader(HttpHeaders.LOCATION));

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Unauthenticated\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId))
                .andExpect(status().isUnauthorized());

        var authenticated = jwt().jwt(token -> token.subject(uniqueSubject("comment-route-viewer")));
        mockMvc.perform(put("/api/v1/posts/{postId}/comments/{commentId}", postId, commentId)
                        .with(authenticated)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Edited\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}", postId, commentId)
                        .with(authenticated))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                                "/api/v1/posts/{postId}/comments/{commentId}/replies",
                                postId,
                                commentId
                        )
                        .with(authenticated)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Reply\"}"))
                .andExpect(status().isNotFound());
        assertThat(commentRowCount(postId)).isEqualTo(1);
    }

    private static Stream<Arguments> invalidCommentRequests() {
        return Stream.of(
                arguments("missing-text", "{}"),
                arguments("null-text", "{\"text\":null}"),
                arguments("empty-text", "{\"text\":\"\"}"),
                arguments("whitespace-only-text", "{\"text\":\" \\t\\n\\r \"}"),
                arguments("control-only-text", "{\"text\":\"\\u0001\\u001f\"}"),
                arguments("embedded-nul", "{\"text\":\"before\\u0000after\"}"),
                arguments("unpaired-high-surrogate", "{\"text\":\"before\\uD800after\"}"),
                arguments("unpaired-low-surrogate", "{\"text\":\"before\\uDC00after\"}")
        );
    }

    private MvcResult createComment(UUID postId, String subject, String requestBody) throws Exception {
        return mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn();
    }

    private UUID createPost(String subject, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":" + jsonString(description) + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return commentIdFromLocation(result.getResponse().getHeader(HttpHeaders.LOCATION));
    }

    private void completeProfile(String subject, String nickname, String displayName) throws Exception {
        mockMvc.perform(put("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":" + jsonString(nickname)
                                + ",\"displayName\":" + jsonString(displayName) + "}"))
                .andExpect(status().isOk());
    }

    private UUID accountIdFor(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM identity_account WHERE external_subject = ?",
                UUID.class,
                subject
        );
    }

    private int accountCount(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_account WHERE external_subject = ?",
                Integer.class,
                subject
        );
    }

    private int commentRowCount(UUID postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_comment WHERE post_id = ?",
                Integer.class,
                postId
        );
    }

    private long postCommentCounter(UUID postId) {
        return jdbcTemplate.queryForObject(
                "SELECT comment_count FROM post WHERE id = ?",
                Long.class,
                postId
        );
    }

    private UUID commentIdFromLocation(String location) {
        assertThat(location).isNotBlank();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private void assertCommentCreateAndListNotFound(UUID postId, String commenter) throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(commenter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Cannot attach\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("post.not_found"));
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(commenter))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("post.not_found"));
    }

    private String commentRequest(String text) {
        return "{\"text\":" + jsonString(text) + "}";
    }

    private String jsonString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append("\\u%04x".formatted((int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private String uniqueSubject(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
