package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.domain.ManagedMedia;
import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;
import dev.dkutko.owlnest.media.domain.ManagedMediaStatus;
import dev.dkutko.owlnest.media.repository.ManagedMediaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class AvatarMediaLifecycleService {

    private static final Duration DELETION_RETENTION = Duration.ofHours(24);

    private final ManagedMediaRepository mediaRepository;

    public AvatarMediaLifecycleService(ManagedMediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(UUID accountId, UUID currentMediaId, UUID candidateMediaId, Instant now) {
        Map<UUID, ManagedMedia> lockedMedia = lockMedia(currentMediaId, candidateMediaId);
        ManagedMedia candidate = lockedMedia.get(candidateMediaId);
        if (candidate == null || !candidate.isOwnedBy(accountId)) {
            throw new MediaNotFoundException();
        }
        if (candidate.getPurpose() != ManagedMediaPurpose.AVATAR) {
            throw new MediaPurposeMismatchException();
        }
        if (candidateMediaId.equals(currentMediaId) && candidate.getStatus() == ManagedMediaStatus.ACTIVE) {
            return;
        }
        if (candidate.getStatus() != ManagedMediaStatus.READY || candidate.isReadyExpiredAt(now)) {
            throw new MediaNotReadyException();
        }

        if (currentMediaId != null) {
            ManagedMedia current = requireCurrentAvatar(lockedMedia.get(currentMediaId), accountId);
            current.supersede(now, now.plus(DELETION_RETENTION));
            mediaRepository.save(current);
        }
        candidate.activateAvatar(now);
        mediaRepository.save(candidate);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remove(UUID accountId, UUID currentMediaId, Instant now) {
        ManagedMedia current = requireCurrentAvatar(
                lockMedia(currentMediaId, currentMediaId).get(currentMediaId),
                accountId
        );
        current.removeByUser(now, now.plus(DELETION_RETENTION));
        mediaRepository.save(current);
    }

    private Map<UUID, ManagedMedia> lockMedia(UUID currentMediaId, UUID candidateMediaId) {
        TreeSet<UUID> orderedIds = new TreeSet<>();
        if (currentMediaId != null) {
            orderedIds.add(currentMediaId);
        }
        orderedIds.add(candidateMediaId);
        List<UUID> ids = List.copyOf(orderedIds);
        Map<UUID, ManagedMedia> locked = new LinkedHashMap<>();
        for (ManagedMedia media : mediaRepository.findAllByIdsForUpdate(ids)) {
            locked.put(media.getId(), media);
        }
        return locked;
    }

    private static ManagedMedia requireCurrentAvatar(ManagedMedia media, UUID accountId) {
        if (media == null
                || !media.isOwnedBy(accountId)
                || media.getPurpose() != ManagedMediaPurpose.AVATAR
                || media.getStatus() != ManagedMediaStatus.ACTIVE) {
            throw new IllegalStateException("Current profile avatar lifecycle is inconsistent");
        }
        return media;
    }
}
