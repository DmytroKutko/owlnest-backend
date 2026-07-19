package dev.dkutko.owlnest;

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
import java.util.stream.IntStream;
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
class PostControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsPostWithOrderedContentAndSafeServerOwnedCard() throws Exception {
        String subject = "post-create-owner";
        completeProfile(subject, "post.creator", "Post Creator");
        UUID accountId = accountIdFor(subject);
        UUID suppliedId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token
                                .subject(subject)
                                .claim("email", "post.creator@owlnest.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "%s",
                                  "title": "A complete post",
                                  "description": "The persisted description",
                                  "postType": "PERSONAL",
                                  "labels": ["  Spring  ", "PostgreSQL", "API"],
                                  "media": [
                                    {"type": "IMAGE", "url": "https://cdn.example.com/first.png"},
                                    {"type": "VIDEO", "url": "https://cdn.example.com/second.mp4"}
                                  ],
                                  "author": {"accountId": "%s", "nickname": "attacker"},
                                  "counters": {"likes": 99, "comments": 99, "reposts": 99},
                                  "viewerState": {"liked": true, "bookmarked": true, "reposted": true},
                                  "timestamps": {"createdAt": "2000-01-01T00:00:00Z"},
                                  "links": {"self": "https://attacker.example/post"},
                                  "unknownField": "ignored"
                                }
                                """.formatted(suppliedId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(suppliedId.toString())))
                .andExpect(jsonPath("$.title").value("A complete post"))
                .andExpect(jsonPath("$.description").value("The persisted description"))
                .andExpect(jsonPath("$.postType").value("COMMUNITY"))
                .andExpect(jsonPath("$.labels[0]").value("Spring"))
                .andExpect(jsonPath("$.labels[1]").value("PostgreSQL"))
                .andExpect(jsonPath("$.labels[2]").value("API"))
                .andExpect(jsonPath("$.media[0].type").value("IMAGE"))
                .andExpect(jsonPath("$.media[0].url").value("https://cdn.example.com/first.png"))
                .andExpect(jsonPath("$.media[1].type").value("VIDEO"))
                .andExpect(jsonPath("$.media[1].url").value("https://cdn.example.com/second.mp4"))
                .andExpect(jsonPath("$.author.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.author.nickname").value("post.creator"))
                .andExpect(jsonPath("$.author.displayName").value("Post Creator"))
                .andExpect(jsonPath("$.author.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.birthDate").doesNotExist())
                .andExpect(jsonPath("$.counters.likes").value(0))
                .andExpect(jsonPath("$.counters.comments").value(0))
                .andExpect(jsonPath("$.counters.reposts").value(0))
                .andExpect(jsonPath("$.counters.views").doesNotExist())
                .andExpect(jsonPath("$.viewerState.liked").value(false))
                .andExpect(jsonPath("$.viewerState.bookmarked").value(false))
                .andExpect(jsonPath("$.viewerState.reposted").value(false))
                .andExpect(jsonPath("$.viewerState.isAuthor").value(true))
                .andExpect(jsonPath("$.viewerState.canEdit").value(true))
                .andExpect(jsonPath("$.viewerState.canDelete").value(true))
                .andExpect(jsonPath("$.timestamps.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.timestamps.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.body").doesNotExist())
                .andExpect(jsonPath("$.unknownField").doesNotExist())
                .andReturn();

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        UUID postId = postIdFromLocation(location);
        assertThat(location).isEqualTo("/api/v1/posts/" + postId);
        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links.self").value(location))
                .andExpect(jsonPath("$.links.comments").value(location + "#comments"));
    }

    @Test
    void firstUsePostAuthorIgnoresPrivateJwtProfileClaimsBeforeOnboarding() throws Exception {
        String subject = "private-claims-post-owner-" + UUID.randomUUID();
        var privateClaimsJwt = jwt().jwt(token -> token
                .subject(subject)
                .claim("email", "private.owner@example.com")
                .claim("email_verified", true)
                .claim("preferred_username", "private.handle")
                .claim("given_name", "Private")
                .claim("family_name", "Owner"));

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
                        .with(privateClaimsJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Privacy-safe first post\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postType").value("PERSONAL"))
                .andExpect(jsonPath("$.author.nickname").value(org.hamcrest.Matchers.startsWith("user_")))
                .andExpect(jsonPath("$.author.displayName").value("OwlNest user"))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.preferredUsername").doesNotExist())
                .andExpect(jsonPath("$.author.givenName").doesNotExist())
                .andExpect(jsonPath("$.author.familyName").doesNotExist())
                .andReturn();

        assertThat(createResult.getResponse().getContentAsString())
                .doesNotContain("private.owner@example.com", "private.handle", "Private", "Owner");
        UUID postId = postIdFromLocation(createResult.getResponse().getHeader(HttpHeaders.LOCATION));

        MvcResult readResult = mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject("privacy-safe-reader-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.nickname").value(org.hamcrest.Matchers.startsWith("user_")))
                .andExpect(jsonPath("$.author.displayName").value("OwlNest user"))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andReturn();
        assertThat(readResult.getResponse().getContentAsString())
                .doesNotContain("private.owner@example.com", "private.handle", "Private", "Owner");
    }

    @Test
    void derivesPersonalPostFromExternalEmailAndNormalizesBlankTitle() throws Exception {
        String subject = "post-default-owner";

        mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token
                                .subject(subject)
                                .claim("email", "post.owner@gmail.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   ",
                                  "description": "Minimal valid post"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(nullValue()))
                .andExpect(jsonPath("$.postType").value("PERSONAL"))
                .andExpect(jsonPath("$.labels").isArray())
                .andExpect(jsonPath("$.labels").isEmpty())
                .andExpect(jsonPath("$.media").isArray())
                .andExpect(jsonPath("$.media").isEmpty());
    }

    @Test
    void normalizesControlAndUnicodeWhitespaceOnlyTitlesOnCreateAndReplace() throws Exception {
        String owner = "blank-normalization-owner-" + UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\\u0001\\u001f\\t\",\"description\":\"Control-only title\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(nullValue()))
                .andReturn();
        UUID postId = postIdFromLocation(createResult.getResponse().getHeader(HttpHeaders.LOCATION));

        mockMvc.perform(put("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\\u2003\\u2003\",\"description\":\"EM SPACE-only title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(nullValue()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM post WHERE id = ?",
                String.class,
                postId
        )).isNull();
    }

    @Test
    void normalizesUnicodeAndControlPaddedBoundaryFieldsOnCreateAndReplace() throws Exception {
        String owner = "unicode-boundary-owner-" + UUID.randomUUID();
        String createdTitle = "t".repeat(200);
        String createdLabel = "l".repeat(50);

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "\\u0001\\u2003%s\\u2003\\u001f",
                                  "description": "Unicode/control padded create",
                                  "labels": ["\\u0001\\u2003%s\\u2003\\u001f"]
                                }
                                """.formatted(createdTitle, createdLabel)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(createdTitle))
                .andExpect(jsonPath("$.labels[0]").value(createdLabel))
                .andReturn();
        UUID postId = postIdFromLocation(createResult.getResponse().getHeader(HttpHeaders.LOCATION));
        assertPersistedTitleAndLabel(postId, createdTitle, createdLabel);

        String replacedTitle = "r".repeat(200);
        String replacedLabel = "b".repeat(50);
        mockMvc.perform(put("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "\\u001e\\u2003%s\\u2003\\u0002",
                                  "description": "Unicode/control padded replacement",
                                  "labels": ["\\u001e\\u2003%s\\u2003\\u0002"]
                                }
                                """.formatted(replacedTitle, replacedLabel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(replacedTitle))
                .andExpect(jsonPath("$.labels[0]").value(replacedLabel));
        assertPersistedTitleAndLabel(postId, replacedTitle, replacedLabel);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("additionalJavaSpaceCharacters")
    void normalizesAdditionalJavaSpaceCharacterBoundariesAndRejectsWhitespaceOnlyFields(
            String scenario,
            String spaceCharacter
    ) throws Exception {
        String owner = "additional-space-owner-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%sCanonical title%s",
                                  "description": "Valid description",
                                  "labels": ["%sCanonical label%s"]
                                }
                                """.formatted(
                                spaceCharacter,
                                spaceCharacter,
                                spaceCharacter,
                                spaceCharacter
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Canonical title"))
                .andExpect(jsonPath("$.labels[0]").value("Canonical label"))
                .andReturn();

        UUID postId = postIdFromLocation(result.getResponse().getHeader(HttpHeaders.LOCATION));
        assertPersistedTitleAndLabel(postId, "Canonical title", "Canonical label");

        mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + spaceCharacter.repeat(2) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));

        mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Valid description\",\"labels\":[\""
                                + spaceCharacter.repeat(2) + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
    }

    @Test
    void acceptsAbsoluteHttpsMediaVariantsAndPreservesTheirTextOnCardRead() throws Exception {
        String owner = "valid-media-owner-" + UUID.randomUUID();
        String structuredUrl = "https://cdn.example.com/assets/image-1.png?size=large&mode=fit#preview";
        String unicodeUrl = "https://cdn.example.com/медіа/сова.png?розмір=великий#перегляд";
        String ipv6Url = "https://[2001:db8::1]/image.png";
        String usernameUrl = "https://reader@cdn.example.com/image.png";
        String usernameAndPasswordUrl = "https://reader:secret@cdn.example.com/video.mp4";
        String mediaUrlPrefix = "https://cdn.example.com/";
        String exactCodePointLimitUrl = mediaUrlPrefix + "😀".repeat(
                2_048 - mediaUrlPrefix.codePointCount(0, mediaUrlPrefix.length())
        );

        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Supported media URLs",
                                  "media": [
                                    {"type": "IMAGE", "url": "%s"},
                                    {"type": "VIDEO", "url": "%s"},
                                    {"type": "IMAGE", "url": "%s"},
                                    {"type": "IMAGE", "url": "%s"},
                                    {"type": "IMAGE", "url": "%s"},
                                    {"type": "IMAGE", "url": "%s"}
                                  ]
                                }
                                """.formatted(
                                structuredUrl,
                                unicodeUrl,
                                ipv6Url,
                                usernameUrl,
                                usernameAndPasswordUrl,
                                exactCodePointLimitUrl
                        )))
                .andExpect(status().isCreated())
                .andReturn();
        UUID postId = postIdFromLocation(result.getResponse().getHeader(HttpHeaders.LOCATION));

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].url").value(structuredUrl))
                .andExpect(jsonPath("$.media[1].url").value(unicodeUrl))
                .andExpect(jsonPath("$.media[2].url").value(ipv6Url))
                .andExpect(jsonPath("$.media[3].url").value(usernameUrl))
                .andExpect(jsonPath("$.media[4].url").value(usernameAndPasswordUrl))
                .andExpect(jsonPath("$.media[5].url").value(exactCodePointLimitUrl));
    }

    @Test
    void readsActivePostWithViewerSpecificPermissions() throws Exception {
        String owner = "post-read-owner";
        UUID postId = createPost(owner, "Readable post");

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject("post-read-viewer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.description").value("Readable post"))
                .andExpect(jsonPath("$.viewerState.isAuthor").value(false))
                .andExpect(jsonPath("$.viewerState.canEdit").value(false))
                .andExpect(jsonPath("$.viewerState.canDelete").value(false));
    }

    @Test
    void acceptsBoundarySizedPostContent() throws Exception {
        String labels = IntStream.range(0, 5)
                .mapToObj(index -> "\"" + ("l".repeat(49) + index) + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String media = IntStream.range(0, 10)
                .mapToObj(index -> "{\"type\":\"IMAGE\",\"url\":\"https://cdn.example.com/" + index + ".png\"}")
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject("post-boundary-owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "%s",
                                  "labels": [%s],
                                  "media": [%s]
                                }
                                """.formatted("t".repeat(200), "d".repeat(20_000), labels, media)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("t".repeat(200)))
                .andExpect(jsonPath("$.description").value("d".repeat(20_000)))
                .andExpect(jsonPath("$.labels", hasSize(5)))
                .andExpect(jsonPath("$.media", hasSize(10)));
    }

    @Test
    void acceptsAstralTextAtUnicodeCodePointLimits() throws Exception {
        String astralCharacter = "\uD83D\uDE00";
        String title = astralCharacter.repeat(200);
        String description = astralCharacter.repeat(20_000);
        String label = astralCharacter.repeat(50);

        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject("astral-boundary-" + UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "%s",
                                  "labels": ["%s"]
                                }
                                """.formatted(title, description, label)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID postId = postIdFromLocation(result.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CHAR_LENGTH(title) FROM post WHERE id = ?",
                Integer.class,
                postId
        )).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CHAR_LENGTH(description) FROM post WHERE id = ?",
                Integer.class,
                postId
        )).isEqualTo(20_000);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CHAR_LENGTH(label) FROM post_label WHERE post_id = ? AND position = 0",
                Integer.class,
                postId
        )).isEqualTo(50);
    }

    @Test
    void rejectsAstralTextAboveUnicodeCodePointLimits() throws Exception {
        String astralCharacter = "\uD83D\uDE00";
        String subjectPrefix = "astral-over-limit-" + UUID.randomUUID();

        assertInvalidCreate(
                subjectPrefix + "-title",
                "{\"title\":\"" + astralCharacter.repeat(201) + "\",\"description\":\"valid\"}"
        );
        assertInvalidCreate(
                subjectPrefix + "-description",
                "{\"description\":\"" + astralCharacter.repeat(20_001) + "\"}"
        );
        assertInvalidCreate(
                subjectPrefix + "-label",
                "{\"description\":\"valid\",\"labels\":[\"" + astralCharacter.repeat(51) + "\"]}"
        );
    }

    @Test
    void preservesStorableInteriorControlCharacters() throws Exception {
        String title = "before\u0001after";
        String description = "before\u001Fafter";
        String label = "before\u0002after";

        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject("interior-controls-" + UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "before\\u0001after",
                                  "description": "before\\u001Fafter",
                                  "labels": ["before\\u0002after"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.description").value(description))
                .andExpect(jsonPath("$.labels[0]").value(label))
                .andReturn();

        UUID postId = postIdFromLocation(result.getResponse().getHeader(HttpHeaders.LOCATION));
        assertPersistedTitleAndLabel(postId, title, label);
    }

    @Test
    void createsPaddedBoundaryTitleAndLabelAfterNormalizingBeforeLengthValidation() throws Exception {
        String normalizedTitle = "t".repeat(200);
        String normalizedLabel = "l".repeat(50);

        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject("padded-create-" + UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  %s  ",
                                  "description": "Padded create boundaries",
                                  "labels": ["  %s  "]
                                }
                                """.formatted(normalizedTitle, normalizedLabel)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(normalizedTitle))
                .andExpect(jsonPath("$.labels[0]").value(normalizedLabel))
                .andReturn();

        UUID postId = postIdFromLocation(result.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM post WHERE id = ?",
                String.class,
                postId
        )).isEqualTo(normalizedTitle);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT label FROM post_label WHERE post_id = ? AND position = 0",
                String.class,
                postId
        )).isEqualTo(normalizedLabel);
    }

    @Test
    void replacesWithPaddedBoundaryTitleAndLabelAfterNormalizingBeforeLengthValidation() throws Exception {
        String owner = "padded-replace-" + UUID.randomUUID();
        UUID postId = createPost(owner, "Original padded-boundary target");
        String normalizedTitle = "r".repeat(200);
        String normalizedLabel = "b".repeat(50);

        mockMvc.perform(put("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  %s  ",
                                  "description": "Padded replacement boundaries",
                                  "labels": ["  %s  "]
                                }
                                """.formatted(normalizedTitle, normalizedLabel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(normalizedTitle))
                .andExpect(jsonPath("$.labels[0]").value(normalizedLabel));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM post WHERE id = ?",
                String.class,
                postId
        )).isEqualTo(normalizedTitle);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT label FROM post_label WHERE post_id = ? AND position = 0",
                String.class,
                postId
        )).isEqualTo(normalizedLabel);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPostRequests")
    void rejectsInvalidPostContent(String scenario, String requestBody) throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject("invalid-post-" + scenario)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
    }

    @Test
    void fullyReplacesOwnedPostWithoutChangingEmailDerivedType() throws Exception {
        String owner = "post-replace-owner";
        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token
                                .subject(owner)
                                .claim("email", "post.replace@owlnest.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Original description\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postType").value("COMMUNITY"))
                .andReturn();
        UUID postId = postIdFromLocation(createResult.getResponse().getHeader(HttpHeaders.LOCATION));

        mockMvc.perform(put("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token
                                .subject(owner)
                                .claim("email", "post.replace@gmail.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Replacement description",
                                  "postType": "PERSONAL",
                                  "labels": [" replacement "],
                                  "media": [{"type": "VIDEO", "url": "https://cdn.example.com/replacement.mp4"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(nullValue()))
                .andExpect(jsonPath("$.description").value("Replacement description"))
                .andExpect(jsonPath("$.postType").value("COMMUNITY"))
                .andExpect(jsonPath("$.labels[0]").value("replacement"))
                .andExpect(jsonPath("$.media[0].url").value("https://cdn.example.com/replacement.mp4"))
                .andExpect(jsonPath("$.timestamps.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject("post-replace-reader"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Replacement description"));
    }

    @Test
    void rejectsWhitespaceOnlyReplacementLabelsWithoutPartiallyReplacingPost() throws Exception {
        String owner = "invalid-replacement-owner-" + UUID.randomUUID();
        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Original title",
                                  "description": "Original description",
                                  "labels": ["original"],
                                  "media": [{"type":"IMAGE","url":"https://cdn.example.com/original.png"}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID postId = postIdFromLocation(createResult.getResponse().getHeader(HttpHeaders.LOCATION));

        for (String invalidLabel : new String[]{"\\u0000\\u001f", "\\u2003\\u2003"}) {
            mockMvc.perform(put("/api/v1/posts/{id}", postId)
                            .with(jwt().jwt(token -> token.subject(owner)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "Changed title",
                                      "description": "Changed description",
                                      "labels": ["%s"],
                                      "media": []
                                    }
                                    """.formatted(invalidLabel)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("request.validation_failed"));
        }

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original title"))
                .andExpect(jsonPath("$.description").value("Original description"))
                .andExpect(jsonPath("$.postType").value("PERSONAL"))
                .andExpect(jsonPath("$.labels[0]").value("original"))
                .andExpect(jsonPath("$.media[0].url").value("https://cdn.example.com/original.png"));
    }

    @Test
    void rejectsEmbeddedNulOnCreateAndReplaceWithoutPartialWrites() throws Exception {
        String owner = "nul-atomicity-owner-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Rejected\",\"description\":\"before\\u0000after\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
        assertThat(countPostsForSubject(owner)).isZero();

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Original title",
                                  "description": "Original description",
                                  "labels": ["original"],
                                  "media": [{"type":"IMAGE","url":"https://cdn.example.com/original.png"}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID postId = postIdFromLocation(createResult.getResponse().getHeader(HttpHeaders.LOCATION));

        for (String invalidReplacement : new String[]{
                "{\"title\":\"changed\\u0000title\",\"description\":\"Changed description\"}",
                "{\"title\":\"Changed title\",\"description\":\"changed\\u0000description\"}",
                "{\"title\":\"Changed title\",\"description\":\"Changed description\","
                        + "\"labels\":[\"changed\\u0000label\"]}"
        }) {
            mockMvc.perform(put("/api/v1/posts/{id}", postId)
                            .with(jwt().jwt(token -> token.subject(owner)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidReplacement))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("request.validation_failed"));
        }

        assertOriginalPostContent(postId, owner);
    }

    @Test
    void acceptsAndPreservesDuplicateOrderedLabelsOnCreateReplaceAndRead() throws Exception {
        String owner = "duplicate-label-owner-" + UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Duplicate labels",
                                  "description": "Created with ordered duplicate labels",
                                  "labels": [" Spring ", "spring", "Spring", "Spring"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.labels[0]").value("Spring"))
                .andExpect(jsonPath("$.labels[1]").value("spring"))
                .andExpect(jsonPath("$.labels[2]").value("Spring"))
                .andExpect(jsonPath("$.labels[3]").value("Spring"))
                .andReturn();
        UUID postId = postIdFromLocation(createResult.getResponse().getHeader(HttpHeaders.LOCATION));

        mockMvc.perform(put("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Replaced with ordered duplicate labels",
                                  "labels": ["İ", "i", "İ", "i"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels[0]").value("İ"))
                .andExpect(jsonPath("$.labels[1]").value("i"))
                .andExpect(jsonPath("$.labels[2]").value("İ"))
                .andExpect(jsonPath("$.labels[3]").value("i"));

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject("duplicate-label-reader-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels[0]").value("İ"))
                .andExpect(jsonPath("$.labels[1]").value("i"))
                .andExpect(jsonPath("$.labels[2]").value("İ"))
                .andExpect(jsonPath("$.labels[3]").value("i"));
    }

    @Test
    void deniesForeignReplaceAndDeleteWithoutChangingPost() throws Exception {
        String owner = "post-foreign-owner";
        UUID postId = createPost(owner, "Owner data remains");
        var foreign = jwt().jwt(token -> token.subject("post-foreign-actor"));

        mockMvc.perform(put("/api/v1/posts/{id}", postId)
                        .with(foreign)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Foreign replacement\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("post.access_denied"));

        mockMvc.perform(delete("/api/v1/posts/{id}", postId).with(foreign))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("post.access_denied"));

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Owner data remains"));
    }

    @Test
    void softDeletesOwnedPostAndHidesItAsNotFound() throws Exception {
        String owner = "post-delete-owner";
        UUID postId = createPost(owner, "Soft delete target");

        mockMvc.perform(delete("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("post.not_found"));
        mockMvc.perform(delete("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("post.not_found"));

        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM post WHERE id = ?",
                Boolean.class,
                postId
        );
        assertThat(deleted).isTrue();
    }

    @Test
    void returnsNotFoundForUnknownPostAcrossCrudOperations() throws Exception {
        UUID missingId = UUID.randomUUID();
        var actor = jwt().jwt(token -> token.subject("post-missing-actor"));

        mockMvc.perform(get("/api/v1/posts/{id}", missingId).with(actor))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("post.not_found"));
        mockMvc.perform(put("/api/v1/posts/{id}", missingId)
                        .with(actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("post.not_found"));
        mockMvc.perform(delete("/api/v1/posts/{id}", missingId).with(actor))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("post.not_found"));
    }

    @Test
    void rejectsMalformedPostIdentifier() throws Exception {
        mockMvc.perform(get("/api/v1/posts/not-a-uuid")
                        .with(jwt().jwt(token -> token.subject("post-malformed-id-actor"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
    }

    @Test
    void requiresAuthenticationForPostCrud() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Unauthenticated\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/posts/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/posts/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Unauthenticated\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/posts/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private static Stream<Arguments> invalidPostRequests() {
        String elevenMedia = IntStream.range(0, 11)
                .mapToObj(index -> "{\"type\":\"IMAGE\",\"url\":\"https://cdn.example.com/" + index + ".png\"}")
                .collect(java.util.stream.Collectors.joining(","));
        String mediaUrlPrefix = "https://cdn.example.com/";
        String aboveCodePointLimitUrl = mediaUrlPrefix + "😀".repeat(
                2_049 - mediaUrlPrefix.codePointCount(0, mediaUrlPrefix.length())
        );
        return Stream.of(
                arguments("missing-description", "{}"),
                arguments("null-description", "{\"description\":null}"),
                arguments("blank-description", "{\"description\":\"   \"}"),
                arguments("nul-title", "{\"title\":\"before\\u0000after\",\"description\":\"valid\"}"),
                arguments("nul-description", "{\"description\":\"before\\u0000after\"}"),
                arguments("nul-label", "{\"description\":\"valid\",\"labels\":[\"before\\u0000after\"]}"),
                arguments("unpaired-high-title", "{\"title\":\"before\\uD800after\",\"description\":\"valid\"}"),
                arguments("unpaired-low-title", "{\"title\":\"before\\uDC00after\",\"description\":\"valid\"}"),
                arguments("unpaired-high-description", "{\"description\":\"before\\uD800after\"}"),
                arguments("unpaired-low-description", "{\"description\":\"before\\uDC00after\"}"),
                arguments("unpaired-high-label", "{\"description\":\"valid\",\"labels\":[\"before\\uD800after\"]}"),
                arguments("unpaired-low-label", "{\"description\":\"valid\",\"labels\":[\"before\\uDC00after\"]}"),
                arguments("long-description", "{\"description\":\"" + "d".repeat(20_001) + "\"}"),
                arguments("long-title", "{\"title\":\"" + "t".repeat(201) + "\",\"description\":\"valid\"}"),
                arguments("too-many-labels", "{\"description\":\"valid\",\"labels\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}"),
                arguments("blank-label", "{\"description\":\"valid\",\"labels\":[\"   \"]}"),
                arguments("control-only-label", "{\"description\":\"valid\",\"labels\":[\"\\u0000\\u001f\"]}"),
                arguments("em-space-only-label", "{\"description\":\"valid\",\"labels\":[\"\\u2003\\u2003\"]}"),
                arguments("long-label", "{\"description\":\"valid\",\"labels\":[\"" + "l".repeat(51) + "\"]}"),
                arguments("long-normalized-label", "{\"description\":\"valid\",\"labels\":[\"  "
                        + "l".repeat(51) + "  \"]}"),
                arguments("too-many-media", "{\"description\":\"valid\",\"media\":[" + elevenMedia + "]}"),
                arguments("unknown-media-type", "{\"description\":\"valid\",\"media\":[{\"type\":\"AUDIO\",\"url\":\"https://cdn.example.com/a\"}]}"),
                arguments("insecure-media-url", "{\"description\":\"valid\",\"media\":[{\"type\":\"IMAGE\",\"url\":\"http://cdn.example.com/a.png\"}]}"),
                arguments("relative-media-url", "{\"description\":\"valid\",\"media\":[{\"type\":\"IMAGE\",\"url\":\"/a.png\"}]}"),
                arguments("opaque-https-media-url", "{\"description\":\"valid\",\"media\":[{\"type\":\"IMAGE\",\"url\":\"https:x\"}]}"),
                arguments("single-slash-https-media-url", "{\"description\":\"valid\",\"media\":[{\"type\":\"IMAGE\",\"url\":\"https:/x\"}]}"),
                arguments("authorityless-https-media-url", "{\"description\":\"valid\",\"media\":[{\"type\":\"IMAGE\",\"url\":\"https:///x\"}]}"),
                arguments("media-url-above-unicode-code-point-limit", "{\"description\":\"valid\","
                        + "\"media\":[{\"type\":\"IMAGE\",\"url\":\"" + aboveCodePointLimitUrl + "\"}]}")
        );
    }

    private static Stream<Arguments> additionalJavaSpaceCharacters() {
        return Stream.of(
                arguments("U+00A0 NO-BREAK SPACE", "\u00A0"),
                arguments("U+2007 FIGURE SPACE", "\u2007"),
                arguments("U+202F NARROW NO-BREAK SPACE", "\u202F")
        );
    }

    private UUID createPost(String subject, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return postIdFromLocation(result.getResponse().getHeader(HttpHeaders.LOCATION));
    }

    private void assertInvalidCreate(String subject, String requestBody) throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
    }

    private int countPostsForSubject(String subject) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM post post_record
                        JOIN identity_account account ON account.id = post_record.author_id
                        WHERE account.external_subject = ?
                        """,
                Integer.class,
                subject
        );
    }

    private void assertOriginalPostContent(UUID postId, String owner) throws Exception {
        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original title"))
                .andExpect(jsonPath("$.description").value("Original description"))
                .andExpect(jsonPath("$.postType").value("PERSONAL"))
                .andExpect(jsonPath("$.labels[0]").value("original"))
                .andExpect(jsonPath("$.media[0].url").value("https://cdn.example.com/original.png"));
    }

    private UUID postIdFromLocation(String location) {
        assertThat(location).isNotBlank();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private void assertPersistedTitleAndLabel(UUID postId, String title, String label) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM post WHERE id = ?",
                String.class,
                postId
        )).isEqualTo(title);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT label FROM post_label WHERE post_id = ? AND position = 0",
                String.class,
                postId
        )).isEqualTo(label);
    }

    private void completeProfile(String subject, String nickname, String displayName) throws Exception {
        mockMvc.perform(put("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "displayName": "%s"
                                }
                                """.formatted(nickname, displayName)))
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
