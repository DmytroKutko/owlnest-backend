package dev.dkutko.owlnest.post.repository;

import dev.dkutko.owlnest.post.domain.PostMedia;

import java.util.List;
import java.util.UUID;

public interface PostContentRepository {

    void replace(UUID postId, List<String> labels, List<PostMedia> media);
}
