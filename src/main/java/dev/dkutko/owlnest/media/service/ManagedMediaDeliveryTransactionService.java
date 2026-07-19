package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.domain.ManagedMedia;
import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;
import dev.dkutko.owlnest.media.domain.ManagedMediaStatus;
import dev.dkutko.owlnest.media.repository.ManagedMediaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ManagedMediaDeliveryTransactionService {

    private final ManagedMediaRepository mediaRepository;
    private final ManagedMediaDeliveryAuthorization deliveryAuthorization;

    public ManagedMediaDeliveryTransactionService(
            ManagedMediaRepository mediaRepository,
            ManagedMediaDeliveryAuthorization deliveryAuthorization
    ) {
        this.mediaRepository = mediaRepository;
        this.deliveryAuthorization = deliveryAuthorization;
    }

    @Transactional(readOnly = true)
    public DeliveryTarget authorize(UUID mediaId, UUID viewerAccountId) {
        ManagedMedia media = mediaRepository.findById(mediaId)
                .orElseThrow(MediaNotFoundException::new);
        if (media.getPurpose() != ManagedMediaPurpose.AVATAR
                || media.getStatus() != ManagedMediaStatus.ACTIVE
                || !deliveryAuthorization.canDeliverActiveAvatar(
                        media.getId(),
                        media.getOwnerAccountId(),
                        viewerAccountId
                )) {
            throw new MediaNotFoundException();
        }
        return new DeliveryTarget(media.getObjectKey());
    }

    record DeliveryTarget(String objectKey) {
    }
}
