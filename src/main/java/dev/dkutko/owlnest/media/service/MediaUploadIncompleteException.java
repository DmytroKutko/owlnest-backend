package dev.dkutko.owlnest.media.service;

public class MediaUploadIncompleteException extends RuntimeException {

    public MediaUploadIncompleteException() {
        super("The expected media upload is not available");
    }
}
