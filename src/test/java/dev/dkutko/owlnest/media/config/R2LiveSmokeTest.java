package dev.dkutko.owlnest.media.config;

import dev.dkutko.owlnest.media.storage.MediaObjectNotFoundException;
import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class R2LiveSmokeTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    @Test
    @EnabledIfEnvironmentVariable(named = "R2_LIVE_TEST", matches = "(?i:true)")
    void writesReadsInspectsAndDeletesUniqueObjectInConfiguredR2Bucket() throws Exception {
        R2Properties properties = liveProperties();
        R2Configuration configuration = new R2Configuration();
        String objectKey = "managed/smoke/" + UUID.randomUUID() + ".png";

        try (S3Client s3Client = configuration.r2S3Client(properties);
             S3Presigner presigner = configuration.r2S3Presigner(properties);
             HttpClient httpClient = HttpClient.newHttpClient()) {
            MediaObjectStorage storage = configuration.r2MediaObjectStorage(s3Client, presigner, properties);
            try {
                assertThatThrownBy(() -> storage.inspect(objectKey))
                        .isExactlyInstanceOf(MediaObjectNotFoundException.class);

                MediaObjectStorage.PresignedUpload upload = storage.createUploadUrl(
                        new MediaObjectStorage.UploadUrlRequest(
                                objectKey,
                                "image/png",
                                ONE_PIXEL_PNG.length,
                                Instant.now().plus(Duration.ofMinutes(5)),
                                true
                        )
                );
                HttpRequest.Builder putRequest = HttpRequest.newBuilder(upload.url())
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(ONE_PIXEL_PNG));
                upload.requiredHeaders().forEach(putRequest::header);

                HttpResponse<Void> putResponse = httpClient.send(
                        putRequest.build(),
                        HttpResponse.BodyHandlers.discarding()
                );
                assertThat(putResponse.statusCode()).isBetween(200, 299);

                MediaObjectStorage.ObjectMetadata metadata = storage.inspect(objectKey);
                assertThat(metadata.contentType()).isEqualTo("image/png");
                assertThat(metadata.contentLength()).isEqualTo(ONE_PIXEL_PNG.length);
                assertThat(metadata.etag()).isNotBlank();

                MediaObjectStorage.PresignedRead read = storage.createReadUrl(
                        objectKey,
                        Instant.now().plus(Duration.ofMinutes(2))
                );
                HttpResponse<byte[]> getResponse = httpClient.send(
                        HttpRequest.newBuilder(read.url()).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                );
                assertThat(getResponse.statusCode()).isEqualTo(200);
                assertThat(getResponse.body()).containsExactly(ONE_PIXEL_PNG);
            } finally {
                storage.delete(objectKey);
            }
        }
    }

    private static R2Properties liveProperties() {
        String accountId = requiredEnvironment("R2_ACCOUNT_ID");
        return new R2Properties(
                true,
                accountId,
                URI.create(environmentOrDefault(
                        "R2_ENDPOINT",
                        "https://" + accountId + ".r2.cloudflarestorage.com"
                )),
                environmentOrDefault("R2_REGION", "auto"),
                requiredEnvironment("R2_BUCKET_NAME"),
                requiredEnvironment("R2_ACCESS_KEY_ID"),
                requiredEnvironment("R2_SECRET_ACCESS_KEY"),
                Duration.ofMinutes(15),
                Duration.ofMinutes(5),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                Duration.ofSeconds(4),
                Duration.ofSeconds(12),
                3
        );
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the live R2 smoke test");
        }
        return value;
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
