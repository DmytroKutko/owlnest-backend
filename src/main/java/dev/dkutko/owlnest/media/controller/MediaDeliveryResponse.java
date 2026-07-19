package dev.dkutko.owlnest.media.controller;

import dev.dkutko.owlnest.media.service.MediaDelivery;

import java.net.URI;
import java.time.Instant;

public record MediaDeliveryResponse(
        URI url,
        Instant expiresAt
) {

    static MediaDeliveryResponse from(MediaDelivery delivery) {
        return new MediaDeliveryResponse(delivery.url(), delivery.expiresAt());
    }
}
