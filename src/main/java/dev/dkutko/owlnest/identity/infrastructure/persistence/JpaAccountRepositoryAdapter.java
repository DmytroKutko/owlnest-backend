package dev.dkutko.owlnest.identity.infrastructure.persistence;

import dev.dkutko.owlnest.identity.domain.Account;
import dev.dkutko.owlnest.identity.domain.AccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaAccountRepositoryAdapter implements AccountRepository {

    private final SpringDataAccountRepository repository;

    public JpaAccountRepositoryAdapter(SpringDataAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Account> findByProviderAndExternalSubject(String provider, String externalSubject) {
        return repository.findByProviderAndExternalSubject(provider, externalSubject);
    }

    @Override
    public Account save(Account account) {
        return repository.save(account);
    }

}
