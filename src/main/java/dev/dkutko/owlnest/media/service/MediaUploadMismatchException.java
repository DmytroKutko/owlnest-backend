package dev.dkutko.owlnest.media.service;

public class MediaUploadMismatchException extends RuntimeException {

    public MediaUploadMismatchException() {
        super("Uploaded media does not match the reservation");
    }
}
