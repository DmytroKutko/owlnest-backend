package dev.dkutko.owlnest.profile.service;

public class UsernameAlreadyInUseException extends RuntimeException {

    public UsernameAlreadyInUseException(String username) {
        super("Username '%s' is already in use".formatted(username));
    }

}
