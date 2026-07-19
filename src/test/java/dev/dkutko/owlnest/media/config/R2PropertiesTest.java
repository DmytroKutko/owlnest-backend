package dev.dkutko.owlnest.media.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class R2PropertiesTest {

    private static final String PREFIX = "owlnest.media.r2.";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(R2Configuration.class)
            .withPropertyValues(
                    PREFIX + "enabled=false",
                    PREFIX + "endpoint=https://disabled.invalid",
                    PREFIX + "upload-url-ttl=15m",
                    PREFIX + "read-url-ttl=5m",
                    PREFIX + "connection-acquisition-timeout=1s",
                    PREFIX + "connect-timeout=2s",
                    PREFIX + "read-timeout=3s",
                    PREFIX + "api-call-attempt-timeout=4s",
                    PREFIX + "api-call-timeout=12s",
                    PREFIX + "max-attempts=3"
            );

    @Test
    void startsDisabledWithoutCredentialsBucketOrCloudflareAccountSettings() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(R2Properties.class);
            assertThat(context.getBean(R2Properties.class).isEnabled()).isFalse();
        });
    }

    @Test
    void bindsCompleteEnabledConfiguration() {
        enabledContext().run(context -> {
            assertThat(context).hasNotFailed();
            R2Properties properties = context.getBean(R2Properties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getAccountId()).isEqualTo("0123456789abcdef");
            assertThat(properties.getEndpoint())
                    .isEqualTo(URI.create("https://0123456789abcdef.r2.cloudflarestorage.com"));
            assertThat(properties.getRegion()).isEqualTo("auto");
            assertThat(properties.getBucketName()).isEqualTo("owlnest-media-dev");
            assertThat(properties.getAccessKeyId()).isEqualTo("test-access-key");
            assertThat(properties.getSecretAccessKey()).isEqualTo("test-secret-key");
            assertThat(properties.getUploadUrlTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.getReadUrlTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getConnectionAcquisitionTimeout()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.getApiCallAttemptTimeout()).isEqualTo(Duration.ofSeconds(4));
            assertThat(properties.getApiCallTimeout()).isEqualTo(Duration.ofSeconds(12));
            assertThat(properties.getMaxAttempts()).isEqualTo(3);
        });
    }

    @ParameterizedTest(name = "enabled configuration rejects missing {0}")
    @MethodSource("requiredEnabledSettings")
    void rejectsIncompleteEnabledConfiguration(String scenario, String property) {
        enabledContext()
                .withPropertyValues(property)
                .run(R2PropertiesTest::assertStartupFailedValidation);
    }

    @ParameterizedTest(name = "enabled configuration rejects endpoint: {0}")
    @MethodSource("invalidCloudflareEndpoints")
    void rejectsInsecureOrNonCanonicalCloudflareEndpoint(String scenario, String endpoint, String region) {
        enabledContext()
                .withPropertyValues(
                        PREFIX + "endpoint=" + endpoint,
                        PREFIX + "region=" + region
                )
                .run(R2PropertiesTest::assertStartupFailedValidation);
    }

    @Test
    void acceptsPresignedUrlTtlsAtExactLaunchPolicyLimits() {
        enabledContext()
                .withPropertyValues(
                        PREFIX + "upload-url-ttl=15m",
                        PREFIX + "read-url-ttl=5m"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    R2Properties properties = context.getBean(R2Properties.class);
                    assertThat(properties.getUploadUrlTtl()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(properties.getReadUrlTtl()).isEqualTo(Duration.ofMinutes(5));
                });
    }

    @ParameterizedTest(name = "rejects presigned TTL one nanosecond above launch limit: {0}")
    @MethodSource("aboveMaximumPresignedTtls")
    void rejectsPresignedUrlTtlOneNanosecondAboveLaunchPolicy(String scenario, String property) {
        enabledContext()
                .withPropertyValues(property)
                .run(R2PropertiesTest::assertStartupFailedValidation);
    }

    @Test
    void applicationConfigurationKeepsExactLaunchTtlDefaults() throws IOException {
        String applicationYaml;
        try (InputStream input = new ClassPathResource("application.yaml").getInputStream()) {
            applicationYaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(applicationYaml)
                .contains("upload-url-ttl: ${R2_UPLOAD_URL_TTL:15m}")
                .contains("read-url-ttl: ${R2_READ_URL_TTL:5m}");
    }

    @Test
    void acceptsEachClientOperationTimeoutEqualToAttemptTimeout() {
        enabledContext()
                .withPropertyValues(
                        PREFIX + "connection-acquisition-timeout=4s",
                        PREFIX + "connect-timeout=4s",
                        PREFIX + "read-timeout=4s",
                        PREFIX + "api-call-attempt-timeout=4s",
                        PREFIX + "api-call-timeout=12000000001ns"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest(name = "rejects invalid timeout hierarchy: {0}")
    @MethodSource("invalidTimeoutHierarchies")
    void rejectsOperationTimeoutAboveAttemptOrAttemptNotBelowTotal(
            String scenario,
            String property
    ) {
        enabledContext()
                .withPropertyValues(property)
                .run(R2PropertiesTest::assertStartupFailedValidation);
    }

    @ParameterizedTest(name = "rejects nonpositive duration: {0}")
    @MethodSource("nonpositiveDurations")
    void rejectsZeroOrNegativeDurations(String scenario, String property) {
        enabledContext()
                .withPropertyValues(property)
                .run(R2PropertiesTest::assertStartupFailedValidation);
    }

    private ApplicationContextRunner enabledContext() {
        return contextRunner.withPropertyValues(
                PREFIX + "enabled=true",
                PREFIX + "account-id=0123456789abcdef",
                PREFIX + "endpoint=https://0123456789abcdef.r2.cloudflarestorage.com",
                PREFIX + "region=auto",
                PREFIX + "bucket-name=owlnest-media-dev",
                PREFIX + "access-key-id=test-access-key",
                PREFIX + "secret-access-key=test-secret-key"
        );
    }

    private static void assertStartupFailedValidation(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context
    ) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(org.springframework.boot.context.properties.bind.validation.BindValidationException.class);
    }

    private static Stream<Arguments> requiredEnabledSettings() {
        return Stream.of(
                arguments("account id", PREFIX + "account-id="),
                arguments("endpoint", PREFIX + "endpoint="),
                arguments("region", PREFIX + "region="),
                arguments("bucket name", PREFIX + "bucket-name="),
                arguments("access key", PREFIX + "access-key-id="),
                arguments("secret key", PREFIX + "secret-access-key=")
        );
    }

    private static Stream<Arguments> invalidCloudflareEndpoints() {
        return Stream.of(
                arguments(
                        "HTTP scheme",
                        "http://0123456789abcdef.r2.cloudflarestorage.com",
                        "auto"
                ),
                arguments(
                        "wrong account host",
                        "https://fedcba9876543210.r2.cloudflarestorage.com",
                        "auto"
                ),
                arguments(
                        "explicit port",
                        "https://0123456789abcdef.r2.cloudflarestorage.com:443",
                        "auto"
                ),
                arguments(
                        "path component",
                        "https://0123456789abcdef.r2.cloudflarestorage.com/bucket",
                        "auto"
                ),
                arguments(
                        "query component",
                        "https://0123456789abcdef.r2.cloudflarestorage.com?bucket=media",
                        "auto"
                ),
                arguments(
                        "region other than auto",
                        "https://0123456789abcdef.r2.cloudflarestorage.com",
                        "eu-east-1"
                )
        );
    }

    private static Stream<Arguments> aboveMaximumPresignedTtls() {
        return Stream.of(
                arguments(
                        "upload URL",
                        PREFIX + "upload-url-ttl=PT15M0.000000001S"
                ),
                arguments(
                        "read URL",
                        PREFIX + "read-url-ttl=PT5M0.000000001S"
                )
        );
    }

    private static Stream<Arguments> invalidTimeoutHierarchies() {
        return Stream.of(
                arguments(
                        "connection acquisition exceeds connect",
                        PREFIX + "connection-acquisition-timeout=2000000001ns"
                ),
                arguments(
                        "connect exceeds attempt",
                        PREFIX + "connect-timeout=4000000001ns"
                ),
                arguments(
                        "read exceeds attempt",
                        PREFIX + "read-timeout=4000000001ns"
                ),
                arguments(
                        "attempt equals total",
                        PREFIX + "api-call-attempt-timeout=12s"
                ),
                arguments(
                        "attempt exceeds total",
                        PREFIX + "api-call-attempt-timeout=12000000001ns"
                ),
                arguments(
                        "total call cannot contain all configured attempts",
                        PREFIX + "api-call-timeout=11999999999ns"
                )
        );
    }

    private static Stream<Arguments> nonpositiveDurations() {
        return Stream.of(
                arguments("upload URL TTL", PREFIX + "upload-url-ttl=0s"),
                arguments("read URL TTL", PREFIX + "read-url-ttl=-1s"),
                arguments("connection acquisition timeout", PREFIX + "connection-acquisition-timeout=0s"),
                arguments("connect timeout", PREFIX + "connect-timeout=0s"),
                arguments("read timeout", PREFIX + "read-timeout=0s"),
                arguments("API attempt timeout", PREFIX + "api-call-attempt-timeout=0s"),
                arguments("API total timeout", PREFIX + "api-call-timeout=0s")
        );
    }
}
