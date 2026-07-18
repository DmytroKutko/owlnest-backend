package dev.dkutko.owlnest.presence.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface PresenceRepository {

    void markOnline(UUID accountId, Instant lastActivityAt, Duration timeToLive);

    boolean isOnline(UUID accountId);

}
