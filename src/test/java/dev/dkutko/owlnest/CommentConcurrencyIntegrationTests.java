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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CommentConcurrencyIntegrationTests {

    private static final int CONCURRENT_CREATES = 8;
    private static final Duration BARRIER_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentCreatesProduceOneUniqueRowAndCounterIncrementPerSuccessfulRequest() throws Exception {
        UUID postId = createPost(uniqueSubject("concurrent-comment-post-owner"), "Concurrent create target");
        List<String> actors = new ArrayList<>();
        for (int index = 0; index < CONCURRENT_CREATES; index++) {
            String actor = uniqueSubject("concurrent-comment-actor-" + index);
            provisionProfile(actor);
            actors.add(actor);
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CREATES);
        CyclicBarrier startBarrier = new CyclicBarrier(CONCURRENT_CREATES);
        List<Future<MvcResult>> futures = new ArrayList<>();
        Set<UUID> commentIds = new HashSet<>();
        try {
            for (int index = 0; index < actors.size(); index++) {
                String actor = actors.get(index);
                int commentIndex = index;
                futures.add(executor.submit(() -> {
                    startBarrier.await(BARRIER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    return mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                                    .with(jwt().jwt(token -> token.subject(actor)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"text\":\"Concurrent comment " + commentIndex + "\"}"))
                            .andReturn();
                }));
            }

            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(201);
                String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
                assertThat(location).isNotBlank();
                commentIds.add(UUID.fromString(location.substring(location.lastIndexOf('/') + 1)));
            }
        } finally {
            terminateExecutor(executor, futures);
        }

        assertThat(commentIds).hasSize(CONCURRENT_CREATES);
        assertThat(commentRowCount(postId)).isEqualTo(CONCURRENT_CREATES);
        assertThat(postCommentCounter(postId)).isEqualTo(CONCURRENT_CREATES);
        assertThat(commentCounterMatchesRows(postId)).isTrue();
        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .with(jwt().jwt(token -> token.subject(uniqueSubject("concurrent-counter-reader")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counters.comments").value(CONCURRENT_CREATES));
    }

    @Test
    void concurrentCreateAndDeleteEndInOneOfTheTwoLegalSerializedOutcomes() throws Exception {
        String postOwner = uniqueSubject("comment-delete-race-owner");
        UUID postId = createPost(postOwner, "Create/delete race target");
        String commenter = uniqueSubject("comment-delete-race-author");
        provisionProfile(commenter);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        Future<MvcResult> createFuture = executor.submit(() -> {
            startBarrier.await(BARRIER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                            .with(jwt().jwt(token -> token.subject(commenter)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"Racing comment\"}"))
                    .andReturn();
        });
        Future<MvcResult> deleteFuture = executor.submit(() -> {
            startBarrier.await(BARRIER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
                            .with(jwt().jwt(token -> token.subject(postOwner))))
                    .andReturn();
        });
        MvcResult createResult;
        MvcResult deleteResult;
        try {
            createResult = createFuture.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            deleteResult = deleteFuture.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            terminateExecutor(executor, List.of(createFuture, deleteFuture));
        }

        assertThat(deleteResult.getResponse().getStatus()).isEqualTo(204);
        assertThat(createResult.getResponse().getStatus()).isIn(201, 404);
        int expectedComments = createResult.getResponse().getStatus() == 201 ? 1 : 0;
        assertThat(commentRowCount(postId)).isEqualTo(expectedComments);
        assertThat(postCommentCounter(postId)).isEqualTo(expectedComments);
        assertThat(commentCounterMatchesRows(postId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM post WHERE id = ?",
                Boolean.class,
                postId
        )).isTrue();
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(commenter))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("post.not_found"));
    }

    @Test
    void counterOverflowRollsBackCommentInsert() throws Exception {
        UUID postId = createPost(uniqueSubject("comment-overflow-post-owner"), "Counter overflow target");
        String commenter = uniqueSubject("comment-overflow-author");
        jdbcTemplate.update("UPDATE post SET comment_count = ? WHERE id = ?", Long.MAX_VALUE, postId);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .with(jwt().jwt(token -> token.subject(commenter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Must roll back\"}"))
                .andReturn())
                .hasRootCauseInstanceOf(ArithmeticException.class);

        assertThat(commentRowCount(postId)).isZero();
        assertThat(postCommentCounter(postId)).isEqualTo(Long.MAX_VALUE);
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

    private void provisionProfile(String subject) throws Exception {
        mockMvc.perform(get("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isOk());
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

    private boolean commentCounterMatchesRows(UUID postId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT post.comment_count = COUNT(comment_record.id)
                        FROM post
                        LEFT JOIN post_comment comment_record ON comment_record.post_id = post.id
                        WHERE post.id = ?
                        GROUP BY post.id, post.comment_count
                        """,
                Boolean.class,
                postId
        );
    }

    private void terminateExecutor(ExecutorService executor, List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        executor.shutdownNow();
        assertThatCode(() -> {
            if (!executor.awaitTermination(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Concurrent comment executor did not terminate");
            }
        }).doesNotThrowAnyException();
    }

    private String uniqueSubject(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
