package dev.dkutko.owlnest.media.service;

public class MediaUploadExpiredException extends RuntimeException {

    public MediaUploadExpiredException() {
        super("The media upload opportunity has expired");
    }
}
