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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PostInteractionConcurrencyIntegrationTests {

    private static final int CONCURRENT_REQUESTS = 8;
    private static final Duration BARRIER_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentDuplicateLikeBySameActorCreatesOneRelationAndOneCounterIncrement() throws Exception {
        UUID postId = createPost(uniqueSubject("duplicate-like-owner"));
        String actor = uniqueSubject("duplicate-like-actor");

        performConcurrentInteractionRequests(
                postId,
                Collections.nCopies(CONCURRENT_REQUESTS, actor),
                "likes",
                true
        );

        UUID actorAccountId = accountIdFor(actor);
        assertThat(likeCount(postId, actorAccountId)).isEqualTo(1);
        assertThat(postLikeCounter(postId)).isEqualTo(1L);
    }

    @Test
    void concurrentLikesByDistinctActorsCreateMatchingRelationsAndCounter() throws Exception {
        UUID postId = createPost(uniqueSubject("distinct-like-owner"));
        List<String> actors = new ArrayList<>();
        for (int index = 0; index < CONCURRENT_REQUESTS; index++) {
            String actor = uniqueSubject("distinct-like-actor-" + index);
            provisionAccount(actor);
            actors.add(actor);
        }

        performConcurrentInteractionRequests(postId, actors, "likes", true);

        assertThat(totalLikeCount(postId)).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(postLikeCounter(postId)).isEqualTo(CONCURRENT_REQUESTS);
    }

    @Test
    void concurrentDuplicateLikeDeletesLeaveNoRelationAndZeroCounter() throws Exception {
        UUID postId = createPost(uniqueSubject("duplicate-delete-owner"));
        String actor = uniqueSubject("duplicate-delete-actor");
        provisionAccount(actor);
        mockMvc.perform(put("/api/v1/posts/{id}/likes", postId)
                        .with(jwt().jwt(token -> token.subject(actor))))
                .andExpect(status().isNoContent());

        performConcurrentInteractionRequests(
                postId,
                Collections.nCopies(CONCURRENT_REQUESTS, actor),
                "likes",
                false
        );

        UUID actorAccountId = accountIdFor(actor);
        assertThat(likeCount(postId, actorAccountId)).isZero();
        assertThat(postLikeCounter(postId)).isZero();
    }

    @Test
    void concurrentDuplicateBookmarkAndRepostCreateOneDesiredStateEach() throws Exception {
        UUID postId = createPost(uniqueSubject("duplicate-desired-state-owner"));
        String actor = uniqueSubject("duplicate-desired-state-actor");
        provisionAccount(actor);
        List<String> requests = Collections.nCopies(CONCURRENT_REQUESTS, actor);

        performConcurrentInteractionRequests(postId, requests, "bookmark", true);
        performConcurrentInteractionRequests(postId, requests, "repost", true);

        UUID actorAccountId = accountIdFor(actor);
        assertThat(interactionCount("post_bookmark", postId, actorAccountId)).isEqualTo(1);
        assertThat(interactionCount("post_repost", postId, actorAccountId)).isEqualTo(1);
        assertThat(postRepostCounter(postId)).isEqualTo(1L);
    }

    private void performConcurrentInteractionRequests(
            UUID postId,
            List<String> actors,
            String interaction,
            boolean enabled
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actors.size());
        CyclicBarrier startBarrier = new CyclicBarrier(actors.size());
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (String actor : actors) {
                futures.add(executor.submit(() -> {
                    startBarrier.await(BARRIER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    if (enabled) {
                        return mockMvc.perform(put("/api/v1/posts/{id}/{interaction}", postId, interaction)
                                        .with(jwt().jwt(token -> token.subject(actor))))
                                .andReturn();
                    }
                    return mockMvc.perform(delete("/api/v1/posts/{id}/{interaction}", postId, interaction)
                                    .with(jwt().jwt(token -> token.subject(actor))))
                            .andReturn();
                }));
            }

            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(204);
            }
        } finally {
            for (Future<MvcResult> future : futures) {
                future.cancel(true);
            }
            executor.shutdownNow();
            assertThatCode(() -> {
                if (!executor.awaitTermination(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Concurrent interaction executor did not terminate");
                }
            }).doesNotThrowAnyException();
        }
    }

    private UUID createPost(String subject) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Concurrency target\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotBlank();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private void provisionAccount(String subject) throws Exception {
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

    private int likeCount(UUID postId, UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_like WHERE post_id = ? AND account_id = ?",
                Integer.class,
                postId,
                accountId
        );
    }

    private int totalLikeCount(UUID postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_like WHERE post_id = ?",
                Integer.class,
                postId
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

    private long postLikeCounter(UUID postId) {
        return jdbcTemplate.queryForObject(
                "SELECT like_count FROM post WHERE id = ?",
                Long.class,
                postId
        );
    }

    private long postRepostCounter(UUID postId) {
        return jdbcTemplate.queryForObject(
                "SELECT repost_count FROM post WHERE id = ?",
                Long.class,
                postId
        );
    }

    private String uniqueSubject(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
