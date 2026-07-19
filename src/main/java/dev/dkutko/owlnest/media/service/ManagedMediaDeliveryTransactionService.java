package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.domain.ManagedMedia;
import dev.dkutko.owlnest.media.domain.ManagedMediaStatus;
import dev.dkutko.owlnest.media.repository.ManagedMediaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ManagedMediaDeliveryTransactionService {

    private final ManagedMediaRepository mediaRepository;
    private final List<ManagedMediaDeliveryAuthorization> deliveryAuthorizations;

    public ManagedMediaDeliveryTransactionService(
            ManagedMediaRepository mediaRepository,
            List<ManagedMediaDeliveryAuthorization> deliveryAuthorizations
    ) {
        this.mediaRepository = mediaRepository;
        this.deliveryAuthorizations = List.copyOf(deliveryAuthorizations);
    }

    @Transactional(readOnly = true)
    public DeliveryTarget authorize(UUID mediaId, UUID viewerAccountId) {
        ManagedMedia media = mediaRepository.findById(mediaId)
                .orElseThrow(MediaNotFoundException::new);
        if (media.getStatus() != ManagedMediaStatus.ACTIVE
                || deliveryAuthorizations.stream().noneMatch(authorization -> authorization.canDeliver(
                        media.getPurpose(),
                        media.getId(),
                        media.getOwnerAccountId(),
                        viewerAccountId
                ))) {
            throw new MediaNotFoundException();
        }
        return new DeliveryTarget(media.getObjectKey());
    }

    record DeliveryTarget(String objectKey) {
    }
}
