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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PostListIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsActivePostsNewestFirstAcrossCursorPages() throws Exception {
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));

        String owner = "global-list-owner-" + UUID.randomUUID();
        UUID newest = createPost(owner, "Global newest");
        UUID middle = createPost(owner, "Global middle");
        UUID oldest = createPost(owner, "Global oldest");
        UUID deleted = createPost(owner, "Global deleted");
        setCreatedAt(newest, Instant.parse("2200-01-04T00:00:00Z"));
        setCreatedAt(middle, Instant.parse("2200-01-04T00:00:00Z"));
        setCreatedAt(oldest, Instant.parse("2200-01-02T00:00:00Z"));
        setCreatedAt(deleted, Instant.parse("2200-01-05T00:00:00Z"));
        deletePost(deleted, owner);
        List<UUID> expectedFirstPage = jdbcTemplate.query(
                "SELECT id FROM post WHERE id IN (?, ?) ORDER BY created_at DESC, id DESC",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                newest,
                middle
        );

        String viewer = "global-list-viewer-" + UUID.randomUUID();
        MvcResult firstPage = mockMvc.perform(get("/api/v1/posts")
                        .queryParam("limit", "2")
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(expectedFirstPage.get(0).toString()))
                .andExpect(jsonPath("$.items[1].id").value(expectedFirstPage.get(1).toString()))
                .andExpect(jsonPath("$.items[0].viewerState.liked").value(false))
                .andExpect(jsonPath("$.items[0].viewerState.bookmarked").value(false))
                .andExpect(jsonPath("$.items[0].viewerState.reposted").value(false))
                .andExpect(jsonPath("$.items[0].viewerState.isAuthor").value(false))
                .andExpect(jsonPath("$.items[0].viewerState.canEdit").value(false))
                .andExpect(jsonPath("$.items[0].viewerState.canDelete").value(false))
                .andExpect(jsonPath("$.page.limit").value(2))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andExpect(jsonPath("$.page.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.links.self").value("/api/v1/posts?limit=2"))
                .andExpect(jsonPath("$.links.next").isNotEmpty())
                .andReturn();

        String cursor = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.page.nextCursor");
        assertThat((String) JsonPath.read(firstPage.getResponse().getContentAsString(), "$.links.next"))
                .isEqualTo("/api/v1/posts?limit=2&cursor=" + cursor);
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("limit", "1")
                        .queryParam("cursor", cursor)
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(oldest.toString()))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andExpect(jsonPath("$.page.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.links.next").isNotEmpty());
    }

    @Test
    void validatesGlobalPostListQueryControls() throws Exception {
        String viewer = "global-list-validation-viewer-" + UUID.randomUUID();

        mockMvc.perform(get("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.limit").value(20));
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("limit", "100")
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.limit").value(100));

        for (String invalidLimit : List.of("0", "101", "abc")) {
            mockMvc.perform(get("/api/v1/posts")
                            .queryParam("limit", invalidLimit)
                            .with(jwt().jwt(token -> token.subject(viewer))))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("cursor", "not-a-cursor")
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("cursor", "x".repeat(1_025))
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("limit", "1", "2")
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("unexpected", "value")
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isBadRequest());
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

    private void setCreatedAt(UUID postId, Instant createdAt) {
        assertThat(jdbcTemplate.update(
                "UPDATE post SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                postId
        )).isEqualTo(1);
    }

    private void deletePost(UUID postId, String owner) throws Exception {
        mockMvc.perform(delete("/api/v1/posts/{id}", postId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isNoContent());
    }
}
