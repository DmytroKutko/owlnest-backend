package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.post.domain.PostMedia;
import dev.dkutko.owlnest.post.domain.PostTextNormalization;
import dev.dkutko.owlnest.post.domain.PostTextValidation;

import java.util.List;
import java.util.UUID;

public record PostWriteCommand(
        String title,
        String description,
        List<String> labels,
        List<PostMedia> media
) {

    private static final int MAX_LABELS = 5;
    private static final int MAX_LABEL_LENGTH = 50;
    private static final int MAX_MEDIA = 10;

    public PostWriteCommand {
        labels = normalizeLabels(labels);
        media = normalizeMedia(media);
    }

    private static List<String> normalizeLabels(List<String> labels) {
        if (labels == null) {
            return List.of();
        }
        if (labels.size() > MAX_LABELS) {
            throw new IllegalArgumentException("labels must not contain more than 5 values");
        }
        List<String> normalized = labels.stream()
                .map(PostWriteCommand::normalizeLabel)
                .toList();
        return List.copyOf(normalized);
    }

    private static String normalizeLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("label must not be blank");
        }
        PostTextValidation.requireStorableUnicode(label, "label");
        String normalized = PostTextNormalization.stripBoundaryWhitespaceAndControls(label);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (PostTextValidation.codePointLength(normalized) > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("label must not exceed 50 characters");
        }
        return normalized;
    }

    private static List<PostMedia> normalizeMedia(List<PostMedia> media) {
        if (media == null) {
            return List.of();
        }
        if (media.size() > MAX_MEDIA) {
            throw new IllegalArgumentException("media must not contain more than 10 values");
        }
        if (media.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("media must not contain null values");
        }
        return List.copyOf(media);
    }

    public List<UUID> managedMediaIds() {
        return media.stream()
                .map(PostMedia::managedMediaId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
