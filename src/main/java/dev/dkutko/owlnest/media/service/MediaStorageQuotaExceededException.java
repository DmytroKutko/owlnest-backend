package dev.dkutko.owlnest.media.service;

public class MediaStorageQuotaExceededException extends RuntimeException {

    public MediaStorageQuotaExceededException() {
        super("Managed media storage quota is exceeded");
    }
}
