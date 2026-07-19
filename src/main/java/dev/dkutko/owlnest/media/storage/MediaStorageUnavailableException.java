package dev.dkutko.owlnest.media.storage;

public class MediaStorageUnavailableException extends RuntimeException {

    public MediaStorageUnavailableException() {
        super("Managed media storage is unavailable");
    }

    public MediaStorageUnavailableException(Throwable cause) {
        super("Managed media storage is unavailable", cause);
    }
}
