package dev.dkutko.owlnest;

import dev.dkutko.owlnest.media.storage.MediaObjectNotFoundException;
import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import dev.dkutko.owlnest.media.storage.MediaStorageUnavailableException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@TestConfiguration(proxyBeanMethods = false)
class RecordingMediaTestStorageConfiguration {

    @Bean
    @Primary
    RecordingMediaTestStorage recordingMediaTestStorage() {
        return new RecordingMediaTestStorage();
    }
}

final class RecordingMediaTestStorage implements MediaObjectStorage {

    private final AtomicInteger availabilityCalls = new AtomicInteger();
    private final List<UploadUrlRequest> uploadCalls = new CopyOnWriteArrayList<>();
    private final List<String> inspectCalls = new CopyOnWriteArrayList<>();
    private final List<ReadCall> readCalls = new CopyOnWriteArrayList<>();
    private final List<String> deleteCalls = new CopyOnWriteArrayList<>();
    private volatile boolean readUnavailable;

    @Override
    public void ensureAvailable() {
        availabilityCalls.incrementAndGet();
    }

    @Override
    public PresignedUpload createUploadUrl(UploadUrlRequest request) {
        uploadCalls.add(request);
        return new PresignedUpload(
                URI.create("https://uploads.example.test/object?signature=test"),
                request.expiresAt(),
                Map.of(
                        "Content-Type", request.contentType(),
                        "If-None-Match", "*"
                )
        );
    }

    @Override
    public ObjectMetadata inspect(String objectKey) {
        inspectCalls.add(objectKey);
        throw new MediaObjectNotFoundException();
    }

    @Override
    public PresignedRead createReadUrl(String objectKey, Instant expiresAt) {
        readCalls.add(new ReadCall(
                objectKey,
                expiresAt,
                Instant.now(),
                TransactionSynchronizationManager.isActualTransactionActive()
        ));
        if (readUnavailable) {
            throw new MediaStorageUnavailableException(
                    new IllegalStateException("provider credential and private object detail")
            );
        }
        return new PresignedRead(
                URI.create("https://downloads.example.test/object?signature=test"),
                expiresAt
        );
    }

    @Override
    public void delete(String objectKey) {
        deleteCalls.add(objectKey);
    }

    void failReadRequests() {
        readUnavailable = true;
    }

    List<ReadCall> readCalls() {
        return List.copyOf(readCalls);
    }

    int totalCalls() {
        return availabilityCalls.get()
                + uploadCalls.size()
                + inspectCalls.size()
                + readCalls.size()
                + deleteCalls.size();
    }

    void reset() {
        availabilityCalls.set(0);
        uploadCalls.clear();
        inspectCalls.clear();
        readCalls.clear();
        deleteCalls.clear();
        readUnavailable = false;
    }

    record ReadCall(
            String objectKey,
            Instant expiresAt,
            Instant observedAt,
            boolean transactionActive
    ) {
    }
}
