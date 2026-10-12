package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * The image description Bedrock Cohere Embed v3 returns per image ({@code width}, {@code height},
 * {@code format}, {@code bit_depth}), read from images written by the JDK's own PNG and JPEG
 * encoders rather than from bytes shaped to suit the parser.
 */
public class CohereImageDescriptionTest {

    static byte[] encode(int width, int height, int type, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, 0x80000000 | (x * 40) << 16 | (y * 30) << 8 | 0x7f);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat("the JDK has a " + format + " writer", ImageIO.write(image, format, out), is(true));
        return out.toByteArray();
    }

    static String dataUri(String mediaType, byte[] bytes) {
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String describe(String uri) {
        return describe(TextNode.valueOf(uri));
    }

    private static String describe(JsonNode image) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        CohereImageDescription.describe(image).writeTo(node);
        return node.toString();
    }

    private static String expected(long width, long height, String format, long bitDepth) {
        return "{\"width\":" + width + ",\"height\":" + height + ",\"format\":\"" + format + "\",\"bit_depth\":" + bitDepth + "}";
    }

    @Test
    public void shouldDescribePngsByWidthHeightAndBitsPerPixel() throws IOException {
        assertThat(describe(dataUri("image/png", encode(7, 5, BufferedImage.TYPE_INT_RGB, "png"))), is(expected(7, 5, "png", 24)));
        assertThat(describe(dataUri("image/png", encode(300, 2, BufferedImage.TYPE_INT_ARGB, "png"))), is(expected(300, 2, "png", 32)));
        assertThat(describe(dataUri("image/png", encode(3, 4, BufferedImage.TYPE_BYTE_GRAY, "png"))), is(expected(3, 4, "png", 8)));
    }

    @Test
    public void shouldDescribeJpegsByWidthHeightAndBitsPerPixel() throws IOException {
        // Cohere's own example: an 8-bit RGB JPEG is "format":"jpeg","bit_depth":24
        assertThat(describe(dataUri("image/jpeg", encode(9, 4, BufferedImage.TYPE_INT_RGB, "jpg"))), is(expected(9, 4, "jpeg", 24)));
        assertThat(describe(dataUri("image/jpeg", encode(640, 480, BufferedImage.TYPE_BYTE_GRAY, "jpg"))), is(expected(640, 480, "jpeg", 8)));
    }

    @Test
    public void shouldDescribeAProgressiveJpeg() {
        // SOF2 (progressive), 1x1, one 8-bit component
        String progressive = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP////////////////////////////////////////"
            + "//////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=";
        assertThat(describe(progressive), is(expected(1, 1, "jpeg", 8)));
    }

    @Test
    public void shouldSkipLargeApplicationSegmentsBeforeTheFrameHeader() throws IOException {
        byte[] jpeg = encode(33, 17, BufferedImage.TYPE_INT_RGB, "jpg");
        byte[] exif = segment(0xE1, 65_000);
        byte[] icc = segment(0xE2, 40_000);
        byte[] spliced = new byte[jpeg.length + exif.length + icc.length + 2];
        spliced[0] = (byte) 0xFF;
        spliced[1] = (byte) 0xD8;
        System.arraycopy(exif, 0, spliced, 2, exif.length);
        System.arraycopy(icc, 0, spliced, 2 + exif.length, icc.length);
        System.arraycopy(jpeg, 2, spliced, 2 + exif.length + icc.length, jpeg.length - 2);
        spliced[spliced.length - 2] = (byte) 0xFF;
        spliced[spliced.length - 1] = (byte) 0xD9;
        assertThat(describe(dataUri("image/jpeg", spliced)), is(expected(33, 17, "jpeg", 24)));
    }

    @Test
    public void shouldNameTheFormatFromTheImageBytesOverTheMediaType() throws IOException {
        assertThat(describe(dataUri("image/jpeg", encode(2, 3, BufferedImage.TYPE_INT_RGB, "png"))), is(expected(2, 3, "png", 24)));
        assertThat(describe(dataUri("image/jpg", encode(2, 3, BufferedImage.TYPE_INT_RGB, "jpg"))), is(expected(2, 3, "jpeg", 24)));
    }

    @Test
    public void shouldDescribeAnUnreadableImageByItsMediaTypeWithoutThrowing() throws IOException {
        byte[] png = encode(7, 5, BufferedImage.TYPE_INT_RGB, "png");
        byte[] jpeg = encode(7, 5, BufferedImage.TYPE_INT_RGB, "jpg");
        assertThat(describe("data:image/png;base64,..."), is(expected(0, 0, "png", 0)));
        assertThat(describe("data:image/jpg;base64,not base64 at all"), is(expected(0, 0, "jpeg", 0)));
        assertThat(describe("data:image/webp;base64,UklGRg=="), is(expected(0, 0, "webp", 0)));
        assertThat(describe("data:image/png;base64,"), is(expected(0, 0, "png", 0)));
        assertThat("truncated inside IHDR", describe(dataUri("image/png", Arrays.copyOf(png, 22))), is(expected(0, 0, "png", 0)));
        assertThat("truncated inside a segment", describe(dataUri("image/jpeg", Arrays.copyOf(jpeg, 30))), is(expected(0, 0, "jpeg", 0)));
        assertThat(describe(dataUri("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0})), is(expected(0, 0, "jpeg", 0)));
        assertThat(describe(dataUri("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, (byte) 0xFF, (byte) 0xFF})), is(expected(0, 0, "jpeg", 0)));
        byte[] fill = new byte[10_000];
        Arrays.fill(fill, (byte) 0xFF);
        fill[1] = (byte) 0xD8;
        assertThat(describe(dataUri("image/jpeg", fill)), is(expected(0, 0, "jpeg", 0)));
        assertThat(describe("https://example.com/cat.png"), is(expected(0, 0, "", 0)));
        assertThat(describe(""), is(expected(0, 0, "", 0)));
        assertThat(describe("data:"), is(expected(0, 0, "", 0)));
        assertThat(describe("data:image/" + "x".repeat(10_000) + ";base64,AAAA"), is(expected(0, 0, "", 0)));
        assertThat(describe((JsonNode) null), is(expected(0, 0, "", 0)));
        assertThat(describe(IntNode.valueOf(7)), is(expected(0, 0, "", 0)));
    }

    @Test
    public void shouldStopWalkingJpegSegmentsAtTheBound() {
        int segments = CohereImageDescription.MAX_JPEG_SEGMENTS;
        assertThat("a frame header after the bound is not reached",
            describe(dataUri("image/jpeg", jpegWithCommentsBeforeFrame(segments))), is(expected(0, 0, "jpeg", 0)));
        assertThat("a frame header inside the bound is read",
            describe(dataUri("image/jpeg", jpegWithCommentsBeforeFrame(segments - 2))), is(expected(4, 3, "jpeg", 24)));
    }

    @Test
    public void shouldReadEachByteOfABase64PayloadAsTheJdkDecoderDoes() {
        Random random = new Random(42);
        for (int length = 0; length < 40; length++) {
            byte[] bytes = new byte[length];
            random.nextBytes(bytes);
            for (String encoded : new String[]{Base64.getEncoder().encodeToString(bytes), Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}) {
                CohereImageDescription.Base64Bytes decoded = CohereImageDescription.Base64Bytes.payloadOf("data:image/png;base64," + encoded);
                for (int i = 0; i < length; i++) {
                    assertThat("byte " + i + " of " + length, decoded.at(i), is(bytes[i] & 0xFF));
                }
                assertThat(decoded.at(length), is(-1));
                assertThat(decoded.at(-1), is(-1));
            }
        }
    }

    private static byte[] segment(int marker, int length) {
        byte[] segment = new byte[length + 2];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) marker;
        segment[2] = (byte) (length >> 8);
        segment[3] = (byte) length;
        return segment;
    }

    private static byte[] jpegWithCommentsBeforeFrame(int comments) {
        byte[] frame = {(byte) 0xFF, (byte) 0xC0, 0, 17, 8, 0, 3, 0, 4, 3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1};
        byte[] bytes = new byte[2 + comments * 4 + frame.length];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        for (int i = 0; i < comments; i++) {
            System.arraycopy(segment(0xFE, 2), 0, bytes, 2 + i * 4, 4);
        }
        System.arraycopy(frame, 0, bytes, 2 + comments * 4, frame.length);
        return bytes;
    }
}
