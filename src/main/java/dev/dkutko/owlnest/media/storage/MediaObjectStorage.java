package dev.dkutko.owlnest.media.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public interface MediaObjectStorage {

    default void ensureAvailable() {
    }

    PresignedUpload createUploadUrl(UploadUrlRequest request);

    ObjectMetadata inspect(String objectKey);

    PresignedRead createReadUrl(String objectKey, Instant expiresAt);

    void delete(String objectKey);

    record UploadUrlRequest(
            String objectKey,
            String contentType,
            long contentLength,
            Instant expiresAt,
            boolean requireObjectToBeAbsent
    ) {
        public UploadUrlRequest {
            Objects.requireNonNull(objectKey, "objectKey must not be null");
            Objects.requireNonNull(contentType, "contentType must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            if (contentLength < 1) {
                throw new IllegalArgumentException("contentLength must be positive");
            }
        }
    }

    record PresignedUpload(
            URI url,
            Instant expiresAt,
            Map<String, String> requiredHeaders
    ) {
        public PresignedUpload {
            Objects.requireNonNull(url, "url must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            requiredHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(requiredHeaders));
        }
    }

    record ObjectMetadata(
            String contentType,
            long contentLength,
            String etag
    ) {
    }

    record PresignedRead(
            URI url,
            Instant expiresAt
    ) {
    }
}
