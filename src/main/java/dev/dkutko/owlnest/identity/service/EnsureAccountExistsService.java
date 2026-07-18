package dev.dkutko.owlnest.identity.service;

import dev.dkutko.owlnest.identity.domain.Account;
import dev.dkutko.owlnest.identity.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class EnsureAccountExistsService {

    private final AccountRepository accountRepository;

    public EnsureAccountExistsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account ensureExists(AuthenticatedIdentity identity) {
        Optional<Account> existingAccount = accountRepository.findByProviderAndExternalSubject(
                identity.provider(),
                identity.subject()
        );
        if (existingAccount.isPresent()) {
            return refreshAccount(existingAccount.get(), identity, Instant.now());
        }

        accountRepository.lockByProviderAndExternalSubject(identity.provider(), identity.subject());
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
