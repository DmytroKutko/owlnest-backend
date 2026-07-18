package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.post.domain.Post;
import dev.dkutko.owlnest.post.domain.PostComment;
import dev.dkutko.owlnest.post.repository.PostCommentRepository;
import dev.dkutko.owlnest.post.repository.PostRepository;
import dev.dkutko.owlnest.profile.service.CurrentProfile;
import dev.dkutko.owlnest.profile.service.GetOrCreateCurrentProfileService;
import dev.dkutko.owlnest.profile.service.GetProfileSummaryService;
import dev.dkutko.owlnest.profile.service.ProfileSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CreatePostCommentService {

    private final GetOrCreateCurrentProfileService currentProfileService;
    private final GetProfileSummaryService profileSummaryService;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;

    public CreatePostCommentService(
            GetOrCreateCurrentProfileService currentProfileService,
            GetProfileSummaryService profileSummaryService,
            PostRepository postRepository,
            PostCommentRepository postCommentRepository
    ) {
        this.currentProfileService = currentProfileService;
        this.profileSummaryService = profileSummaryService;
        this.postRepository = postRepository;
        this.postCommentRepository = postCommentRepository;
    }

    @Transactional
    public PostCommentItem create(UUID postId, CreatePostCommentCommand command) {
        CurrentProfile actor = currentProfileService.getOrCreate();
        ProfileSummary author = profileSummaryService.getByAccountId(actor.accountId());
        Post post = postRepository.findActiveByIdForUpdate(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        Instant createdAt = postCommentRepository.nextCreatedAt(postId);
        PostComment comment = PostComment.create(
                UUID.randomUUID(),
                postId,
                actor.accountId(),
                command.text(),
                createdAt
        );

        post.recordCommentCreated();
        postCommentRepository.save(comment);
        postCommentRepository.flush();

        return new PostCommentItem(
                comment.getId(),
                comment.getPostId(),
                comment.getText(),
                author,
                comment.getCreatedAt()
        );
    }
}
