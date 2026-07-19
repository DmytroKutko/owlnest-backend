package dev.dkutko.owlnest.media.service;

import java.net.URI;
import java.time.Instant;

public record MediaDelivery(
        URI url,
        Instant expiresAt
) {
}
