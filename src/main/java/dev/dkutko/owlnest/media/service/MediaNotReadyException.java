package dev.dkutko.owlnest.media.service;

public class MediaNotReadyException extends RuntimeException {

    public MediaNotReadyException() {
        super("Managed media is not ready for attachment");
    }
}
