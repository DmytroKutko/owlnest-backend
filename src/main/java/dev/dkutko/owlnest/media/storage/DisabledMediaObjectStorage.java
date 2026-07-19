package dev.dkutko.owlnest.media.storage;

import java.time.Instant;

public class DisabledMediaObjectStorage implements MediaObjectStorage {

    @Override
    public void ensureAvailable() {
        throw new MediaStorageUnavailableException();
    }

    @Override
    public PresignedUpload createUploadUrl(UploadUrlRequest request) {
        throw new MediaStorageUnavailableException();
    }

    @Override
    public ObjectMetadata inspect(String objectKey) {
        throw new MediaStorageUnavailableException();
    }

    @Override
    public PresignedRead createReadUrl(String objectKey, Instant expiresAt) {
        throw new MediaStorageUnavailableException();
    }

    @Override
    public void delete(String objectKey) {
        throw new MediaStorageUnavailableException();
    }
}
