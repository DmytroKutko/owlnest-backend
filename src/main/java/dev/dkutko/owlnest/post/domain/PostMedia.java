package dev.dkutko.owlnest.post.domain;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public record PostMedia(
        PostMediaType type,
        URI url,
        UUID managedMediaId
) {

    private static final int MAX_URL_CODE_POINTS = 2_048;

    public PostMedia {
        Objects.requireNonNull(type, "type must not be null");
        if ((url == null) == (managedMediaId == null)) {
            throw new IllegalArgumentException("post media must have exactly one of url or managedMediaId");
        }
        if (managedMediaId != null && type != PostMediaType.IMAGE) {
            throw new IllegalArgumentException("managed post media currently supports IMAGE only");
        }
        if (url != null) {
            String rawUrl = url.toString();
            PostTextValidation.requireStorableUnicode(rawUrl, "media URL");
            if (rawUrl.isBlank()) {
                throw new IllegalArgumentException("media URL must not be blank");
            }
            if (PostTextValidation.codePointLength(rawUrl) > MAX_URL_CODE_POINTS) {
                throw new IllegalArgumentException("media URL must not exceed 2048 Unicode code points");
            }
            if (!url.isAbsolute()
                    || !"https".equalsIgnoreCase(url.getScheme())
                    || url.isOpaque()
                    || url.getRawAuthority() == null
                    || url.getRawAuthority().isEmpty()) {
                throw new IllegalArgumentException(
                        "media URL must be a hierarchical absolute HTTPS URI with a nonempty authority"
                );
            }
        }
    }

    public PostMedia(PostMediaType type, URI url) {
        this(type, url, null);
    }

    public static PostMedia managedImage(UUID mediaId) {
        return new PostMedia(PostMediaType.IMAGE, null, mediaId);
    }
}
