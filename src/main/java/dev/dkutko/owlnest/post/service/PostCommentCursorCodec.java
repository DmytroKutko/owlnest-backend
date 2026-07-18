package dev.dkutko.owlnest.post.service;

import java.nio.ByteBuffer;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

final class PostCommentCursorCodec {

    private static final String TRANSPORT_PREFIX = "v1.";
    private static final byte BINARY_VERSION = 1;
    private static final int MAX_TRANSPORT_LENGTH = 1_024;
    private static final int PAYLOAD_LENGTH = Byte.BYTES
            + Long.BYTES * 5
            + Integer.BYTES;
    private static final Pattern URL_SAFE_PAYLOAD = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private PostCommentCursorCodec() {
    }

    static String encode(UUID postId, Instant createdAt, UUID commentId) {
        Objects.requireNonNull(postId, "postId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(commentId, "commentId must not be null");
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_LENGTH);
        payload.put(BINARY_VERSION);
        putUuid(payload, postId);
        payload.putLong(createdAt.getEpochSecond());
        payload.putInt(createdAt.getNano());
        putUuid(payload, commentId);
        return TRANSPORT_PREFIX + ENCODER.encodeToString(payload.array());
    }

    static Position decode(String cursor, UUID expectedPostId) {
        Objects.requireNonNull(expectedPostId, "expectedPostId must not be null");
        if (cursor == null || cursor.isBlank() || cursor.length() > MAX_TRANSPORT_LENGTH) {
            throw invalidCursor();
        }
        if (!cursor.startsWith(TRANSPORT_PREFIX)) {
            throw invalidCursor();
        }

        String encodedPayload = cursor.substring(TRANSPORT_PREFIX.length());
        if (!URL_SAFE_PAYLOAD.matcher(encodedPayload).matches()) {
            throw invalidCursor();
        }

        byte[] decodedPayload;
        try {
            decodedPayload = DECODER.decode(encodedPayload);
        } catch (IllegalArgumentException exception) {
            throw invalidCursor(exception);
        }
        if (decodedPayload.length != PAYLOAD_LENGTH
                || !ENCODER.encodeToString(decodedPayload).equals(encodedPayload)) {
            throw invalidCursor();
        }

        ByteBuffer payload = ByteBuffer.wrap(decodedPayload);
        if (payload.get() != BINARY_VERSION) {
            throw invalidCursor();
        }
        UUID cursorPostId = readUuid(payload);
        if (!cursorPostId.equals(expectedPostId)) {
            throw invalidCursor();
        }

        long epochSecond = payload.getLong();
        int nano = payload.getInt();
        if (nano < 0 || nano > 999_999_999) {
            throw invalidCursor();
        }
        Instant createdAt;
        try {
            createdAt = Instant.ofEpochSecond(epochSecond, nano);
        } catch (DateTimeException exception) {
            throw invalidCursor(exception);
        }
        return new Position(createdAt, readUuid(payload));
    }

    private static void putUuid(ByteBuffer target, UUID value) {
        target.putLong(value.getMostSignificantBits());
        target.putLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer source) {
        return new UUID(source.getLong(), source.getLong());
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("Invalid post comment cursor");
    }

    private static IllegalArgumentException invalidCursor(Exception cause) {
        return new IllegalArgumentException("Invalid post comment cursor", cause);
    }

    record Position(Instant createdAt, UUID commentId) {
    }
}
