package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;
import dev.dkutko.owlnest.media.service.ManagedMediaDeliveryAuthorization;
import dev.dkutko.owlnest.post.repository.PostContentRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostImageDeliveryAuthorization implements ManagedMediaDeliveryAuthorization {

    private final PostContentRepository postContentRepository;

    public PostImageDeliveryAuthorization(PostContentRepository postContentRepository) {
        this.postContentRepository = postContentRepository;
    }

    @Override
    public boolean canDeliver(
            ManagedMediaPurpose purpose,
            UUID mediaId,
            UUID ownerAccountId,
            UUID viewerAccountId
    ) {
        return purpose == ManagedMediaPurpose.POST_IMAGE
                && postContentRepository.hasActivePostImageAssociation(mediaId, ownerAccountId);
    }
}
