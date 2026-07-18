package dev.dkutko.owlnest.post.domain;

import java.net.URI;
import java.util.Objects;

public record PostMedia(
        PostMediaType type,
        URI url
) {

    private static final int MAX_URL_CODE_POINTS = 2_048;

    public PostMedia {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(url, "url must not be null");
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
