package dev.dkutko.owlnest.media.service;

public class MediaInUseException extends RuntimeException {

    public MediaInUseException() {
        super("Managed media is currently in use");
    }
}
