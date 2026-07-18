package dev.dkutko.owlnest.post.domain;

public final class PostTextNormalization {

    private PostTextNormalization() {
    }

    public static String stripBoundaryWhitespaceAndControls(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isWhitespaceOrControl(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isWhitespaceOrControl(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    public static boolean containsOnlyWhitespaceOrControls(String value) {
        if (value.isEmpty()) {
            return true;
        }
        return value.codePoints().allMatch(PostTextNormalization::isWhitespaceOrControl);
    }

    private static boolean isWhitespaceOrControl(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.isISOControl(codePoint);
    }
}
