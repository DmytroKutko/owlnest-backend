package dev.dkutko.owlnest.identity.repository;

import dev.dkutko.owlnest.identity.domain.Account;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AccountRepositoryImpl implements AccountRepository {

    private static final String LOCK_IDENTITY_SQL = """
            SELECT pg_advisory_xact_lock(hashtextextended(?, 0))
            """;

    private final SpringDataAccountRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public AccountRepositoryImpl(
            SpringDataAccountRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void lockByProviderAndExternalSubject(String provider, String externalSubject) {
        String identityKey = "identity:" + provider.length() + ":" + provider
                + ":" + externalSubject.length() + ":" + externalSubject;
        jdbcTemplate.execute(LOCK_IDENTITY_SQL, (PreparedStatementCallback<Void>) statement -> {
            statement.setString(1, identityKey);
            statement.execute();
            return null;
        });
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
