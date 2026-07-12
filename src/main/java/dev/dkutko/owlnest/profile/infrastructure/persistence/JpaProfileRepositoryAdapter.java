package dev.dkutko.owlnest.profile.infrastructure.persistence;

import dev.dkutko.owlnest.profile.domain.Profile;
import dev.dkutko.owlnest.profile.domain.ProfileRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaProfileRepositoryAdapter implements ProfileRepository {

    private final SpringDataProfileRepository repository;

    public JpaProfileRepositoryAdapter(SpringDataProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Profile> findByAccountId(UUID accountId) {
        return repository.findById(accountId);
    }

    @Override
    public boolean existsByUsernameIgnoreCaseAndAccountIdNot(String username, UUID accountId) {
        return repository.existsByUsernameIgnoreCaseAndAccountIdNot(username, accountId);
    }

    @Override
    public Profile save(Profile profile) {
        return repository.save(profile);
    }

}
