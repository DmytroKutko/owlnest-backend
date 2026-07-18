package dev.dkutko.owlnest;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.ArrayList;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PostReadConsistencyConcurrencyIntegrationTests {

    private static final int READER_COUNT = 4;
    private static final int ROUNDS = 50;
    private static final Duration BARRIER_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(90);
    private static final Version ALPHA = new Version(
            "Alpha title",
            "Alpha description",
            "PERSONAL",
            List.of("alpha-one", "alpha-two"),
            List.of(new Media("IMAGE", "https://cdn.example.com/alpha-one.png"),
                    new Media("VIDEO", "https://cdn.example.com/alpha-two.mp4"))
    );
    private static final Version BETA = new Version(
            "Beta title",
            "Beta description",
            "COMMUNITY",
            List.of("beta-one", "beta-two"),
            List.of(new Media("VIDEO", "https://cdn.example.com/beta-one.mp4"),
                    new Media("IMAGE", "https://cdn.example.com/beta-two.png"))
    );

    @Autowired
    private MockMvc mockMvc;

    @Test
    void concurrentGetsObserveOnlyOneCompleteCommittedReplacementVersion() throws Exception {
        String owner = "consistent-read-owner-" + UUID.randomUUID();
        String viewer = "consistent-read-viewer-" + UUID.randomUUID();
        UUID postId = createPost(owner, ALPHA);
        mockMvc.perform(get("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isOk());

        ExecutorService executor = Executors.newFixedThreadPool(READER_COUNT + 1);
        CyclicBarrier roundBarrier = new CyclicBarrier(READER_COUNT + 1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> replaceRepeatedly(postId, owner, roundBarrier)));
            for (int reader = 0; reader < READER_COUNT; reader++) {
                futures.add(executor.submit(() -> readRepeatedly(postId, viewer, roundBarrier)));
            }
            for (Future<?> future : futures) {
                future.get(RUN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } finally {
            for (Future<?> future : futures) {
                future.cancel(true);
            }
            executor.shutdownNow();
            assertThatCode(() -> {
                if (!executor.awaitTermination(RUN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Concurrent post read executor did not terminate");
                }
            }).doesNotThrowAnyException();
        }
    }

    private Void replaceRepeatedly(UUID postId, String owner, CyclicBarrier barrier) throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            barrier.await(BARRIER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            Version replacement = round % 2 == 0 ? BETA : ALPHA;
            MvcResult result = mockMvc.perform(put("/api/v1/posts/{id}", postId)
                            .with(jwt().jwt(token -> token.subject(owner)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(replacement.requestBody()))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            barrier.await(BARRIER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
        return null;
    }

    private Void readRepeatedly(UUID postId, String viewer, CyclicBarrier barrier) throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            barrier.await(BARRIER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            MvcResult result = mockMvc.perform(get("/api/v1/posts/{id}", postId)
                            .with(jwt().jwt(token -> token.subject(viewer))))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(Version.fromResponse(result.getResponse().getContentAsString())).isIn(ALPHA, BETA);
            barrier.await(BARRIER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
        return null;
    }

    private UUID createPost(String owner, Version version) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .with(jwt().jwt(token -> token.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(version.requestBody()))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotBlank();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private record Version(
            String title,
            String description,
            String postType,
            List<String> labels,
            List<Media> media
    ) {

        private String requestBody() {
            return """
                    {
                      "title": "%s",
                      "description": "%s",
                      "postType": "%s",
                      "labels": ["%s", "%s"],
                      "media": [
                        {"type": "%s", "url": "%s"},
                        {"type": "%s", "url": "%s"}
                      ]
                    }
                    """.formatted(
                    title,
                    description,
                    postType,
                    labels.get(0),
                    labels.get(1),
                    media.get(0).type(),
                    media.get(0).url(),
                    media.get(1).type(),
                    media.get(1).url()
            );
        }

        private static Version fromResponse(String json) {
            List<String> responseLabels = JsonPath.read(json, "$.labels");
            List<String> mediaTypes = JsonPath.read(json, "$.media[*].type");
            List<String> mediaUrls = JsonPath.read(json, "$.media[*].url");
            return new Version(
                    JsonPath.read(json, "$.title"),
                    JsonPath.read(json, "$.description"),
                    JsonPath.read(json, "$.postType"),
                    List.copyOf(responseLabels),
                    List.of(
                            new Media(mediaTypes.get(0), mediaUrls.get(0)),
                            new Media(mediaTypes.get(1), mediaUrls.get(1))
                    )
            );
        }
    }

    private record Media(String type, String url) {
    }
}
