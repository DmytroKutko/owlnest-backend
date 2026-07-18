package dev.dkutko.owlnest.profile.service;

import java.util.UUID;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(UUID accountId) {
        super("Profile not found for account " + accountId);
    }

}
