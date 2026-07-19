package dev.dkutko.owlnest.media.service;

public class MediaPurposeMismatchException extends RuntimeException {

    public MediaPurposeMismatchException() {
        super("Managed media purpose does not match the requested attachment");
    }
}
