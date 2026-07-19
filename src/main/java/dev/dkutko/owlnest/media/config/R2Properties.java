package dev.dkutko.owlnest.media.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "owlnest.media.r2")
public class R2Properties {

    private static final Duration MAX_UPLOAD_URL_TTL = Duration.ofMinutes(15);
    private static final Duration MAX_READ_URL_TTL = Duration.ofMinutes(5);

    private final boolean enabled;
    private final String accountId;

    @NotNull
    private final URI endpoint;

    private final String region;
    private final String bucketName;
    private final String accessKeyId;
    private final String secretAccessKey;

    @NotNull
    private final Duration uploadUrlTtl;

    @NotNull
    private final Duration readUrlTtl;

    @NotNull
    private final Duration connectionAcquisitionTimeout;

    @NotNull
    private final Duration connectTimeout;

    @NotNull
    private final Duration readTimeout;

    @NotNull
    private final Duration apiCallAttemptTimeout;

    @NotNull
    private final Duration apiCallTimeout;

    @Min(1)
    @Max(3)
    private final int maxAttempts;

    public R2Properties(
            boolean enabled,
            String accountId,
            URI endpoint,
            String region,
            String bucketName,
            String accessKeyId,
            String secretAccessKey,
            Duration uploadUrlTtl,
            Duration readUrlTtl,
            Duration connectionAcquisitionTimeout,
            Duration connectTimeout,
            Duration readTimeout,
            Duration apiCallAttemptTimeout,
            Duration apiCallTimeout,
            int maxAttempts
    ) {
        this.enabled = enabled;
        this.accountId = accountId;
        this.endpoint = endpoint;
        this.region = region;
        this.bucketName = bucketName;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.uploadUrlTtl = uploadUrlTtl;
        this.readUrlTtl = readUrlTtl;
        this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.apiCallAttemptTimeout = apiCallAttemptTimeout;
        this.apiCallTimeout = apiCallTimeout;
        this.maxAttempts = maxAttempts;
    }

    @AssertTrue(message = "R2 credentials and bucket settings must be configured when R2 is enabled")
    public boolean isCompleteWhenEnabled() {
        return !enabled
                || hasText(accountId)
                && endpoint != null
                && hasText(region)
                && hasText(bucketName)
                && hasText(accessKeyId)
                && hasText(secretAccessKey);
    }

    @AssertTrue(message = "R2 endpoint must use HTTPS when R2 is enabled")
    public boolean isSecureEndpointWhenEnabled() {
        return !enabled || endpoint != null && "https".equalsIgnoreCase(endpoint.getScheme());
    }

    @AssertTrue(message = "R2 endpoint must match the configured Cloudflare account")
    public boolean isCloudflareAccountEndpointWhenEnabled() {
        if (!enabled || endpoint == null || !hasText(accountId) || !hasText(region)) {
            return true;
        }
        String expectedHost = accountId + ".r2.cloudflarestorage.com";
        return expectedHost.equalsIgnoreCase(endpoint.getHost())
                && endpoint.getPort() == -1
                && (endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()))
                && endpoint.getUserInfo() == null
                && endpoint.getQuery() == null
                && endpoint.getFragment() == null
                && "auto".equals(region);
    }

    @AssertTrue(message = "R2 durations must be positive")
    public boolean isDurationsPositive() {
        return isPositive(uploadUrlTtl)
                && isPositive(readUrlTtl)
                && isPositive(connectionAcquisitionTimeout)
                && isPositive(connectTimeout)
                && isPositive(readTimeout)
                && isPositive(apiCallAttemptTimeout)
                && isPositive(apiCallTimeout);
    }

    @AssertTrue(message = "R2 upload URL TTL must not exceed fifteen minutes")
    public boolean isUploadUrlTtlWithinPolicy() {
        return isAtMost(uploadUrlTtl, MAX_UPLOAD_URL_TTL);
    }

    @AssertTrue(message = "R2 read URL TTL must not exceed five minutes")
    public boolean isReadUrlTtlWithinPolicy() {
        return isAtMost(readUrlTtl, MAX_READ_URL_TTL);
    }

    @AssertTrue(message = "R2 timeout hierarchy must fit acquisition, connect and read within an attempt and total call")
    public boolean isTimeoutHierarchyValid() {
        if (connectionAcquisitionTimeout == null
                || connectTimeout == null
                || readTimeout == null
                || apiCallAttemptTimeout == null
                || apiCallTimeout == null
                || !isPositive(apiCallAttemptTimeout)
                || !isPositive(apiCallTimeout)
                || maxAttempts < 1
                || maxAttempts > 3) {
            return true;
        }
        return connectionAcquisitionTimeout.compareTo(connectTimeout) <= 0
                && connectTimeout.compareTo(apiCallAttemptTimeout) <= 0
                && readTimeout.compareTo(apiCallAttemptTimeout) <= 0
                && apiCallAttemptTimeout.compareTo(apiCallTimeout) < 0
                && apiCallTimeout.dividedBy(apiCallAttemptTimeout) >= maxAttempts;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getAccountId() {
        return accountId;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public String getRegion() {
        return region;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public Duration getUploadUrlTtl() {
        return uploadUrlTtl;
    }

    public Duration getReadUrlTtl() {
        return readUrlTtl;
    }

    public Duration getConnectionAcquisitionTimeout() {
        return connectionAcquisitionTimeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public Duration getApiCallAttemptTimeout() {
        return apiCallAttemptTimeout;
    }

    public Duration getApiCallTimeout() {
        return apiCallTimeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

    private static boolean isAtMost(Duration value, Duration maximum) {
        return value != null && value.compareTo(maximum) <= 0;
    }
}
