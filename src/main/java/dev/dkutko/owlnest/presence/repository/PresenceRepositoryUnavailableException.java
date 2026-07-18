package dev.dkutko.owlnest.presence.repository;

public class PresenceRepositoryUnavailableException extends RuntimeException {

    public PresenceRepositoryUnavailableException(Throwable cause) {
        super("Online presence is temporarily unavailable", cause);
    }

}
