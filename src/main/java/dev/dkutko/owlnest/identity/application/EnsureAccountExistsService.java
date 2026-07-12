package dev.dkutko.owlnest.identity.application;

import dev.dkutko.owlnest.identity.domain.Account;
import dev.dkutko.owlnest.identity.domain.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EnsureAccountExistsService {

    private final AccountRepository accountRepository;

    public EnsureAccountExistsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account ensureExists(AuthenticatedIdentity identity) {
        Instant now = Instant.now();
        return accountRepository
                .findByProviderAndExternalSubject(identity.provider(), identity.subject())
                .map(account -> refreshAccount(account, identity, now))
                .orElseGet(() -> accountRepository.save(Account.create(
                        identity.provider(),
                        identity.subject(),
                        identity.email(),
                        identity.emailVerified(),
                        now
                )));
    }

    private Account refreshAccount(Account account, AuthenticatedIdentity identity, Instant now) {
        account.refreshFrom(identity.email(), identity.emailVerified(), now);
        return account;
    }

}
