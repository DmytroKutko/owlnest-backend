package dev.dkutko.owlnest.media.storage;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class R2MediaObjectStorageTest {

    private static final String BUCKET = "owlnest-media-test";

    @Test
    void createsLocalCreateOnlyPutCapabilityWithExactSignedHeaders() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(
                URI.create("https://example.r2.cloudflarestorage.com/upload?signature=test").toURL()
        );
        when(presignedRequest.signedHeaders()).thenReturn(Map.of(
                "content-length", List.of("42"),
                "content-type", List.of("image/webp"),
                "if-none-match", List.of("*")
        ));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
        R2MediaObjectStorage storage = new R2MediaObjectStorage(s3Client, presigner, BUCKET);
        Instant expiresAt = Instant.now().plusSeconds(300);

        MediaObjectStorage.PresignedUpload result = storage.createUploadUrl(
                new MediaObjectStorage.UploadUrlRequest(
                        "managed/account/avatar/object",
                        "image/webp",
                        42,
                        expiresAt,
                        true
                )
        );

        assertThat(result.url()).isEqualTo(
                URI.create("https://example.r2.cloudflarestorage.com/upload?signature=test")
        );
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        assertThat(result.requiredHeaders()).containsExactly(
                Map.entry("Content-Type", "image/webp"),
                Map.entry("If-None-Match", "*")
        );

        ArgumentCaptor<PutObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(presigner).presignPutObject(requestCaptor.capture());
        PutObjectPresignRequest presignRequest = requestCaptor.getValue();
        assertThat(presignRequest.putObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(presignRequest.putObjectRequest().key()).isEqualTo("managed/account/avatar/object");
        assertThat(presignRequest.putObjectRequest().contentType()).isEqualTo("image/webp");
        assertThat(presignRequest.putObjectRequest().contentLength()).isEqualTo(42L);
        assertThat(presignRequest.putObjectRequest().ifNoneMatch()).isEqualTo("*");
        assertThat(presignRequest.signatureDuration())
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void realPresignerSignsCreateOnlyPutHeadersWithoutNetworkIo() {
        try (S3Presigner realPresigner = S3Presigner.builder()
                .endpointOverride(URI.create("https://account.r2.cloudflarestorage.com"))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")
                ))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            R2MediaObjectStorage storage = new R2MediaObjectStorage(mock(S3Client.class), realPresigner, BUCKET);

            MediaObjectStorage.PresignedUpload result = storage.createUploadUrl(
                    new MediaObjectStorage.UploadUrlRequest(
                            "managed/account/avatar/real-presigner",
                            "image/webp",
                            42,
                            Instant.now().plusSeconds(60),
                            true
                    )
            );

            assertThat(result.url().getHost()).isEqualTo("account.r2.cloudflarestorage.com");
            assertThat(result.url().getQuery()).contains("X-Amz-SignedHeaders=");
            assertThat(result.requiredHeaders()).containsExactly(
                    Map.entry("Content-Type", "image/webp"),
                    Map.entry("If-None-Match", "*")
            );
        }
    }

    @ParameterizedTest(name = "rejects invalid signed PUT headers: {0}")
    @MethodSource("invalidSignedPutHeaders")
    void rejectsPresignerResultWithMissingOrWrongRequiredSignedHeader(
            String scenario,
            Map<String, List<String>> signedHeaders
    ) throws Exception {
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://uploads.example.test/object").toURL());
        when(presignedRequest.signedHeaders()).thenReturn(signedHeaders);
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
        R2MediaObjectStorage storage = new R2MediaObjectStorage(mock(S3Client.class), presigner, BUCKET);

        assertSanitizedUnavailable(() -> storage.createUploadUrl(
                new MediaObjectStorage.UploadUrlRequest(
                        "managed/account/avatar/missing-header",
                        "image/png",
                        1,
                        Instant.now().plusSeconds(60),
                        true
                )
        ));
    }

    @Test
    void returnsHeadMetadataAndTargetsConfiguredBucketAndKey() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentType("video/mp4")
                        .contentLength(123L)
                        .eTag("etag-123")
                        .build()
        );
        R2MediaObjectStorage storage = new R2MediaObjectStorage(s3Client, mock(S3Presigner.class), BUCKET);

        MediaObjectStorage.ObjectMetadata metadata = storage.inspect("managed/account/post/object");

        assertThat(metadata).isEqualTo(new MediaObjectStorage.ObjectMetadata("video/mp4", 123, "etag-123"));
        ArgumentCaptor<HeadObjectRequest> requestCaptor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo("managed/account/post/object");
    }

    @Test
    void classifiesHeadNotFoundWithoutExposingProviderMessage() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(
                S3Exception.builder()
                        .statusCode(404)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build())
                        .message("provider request included private object details")
                        .build()
        );
        R2MediaObjectStorage storage = new R2MediaObjectStorage(s3Client, mock(S3Presigner.class), BUCKET);

        assertThatThrownBy(() -> storage.inspect("managed/private/object"))
                .isExactlyInstanceOf(MediaObjectNotFoundException.class)
                .hasMessage("Managed media object was not found")
                .hasMessageNotContaining("private object");
    }

    @ParameterizedTest(name = "HEAD fails closed for ambiguous 404: {0}")
    @MethodSource("unavailableNotFoundErrors")
    void classifiesMissingBucketAndAmbiguousHeadNotFoundAsUnavailable(String scenario, S3Exception exception) {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(exception);
        R2MediaObjectStorage storage = new R2MediaObjectStorage(s3Client, mock(S3Presigner.class), BUCKET);

        assertSanitizedUnavailable(() -> storage.inspect("managed/private/ambiguous"));
    }

    @Test
    void classifiesHeadAuthorizationAndTransportFailuresAsSanitizedUnavailable() {
        S3Client forbiddenClient = mock(S3Client.class);
        when(forbiddenClient.headObject(any(HeadObjectRequest.class))).thenThrow(
                S3Exception.builder()
                        .statusCode(403)
                        .message("provider credential and object detail")
                        .build()
        );
        R2MediaObjectStorage forbiddenStorage = new R2MediaObjectStorage(
                forbiddenClient,
                mock(S3Presigner.class),
                BUCKET
        );
        assertSanitizedUnavailable(() -> forbiddenStorage.inspect("managed/private/forbidden"));

        S3Client unavailableClient = mock(S3Client.class);
        when(unavailableClient.headObject(any(HeadObjectRequest.class))).thenThrow(
                SdkClientException.builder()
                        .message("provider transport detail")
                        .build()
        );
        R2MediaObjectStorage unavailableStorage = new R2MediaObjectStorage(
                unavailableClient,
                mock(S3Presigner.class),
                BUCKET
        );
        assertSanitizedUnavailable(() -> unavailableStorage.inspect("managed/private/unavailable"));
    }

    @Test
    void createsPrivateGetCapabilityForConfiguredBucketAndKey() throws Exception {
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(
                URI.create("https://downloads.example.test/object?signature=test").toURL()
        );
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        R2MediaObjectStorage storage = new R2MediaObjectStorage(mock(S3Client.class), presigner, BUCKET);
        Instant expiresAt = Instant.now().plusSeconds(60);

        MediaObjectStorage.PresignedRead result = storage.createReadUrl("managed/private/avatar", expiresAt);

        assertThat(result.url()).isEqualTo(URI.create("https://downloads.example.test/object?signature=test"));
        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(captor.capture());
        GetObjectRequest request = captor.getValue().getObjectRequest();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo("managed/private/avatar");
    }

    @Test
    void deletesIdempotentlyAndMapsProviderFailures() {
        S3Client successfulClient = mock(S3Client.class);
        R2MediaObjectStorage storage = new R2MediaObjectStorage(
                successfulClient,
                mock(S3Presigner.class),
                BUCKET
        );

        storage.delete("managed/private/delete");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(successfulClient).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("managed/private/delete");

        S3Client missingClient = mock(S3Client.class);
        when(missingClient.deleteObject(any(DeleteObjectRequest.class))).thenThrow(
                S3Exception.builder()
                        .statusCode(404)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build())
                        .message("private provider detail")
                        .build()
        );
        new R2MediaObjectStorage(missingClient, mock(S3Presigner.class), BUCKET)
                .delete("managed/already-missing");

        S3Client failedClient = mock(S3Client.class);
        when(failedClient.deleteObject(any(DeleteObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(503).message("private provider detail").build()
        );
        assertSanitizedUnavailable(() -> new R2MediaObjectStorage(
                failedClient,
                mock(S3Presigner.class),
                BUCKET
        ).delete("managed/private/failure"));
    }

    @ParameterizedTest(name = "DELETE fails closed for ambiguous 404: {0}")
    @MethodSource("unavailableNotFoundErrors")
    void classifiesMissingBucketAndAmbiguousDeleteNotFoundAsUnavailable(String scenario, S3Exception exception) {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(exception);
        R2MediaObjectStorage storage = new R2MediaObjectStorage(s3Client, mock(S3Presigner.class), BUCKET);

        assertSanitizedUnavailable(() -> storage.delete("managed/private/ambiguous"));
    }

    @Test
    void disabledStorageRejectsEveryOperationWithSameSanitizedError() {
        DisabledMediaObjectStorage storage = new DisabledMediaObjectStorage();
        Instant expiresAt = Instant.now().plusSeconds(60);

        List<ThrowingCallable> operations = List.of(
                storage::ensureAvailable,
                () -> storage.createUploadUrl(new MediaObjectStorage.UploadUrlRequest(
                        "managed/object",
                        "image/jpeg",
                        1,
                        expiresAt,
                        true
                )),
                () -> storage.inspect("managed/object"),
                () -> storage.createReadUrl("managed/object", expiresAt),
                () -> storage.delete("managed/object")
        );

        operations.forEach(R2MediaObjectStorageTest::assertSanitizedUnavailable);
    }

    private static void assertSanitizedUnavailable(ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isExactlyInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("Managed media storage is unavailable")
                .hasMessageNotContaining("managed/private")
                .hasMessageNotContaining("provider");
    }

    private static Stream<Arguments> invalidSignedPutHeaders() {
        return Stream.of(
                arguments(
                        "missing Content-Length",
                        Map.of(
                                "content-type", List.of("image/png"),
                                "if-none-match", List.of("*")
                        )
                ),
                arguments(
                        "wrong Content-Length",
                        Map.of(
                                "content-length", List.of("2"),
                                "content-type", List.of("image/png"),
                                "if-none-match", List.of("*")
                        )
                ),
                arguments(
                        "missing Content-Type",
                        Map.of(
                                "content-length", List.of("1"),
                                "if-none-match", List.of("*")
                        )
                ),
                arguments(
                        "missing If-None-Match",
                        Map.of(
                                "content-length", List.of("1"),
                                "content-type", List.of("image/png")
                        )
                )
        );
    }

    private static Stream<Arguments> unavailableNotFoundErrors() {
        return Stream.of(
                arguments(
                        "NoSuchBucket",
                        S3Exception.builder()
                                .statusCode(404)
                                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucket").build())
                                .message("provider private bucket detail")
                                .build()
                ),
                arguments(
                        "unknown error code",
                        S3Exception.builder()
                                .statusCode(404)
                                .awsErrorDetails(AwsErrorDetails.builder().errorCode("Unknown404").build())
                                .message("provider private detail")
                                .build()
                ),
                arguments(
                        "missing error details",
                        S3Exception.builder()
                                .statusCode(404)
                                .message("provider ambiguous private detail")
                                .build()
                )
        );
    }
}
