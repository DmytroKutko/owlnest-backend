package dev.dkutko.owlnest.media.service;

public class MediaNotFoundException extends RuntimeException {

    public MediaNotFoundException() {
        super("Managed media was not found");
    }
}
