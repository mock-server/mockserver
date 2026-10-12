package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;

/**
 * The {@code image_description} Bedrock Cohere Embed v3 returns for each {@code images} entry:
 * {@code {"width":long,"height":long,"format":string,"bit_depth":long}}, read from the PNG
 * {@code IHDR} chunk or the JPEG start-of-frame segment of a base64 data URI. Only the header
 * bytes are decoded, straight from the base64 text, and the image is never decoded. An image
 * whose header cannot be read gets zero width, height and bit depth, and the format its data URI
 * names; {@link #describe} never throws. {@code bit_depth} is bits per pixel, as Cohere's example
 * (an 8-bit RGB JPEG is 24).
 */
final class CohereImageDescription {

    /** Bounds the JPEG marker walk, so a stream of tiny segments cannot make it long. */
    static final int MAX_JPEG_SEGMENTS = 1024;

    private static final int MAX_MEDIA_TYPE_LENGTH = 64;

    private static final int[] PNG_SIGNATURE = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final long width;
    private final long height;
    private final String format;
    private final long bitDepth;

    private CohereImageDescription(long width, long height, String format, long bitDepth) {
        this.width = width;
        this.height = height;
        this.format = format;
        this.bitDepth = bitDepth;
    }

    /**
     * Rejects a v3 {@code images} entry the real API refuses: AWS accepts only a data URI of
     * {@code image/jpeg} or {@code image/png} ({@code image/jpg} is AWS's own example's spelling).
     */
    static void checkSupported(JsonNode image) {
        String mediaType = image != null && image.isTextual() ? mediaType(image.asText()) : null;
        if (!"image/png".equals(mediaType) && !"image/jpeg".equals(mediaType) && !"image/jpg".equals(mediaType)) {
            throw new EmbeddingWire.InvalidEmbeddingRequestException("images",
                "images must be data URIs in image/jpeg or image/png format"
                    + (mediaType != null && !mediaType.isEmpty() ? ", got " + mediaType : ""));
        }
    }

    static CohereImageDescription describe(JsonNode image) {
        String uri = image != null && image.isTextual() ? image.asText() : "";
        Base64Bytes bytes = Base64Bytes.payloadOf(uri);
        CohereImageDescription description = png(bytes);
        if (description == null) {
            description = jpeg(bytes);
        }
        return description != null ? description : new CohereImageDescription(0, 0, formatFromMediaType(uri), 0);
    }

    void writeTo(ObjectNode node) {
        node.put("width", width);
        node.put("height", height);
        node.put("format", format);
        node.put("bit_depth", bitDepth);
    }

    private static CohereImageDescription png(Base64Bytes bytes) {
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes.at(i) != PNG_SIGNATURE[i]) {
                return null;
            }
        }
        if (bytes.at(12) != 'I' || bytes.at(13) != 'H' || bytes.at(14) != 'D' || bytes.at(15) != 'R') {
            return null;
        }
        long width = bytes.unsigned32(16);
        long height = bytes.unsigned32(20);
        int depth = bytes.at(24);
        int colourType = bytes.at(25);
        if (width < 0 || height < 0 || depth < 0 || colourType < 0) {
            return null;
        }
        return new CohereImageDescription(width, height, "png", (long) depth * pngChannels(colourType));
    }

    private static int pngChannels(int colourType) {
        switch (colourType) {
            case 0: // greyscale
            case 3: // palette index
                return 1;
            case 2:
                return 3;
            case 4: // greyscale with alpha
                return 2;
            case 6:
                return 4;
            default:
                return 0;
        }
    }

    private static CohereImageDescription jpeg(Base64Bytes bytes) {
        if (bytes.at(0) != 0xFF || bytes.at(1) != 0xD8) {
            return null;
        }
        long position = 2;
        for (int segment = 0; segment < MAX_JPEG_SEGMENTS; segment++) {
            if (bytes.at(position) != 0xFF) {
                return null;
            }
            int marker = bytes.at(position + 1);
            while (marker == 0xFF) {
                position++;
                marker = bytes.at(position + 1);
            }
            if (marker < 0 || marker == 0xD9 || marker == 0xDA) {
                return null;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                position += 2;
                continue;
            }
            int length = bytes.unsigned16(position + 2);
            if (length < 2) {
                return null;
            }
            if (isStartOfFrame(marker)) {
                int precision = bytes.at(position + 4);
                int height = bytes.unsigned16(position + 5);
                int width = bytes.unsigned16(position + 7);
                int components = bytes.at(position + 9);
                if (precision < 0 || height < 0 || width < 0 || components < 0) {
                    return null;
                }
                return new CohereImageDescription(width, height, "jpeg", (long) precision * components);
            }
            position += 2 + length;
        }
        return null;
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    private static String formatFromMediaType(String uri) {
        String mediaType = mediaType(uri);
        if (mediaType == null) {
            return "";
        }
        String subtype = mediaType.substring(mediaType.indexOf('/') + 1);
        return "jpg".equals(subtype) ? "jpeg" : subtype;
    }

    /**
     * The lower-cased media type of a data URI, read from at most {@code MAX_MEDIA_TYPE_LENGTH}
     * characters, or {@code null} when {@code uri} is not a data URI or its media type is longer.
     */
    private static String mediaType(String uri) {
        if (!uri.regionMatches(true, 0, "data:", 0, 5)) {
            return null;
        }
        int limit = Math.min(uri.length(), 5 + MAX_MEDIA_TYPE_LENGTH);
        int end = 5;
        while (end < limit && uri.charAt(end) != ';' && uri.charAt(end) != ',') {
            end++;
        }
        if (end == limit && limit < uri.length()) {
            return null;
        }
        return uri.substring(5, end).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Random access to the bytes of a base64 payload without decoding the rest of it: byte
     * {@code i} is read from the two characters of quad {@code i / 3} that carry it. A byte
     * outside the payload, or one carried by a character that is not base64, reads as -1.
     */
    static final class Base64Bytes {
        private final String text;
        private final int start;

        private Base64Bytes(String text, int start) {
            this.text = text;
            this.start = start;
        }

        static Base64Bytes payloadOf(String uri) {
            int comma = uri.regionMatches(true, 0, "data:", 0, 5) ? uri.indexOf(',') : -1;
            return new Base64Bytes(uri, comma + 1);
        }

        int at(long index) {
            if (index < 0) {
                return -1;
            }
            int remainder = (int) (index % 3);
            long first = start + (index / 3) * 4 + remainder;
            if (first + 1 >= text.length()) {
                return -1;
            }
            int a = value(text.charAt((int) first));
            int b = value(text.charAt((int) first + 1));
            if (a < 0 || b < 0) {
                return -1;
            }
            switch (remainder) {
                case 0:
                    return (a << 2) | (b >> 4);
                case 1:
                    return ((a & 0x0F) << 4) | (b >> 2);
                default:
                    return ((a & 0x03) << 6) | b;
            }
        }

        int unsigned16(long index) {
            int high = at(index);
            int low = at(index + 1);
            return high < 0 || low < 0 ? -1 : (high << 8) | low;
        }

        long unsigned32(long index) {
            int high = unsigned16(index);
            int low = unsigned16(index + 2);
            return high < 0 || low < 0 ? -1 : ((long) high << 16) | low;
        }

        private static int value(char c) {
            if (c >= 'A' && c <= 'Z') {
                return c - 'A';
            }
            if (c >= 'a' && c <= 'z') {
                return c - 'a' + 26;
            }
            if (c >= '0' && c <= '9') {
                return c - '0' + 52;
            }
            if (c == '+' || c == '-') {
                return 62;
            }
            if (c == '/' || c == '_') {
                return 63;
            }
            return -1;
        }
    }
}
