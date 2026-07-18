package dev.dkutko.owlnest.identity.repository;

import dev.dkutko.owlnest.identity.domain.Account;

import java.util.Optional;

public interface AccountRepository {

    void lockByProviderAndExternalSubject(String provider, String externalSubject);

    Optional<Account> findByProviderAndExternalSubject(String provider, String externalSubject);

    Account save(Account account);

}
