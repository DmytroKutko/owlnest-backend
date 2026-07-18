package dev.dkutko.owlnest.presence.repository;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Repository
public class RedisPresenceRepository implements PresenceRepository {

    private static final String KEY_PREFIX = "presence:account:";

    private final StringRedisTemplate redisTemplate;

    public RedisPresenceRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void markOnline(UUID accountId, Instant lastActivityAt, Duration timeToLive) {
        try {
            redisTemplate.opsForValue().set(key(accountId), lastActivityAt.toString(), timeToLive);
        } catch (DataAccessException exception) {
            throw new PresenceRepositoryUnavailableException(exception);
        }
    }

    @Override
    public boolean isOnline(UUID accountId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(accountId)));
        } catch (DataAccessException exception) {
            throw new PresenceRepositoryUnavailableException(exception);
        }
    }

    private String key(UUID accountId) {
        return KEY_PREFIX + accountId;
    }

}
