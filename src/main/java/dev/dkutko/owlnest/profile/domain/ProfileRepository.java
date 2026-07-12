package dev.dkutko.owlnest.profile.domain;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {

    Optional<Profile> findByAccountId(UUID accountId);

    Profile save(Profile profile);

}
