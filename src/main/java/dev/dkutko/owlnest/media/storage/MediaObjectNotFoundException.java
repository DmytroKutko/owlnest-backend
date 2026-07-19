package dev.dkutko.owlnest.media.storage;

public class MediaObjectNotFoundException extends RuntimeException {

    public MediaObjectNotFoundException() {
        super("Managed media object was not found");
    }
}
