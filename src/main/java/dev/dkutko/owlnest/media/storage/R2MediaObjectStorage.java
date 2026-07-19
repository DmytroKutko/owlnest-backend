package dev.dkutko.owlnest.media.storage;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class R2MediaObjectStorage implements MediaObjectStorage {

    private static final Duration MAXIMUM_SIGNATURE_DURATION = Duration.ofDays(7);
    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String IF_NONE_MATCH_HEADER = "If-None-Match";
    private static final String CREATE_ONLY_VALUE = "*";

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucketName;

    public R2MediaObjectStorage(S3Client s3Client, S3Presigner presigner, String bucketName) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucketName = bucketName;
    }

    @Override
    public PresignedUpload createUploadUrl(UploadUrlRequest request) {
        Duration signatureDuration = remainingDuration(request.expiresAt());
        PutObjectRequest.Builder putObject = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(request.objectKey())
                .contentType(request.contentType())
                .contentLength(request.contentLength());
        if (request.requireObjectToBeAbsent()) {
            putObject.ifNoneMatch(CREATE_ONLY_VALUE);
        }

        try {
            PresignedPutObjectRequest presigned = presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(signatureDuration)
                            .putObjectRequest(putObject.build())
                            .build()
            );
            requireSignedHeader(
                    presigned.signedHeaders(),
                    CONTENT_LENGTH_HEADER,
                    Long.toString(request.contentLength())
            );
            requireSignedHeader(presigned.signedHeaders(), CONTENT_TYPE_HEADER, request.contentType());
            if (request.requireObjectToBeAbsent()) {
                requireSignedHeader(presigned.signedHeaders(), IF_NONE_MATCH_HEADER, CREATE_ONLY_VALUE);
            }

            Map<String, String> requiredHeaders = new LinkedHashMap<>();
            requiredHeaders.put(CONTENT_TYPE_HEADER, request.contentType());
            if (request.requireObjectToBeAbsent()) {
                requiredHeaders.put(IF_NONE_MATCH_HEADER, CREATE_ONLY_VALUE);
            }
            return new PresignedUpload(
                    URI.create(presigned.url().toString()),
                    request.expiresAt(),
                    requiredHeaders
            );
        } catch (SdkException | IllegalArgumentException exception) {
            throw new MediaStorageUnavailableException(exception);
        }
    }

    @Override
    public ObjectMetadata inspect(String objectKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build()
            );
            Long contentLength = response.contentLength();
            return new ObjectMetadata(
                    response.contentType(),
                    contentLength == null ? -1 : contentLength,
                    response.eTag()
            );
        } catch (S3Exception exception) {
            if (isNoSuchKey(exception)) {
                throw new MediaObjectNotFoundException();
            }
            throw new MediaStorageUnavailableException(exception);
        } catch (SdkException exception) {
            throw new MediaStorageUnavailableException(exception);
        }
    }

    @Override
    public PresignedRead createReadUrl(String objectKey, Instant expiresAt) {
        Duration signatureDuration = remainingDuration(expiresAt);
        try {
            PresignedGetObjectRequest presigned = presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(signatureDuration)
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(bucketName)
                                    .key(objectKey)
                                    .build())
                            .build()
            );
            return new PresignedRead(URI.create(presigned.url().toString()), expiresAt);
        } catch (SdkException | IllegalArgumentException exception) {
            throw new MediaStorageUnavailableException(exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build());
        } catch (S3Exception exception) {
            if (isNoSuchKey(exception)) {
                return;
            }
            throw new MediaStorageUnavailableException(exception);
        } catch (SdkException exception) {
            throw new MediaStorageUnavailableException(exception);
        }
    }

    private static boolean isNoSuchKey(S3Exception exception) {
        return exception.statusCode() == 404
                && exception.awsErrorDetails() != null
                && "NoSuchKey".equals(exception.awsErrorDetails().errorCode());
    }

    private static Duration remainingDuration(Instant expiresAt) {
        Duration duration = Duration.between(Instant.now(), expiresAt);
        if (duration.isNegative() || duration.isZero() || duration.compareTo(MAXIMUM_SIGNATURE_DURATION) > 0) {
            throw new MediaStorageUnavailableException();
        }
        return duration;
    }

    private static void requireSignedHeader(
            Map<String, List<String>> signedHeaders,
            String expectedName,
            String expectedValue
    ) {
        boolean present = signedHeaders.entrySet().stream()
                .anyMatch(entry -> entry.getKey().equalsIgnoreCase(expectedName)
                        && entry.getValue().contains(expectedValue));
        if (!present) {
            throw new MediaStorageUnavailableException();
        }
    }
}
