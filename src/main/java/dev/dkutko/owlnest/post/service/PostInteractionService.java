package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.post.repository.PostInteractionRepository;
import dev.dkutko.owlnest.post.repository.PostRepository;
import dev.dkutko.owlnest.profile.service.CurrentProfile;
import dev.dkutko.owlnest.profile.service.GetOrCreateCurrentProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PostInteractionService {

    private final GetOrCreateCurrentProfileService currentProfileService;
    private final PostRepository postRepository;
    private final PostInteractionRepository postInteractionRepository;

    public PostInteractionService(
            GetOrCreateCurrentProfileService currentProfileService,
            PostRepository postRepository,
            PostInteractionRepository postInteractionRepository
    ) {
        this.currentProfileService = currentProfileService;
        this.postRepository = postRepository;
        this.postInteractionRepository = postInteractionRepository;
    }

    @Transactional
    public void setLiked(UUID postId) {
        mutate(postId, Interaction.LIKE, true);
    }

    @Transactional
    public void clearLiked(UUID postId) {
        mutate(postId, Interaction.LIKE, false);
    }

    @Transactional
    public void setBookmarked(UUID postId) {
        mutate(postId, Interaction.BOOKMARK, true);
    }

    @Transactional
    public void clearBookmarked(UUID postId) {
        mutate(postId, Interaction.BOOKMARK, false);
    }

    @Transactional
    public void setReposted(UUID postId) {
        mutate(postId, Interaction.REPOST, true);
    }

    @Transactional
    public void clearReposted(UUID postId) {
        mutate(postId, Interaction.REPOST, false);
    }

    private void mutate(UUID postId, Interaction interaction, boolean enabled) {
        CurrentProfile actor = currentProfileService.getOrCreate();
        postRepository.findActiveByIdForUpdate(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        UUID accountId = actor.accountId();
        boolean changed = enabled
                ? addInteraction(interaction, postId, accountId)
                : removeInteraction(interaction, postId, accountId);
        if (changed && interaction != Interaction.BOOKMARK) {
            int delta = enabled ? 1 : -1;
            boolean counterAdjusted = interaction == Interaction.LIKE
                    ? postInteractionRepository.adjustLikeCount(postId, delta)
                    : postInteractionRepository.adjustRepostCount(postId, delta);
            if (!counterAdjusted) {
                throw new IllegalStateException("Post interaction counter could not be adjusted");
            }
        }
    }

    private boolean addInteraction(Interaction interaction, UUID postId, UUID accountId) {
        Instant now = Instant.now();
        return switch (interaction) {
            case LIKE -> postInteractionRepository.addLike(postId, accountId, now);
            case BOOKMARK -> postInteractionRepository.addBookmark(postId, accountId, now);
            case REPOST -> postInteractionRepository.addRepost(postId, accountId, now);
        };
    }

    private boolean removeInteraction(Interaction interaction, UUID postId, UUID accountId) {
        return switch (interaction) {
            case LIKE -> postInteractionRepository.removeLike(postId, accountId);
            case BOOKMARK -> postInteractionRepository.removeBookmark(postId, accountId);
            case REPOST -> postInteractionRepository.removeRepost(postId, accountId);
        };
    }

    private enum Interaction {
        LIKE,
        BOOKMARK,
        REPOST
    }
}
