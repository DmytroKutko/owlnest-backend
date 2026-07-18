package dev.dkutko.owlnest;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PostJwtDecoderIntegrationTests {

    private static final String AUDIENCE = "owlnest-api";
    private static final JwtFixture JWT_FIXTURE = JwtFixture.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", JWT_FIXTURE::issuer);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWT_FIXTURE::jwkSetUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCE);
    }

    @AfterAll
    static void closeJwkServer() throws InterruptedException {
        JWT_FIXTURE.close();
    }

    @Test
    void acceptsValidSignedTokenThroughProductionDecoderAndReachesPostAndCommentControllers() throws Exception {
        String subject = "valid-decoder-subject-" + UUID.randomUUID();
        String token = JWT_FIXTURE.token(new TokenClaims(
                subject,
                JWT_FIXTURE.issuer(),
                AUDIENCE,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300)
        ));

        mockMvc.perform(get("/api/v1/posts/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("post.not_found"));

        MvcResult postResult = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Decoder validation\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String postLocation = postResult.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(postLocation).isNotBlank();
        UUID postId = UUID.fromString(postLocation.substring(postLocation.lastIndexOf('/') + 1));

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Comment decoder validation\"}"))
                .andExpect(status().isCreated());

        assertThat(accountCount(subject)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    void rejectsInvalidTokenBeforeProvisioning(String scenario, String subject, String token) throws Exception {
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        assertThat(accountCount(subject)).as(scenario).isZero();
    }

    private static Stream<Arguments> invalidTokens() throws JOSEException {
        Instant now = Instant.now();
        return Stream.of(
                invalidSignatureArguments(now),
                tokenArguments(
                        "expired token",
                        "expired-subject-" + UUID.randomUUID(),
                        JWT_FIXTURE.issuer(),
                        AUDIENCE,
                        now.minusSeconds(600),
                        now.minusSeconds(120)
                ),
                tokenArguments(
                        "future not-before",
                        "future-nbf-subject-" + UUID.randomUUID(),
                        JWT_FIXTURE.issuer(),
                        AUDIENCE,
                        now.plusSeconds(120),
                        now.plusSeconds(600)
                ),
                tokenArguments(
                        "wrong issuer",
                        "wrong-issuer-subject-" + UUID.randomUUID(),
                        JWT_FIXTURE.issuer() + "/wrong",
                        AUDIENCE,
                        now.minusSeconds(5),
                        now.plusSeconds(300)
                ),
                tokenArguments(
                        "wrong audience",
                        "wrong-audience-subject-" + UUID.randomUUID(),
                        JWT_FIXTURE.issuer(),
                        "another-api",
                        now.minusSeconds(5),
                        now.plusSeconds(300)
                )
        );
    }

    private static Arguments invalidSignatureArguments(Instant now) throws JOSEException {
        String subject = "invalid-signature-subject-" + UUID.randomUUID();
        return Arguments.of("invalid signature", subject, invalidSignatureToken(subject, now));
    }

    private static Arguments tokenArguments(
            String scenario,
            String subject,
            String issuer,
            String audience,
            Instant notBefore,
            Instant expiresAt
    ) throws JOSEException {
        return Arguments.of(
                scenario,
                subject,
                JWT_FIXTURE.token(new TokenClaims(subject, issuer, audience, notBefore, expiresAt))
        );
    }

    private static String invalidSignatureToken(String subject, Instant now) throws JOSEException {
        return JWT_FIXTURE.token(
                new TokenClaims(
                        subject,
                        JWT_FIXTURE.issuer(),
                        AUDIENCE,
                        now.minusSeconds(5),
                        now.plusSeconds(300)
                ),
                JWT_FIXTURE.invalidSignatureKey()
        );
    }

    private int accountCount(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_account WHERE external_subject = ?",
                Integer.class,
                subject
        );
    }

    private record TokenClaims(
            String subject,
            String issuer,
            String audience,
            Instant notBefore,
            Instant expiresAt
    ) {
    }

    private record JwtFixture(
            HttpServer server,
            ExecutorService executor,
            RSAKey signingKey,
            RSAKey invalidSignatureKey,
            String issuer,
            String jwkSetUri
    ) {

        private static JwtFixture start() {
            try {
                RSAKey signingKey = new RSAKeyGenerator(2_048).keyID("owlnest-test-key").generate();
                RSAKey invalidSignatureKey = new RSAKeyGenerator(2_048).keyID("invalid-test-key").generate();
                byte[] jwkSet = new JWKSet(signingKey.toPublicJWK())
                        .toString()
                        .getBytes(StandardCharsets.UTF_8);
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                ExecutorService executor = Executors.newSingleThreadExecutor(
                        Thread.ofPlatform().daemon(true).name("owlnest-test-jwks").factory()
                );
                server.setExecutor(executor);
                server.createContext("/jwks", exchange -> respondWithJwkSet(exchange, jwkSet));
                server.start();
                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                return new JwtFixture(
                        server,
                        executor,
                        signingKey,
                        invalidSignatureKey,
                        baseUrl + "/issuer",
                        baseUrl + "/jwks"
                );
            } catch (JOSEException | IOException exception) {
                throw new IllegalStateException("Local test JWKS server could not start", exception);
            }
        }

        private String token(TokenClaims claims) throws JOSEException {
            return token(claims, signingKey);
        }

        private String token(TokenClaims claims, RSAKey signerKey) throws JOSEException {
            Instant issuedAt = claims.notBefore().minusSeconds(5);
            JWTClaimsSet claimSet = new JWTClaimsSet.Builder()
                    .subject(claims.subject())
                    .issuer(claims.issuer())
                    .audience(claims.audience())
                    .issueTime(Date.from(issuedAt))
                    .notBeforeTime(Date.from(claims.notBefore()))
                    .expirationTime(Date.from(claims.expiresAt()))
                    .build();
            SignedJWT signedJwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claimSet
            );
            signedJwt.sign(new RSASSASigner(signerKey.toPrivateKey()));
            return signedJwt.serialize();
        }

        private void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Local test JWKS executor did not terminate");
            }
        }

        private static void respondWithJwkSet(HttpExchange exchange, byte[] body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            } finally {
                exchange.close();
            }
        }
    }
}
