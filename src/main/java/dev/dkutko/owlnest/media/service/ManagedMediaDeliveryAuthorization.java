package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;

import java.util.UUID;

public interface ManagedMediaDeliveryAuthorization {

    boolean canDeliver(
            ManagedMediaPurpose purpose,
            UUID mediaId,
            UUID ownerAccountId,
            UUID viewerAccountId
    );
}
