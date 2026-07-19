package dev.dkutko.owlnest.media.service;

import java.util.UUID;

public interface ManagedMediaDeliveryAuthorization {

    boolean canDeliverActiveAvatar(UUID mediaId, UUID ownerAccountId, UUID viewerAccountId);
}
