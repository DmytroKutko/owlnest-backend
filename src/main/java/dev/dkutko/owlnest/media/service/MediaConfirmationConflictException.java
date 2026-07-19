package dev.dkutko.owlnest.media.service;

public class MediaConfirmationConflictException extends RuntimeException {

    public MediaConfirmationConflictException() {
        super("Managed media cannot be confirmed in its current state");
    }
}
