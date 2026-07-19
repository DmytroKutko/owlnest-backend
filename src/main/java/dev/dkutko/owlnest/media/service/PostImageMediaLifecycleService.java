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
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class PostImageMediaLifecycleService {

    private static final Duration DELETION_RETENTION = Duration.ofHours(24);

    private final ManagedMediaRepository mediaRepository;

    public PostImageMediaLifecycleService(ManagedMediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(
            UUID accountId,
            List<UUID> currentMediaIds,
            List<UUID> candidateMediaIds,
            Instant now
    ) {
        requireNoDuplicates(candidateMediaIds);
        Map<UUID, ManagedMedia> locked = lockMedia(currentMediaIds, candidateMediaIds);
        Set<UUID> currentIds = Set.copyOf(currentMediaIds);
        Set<UUID> candidateIds = Set.copyOf(candidateMediaIds);

        for (UUID candidateId : candidateMediaIds) {
            ManagedMedia candidate = locked.get(candidateId);
            if (candidate == null || !candidate.isOwnedBy(accountId)) {
                throw new MediaNotFoundException();
            }
            if (candidate.getPurpose() != ManagedMediaPurpose.POST_IMAGE) {
                throw new MediaPurposeMismatchException();
            }
            if (currentIds.contains(candidateId) && candidate.getStatus() == ManagedMediaStatus.ACTIVE) {
                continue;
            }
            if (candidate.getStatus() != ManagedMediaStatus.READY || candidate.isReadyExpiredAt(now)) {
                throw new MediaNotReadyException();
            }
        }

        for (UUID currentId : currentMediaIds) {
            if (!candidateIds.contains(currentId)) {
                requireCurrentPostImage(locked.get(currentId), accountId)
                        .detach(now, now.plus(DELETION_RETENTION));
            }
        }
        for (UUID candidateId : candidateMediaIds) {
            ManagedMedia candidate = locked.get(candidateId);
            if (candidate.getStatus() == ManagedMediaStatus.READY) {
                candidate.activatePostImage(now);
            }
        }
        locked.values().forEach(mediaRepository::save);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void detachAll(UUID accountId, List<UUID> currentMediaIds, Instant now) {
        Map<UUID, ManagedMedia> locked = lockMedia(currentMediaIds, List.of());
        for (UUID currentId : currentMediaIds) {
            ManagedMedia current = requireCurrentPostImage(locked.get(currentId), accountId);
            current.detach(now, now.plus(DELETION_RETENTION));
            mediaRepository.save(current);
        }
    }

    private Map<UUID, ManagedMedia> lockMedia(List<UUID> currentIds, List<UUID> candidateIds) {
        TreeSet<UUID> orderedIds = new TreeSet<>();
        orderedIds.addAll(currentIds);
        orderedIds.addAll(candidateIds);
        Map<UUID, ManagedMedia> locked = new LinkedHashMap<>();
        if (orderedIds.isEmpty()) {
            return locked;
        }
        for (ManagedMedia media : mediaRepository.findAllByIdsForUpdate(List.copyOf(orderedIds))) {
            locked.put(media.getId(), media);
        }
        return locked;
    }

    private static void requireNoDuplicates(List<UUID> ids) {
        if (Set.copyOf(ids).size() != ids.size()) {
            throw new IllegalArgumentException("managed media cannot be attached more than once");
        }
    }

    private static ManagedMedia requireCurrentPostImage(ManagedMedia media, UUID accountId) {
        if (media == null
                || !media.isOwnedBy(accountId)
                || media.getPurpose() != ManagedMediaPurpose.POST_IMAGE
                || media.getStatus() != ManagedMediaStatus.ACTIVE) {
            throw new IllegalStateException("Current post image lifecycle is inconsistent");
        }
        return media;
    }
}
