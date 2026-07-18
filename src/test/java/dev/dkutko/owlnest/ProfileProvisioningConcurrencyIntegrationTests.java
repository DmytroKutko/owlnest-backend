package dev.dkutko.owlnest;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProfileProvisioningConcurrencyIntegrationTests {

    private static final int CONCURRENT_REQUESTS = 8;
    private static final Duration BARRIER_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentFirstUseRequestsReturnOneStableProvisionedAccountAndProfile() throws Exception {
        String subject = "concurrent-first-use-" + UUID.randomUUID();
        Set<String> responseAccountIds = performConcurrentProfileRequests(subject);

        assertThat(responseAccountIds).hasSize(1);
        UUID stableAccountId = UUID.fromString(responseAccountIds.iterator().next());
        assertThat(accountCount(subject)).isEqualTo(1);
        assertThat(profileCount(subject)).isEqualTo(1);
        assertThat(accountIdFor(subject)).isEqualTo(stableAccountId);
    }

    @Test
    void concurrentRequestsForExistingAccountWithoutProfileCreateOneStableProfile() throws Exception {
        String subject = "concurrent-missing-profile-" + UUID.randomUUID();
        UUID existingAccountId = insertAccountWithoutProfile(subject);
        try {
            Set<String> responseAccountIds = performConcurrentProfileRequests(subject);

            assertThat(responseAccountIds).containsExactly(existingAccountId.toString());
            assertThat(accountCount(subject)).isEqualTo(1);
            assertThat(profileCount(subject)).isEqualTo(1);
            assertThat(accountIdFor(subject)).isEqualTo(existingAccountId);
        } finally {
            jdbcTemplate.update("DELETE FROM identity_account WHERE id = ?", existingAccountId);
        }
    }

    private Set<String> performConcurrentProfileRequests(String subject) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CyclicBarrier startBarrier = new CyclicBarrier(CONCURRENT_REQUESTS);
        List<Future<MvcResult>> futures = new ArrayList<>();
        Set<String> responseAccountIds = new HashSet<>();
        try {
            for (int request = 0; request < CONCURRENT_REQUESTS; request++) {
                futures.add(executor.submit(() -> {
                    startBarrier.await(BARRIER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    return mockMvc.perform(get("/api/v1/profile/me")
                                    .with(jwt().jwt(token -> token.subject(subject))))
                            .andReturn();
                }));
            }

            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(200);
                responseAccountIds.add(JsonPath.read(result.getResponse().getContentAsString(), "$.accountId"));
            }
        } finally {
            for (Future<MvcResult> future : futures) {
                future.cancel(true);
            }
            executor.shutdownNow();
            assertThatCode(() -> {
                if (!executor.awaitTermination(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Concurrent profile executor did not terminate");
                }
            }).doesNotThrowAnyException();
        }
        return responseAccountIds;
    }

    private UUID insertAccountWithoutProfile(String subject) {
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO identity_account (
                            id, provider, external_subject, email, email_verified, created_at, last_seen_at
                        ) VALUES (?, 'KEYCLOAK', ?, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                accountId,
                subject
        );
        return accountId;
    }

    private int accountCount(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_account WHERE external_subject = ?",
                Integer.class,
                subject
        );
    }

    private int profileCount(String subject) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM profile profile_data
                        JOIN identity_account account ON account.id = profile_data.account_id
                        WHERE account.external_subject = ?
                        """,
                Integer.class,
                subject
        );
    }

    private UUID accountIdFor(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM identity_account WHERE external_subject = ?",
                UUID.class,
                subject
        );
    }
}
