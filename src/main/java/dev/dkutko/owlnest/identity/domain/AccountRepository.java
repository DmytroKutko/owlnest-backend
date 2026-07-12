package dev.dkutko.owlnest.identity.domain;

import java.util.Optional;

public interface AccountRepository {

    Optional<Account> findByProviderAndExternalSubject(String provider, String externalSubject);

    Account save(Account account);

}
