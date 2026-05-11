package org.mockserver.grpc;

import org.junit.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GrpcFrameCodecTest {

    @Test
    public void shouldEncodeUncompressedMessage() {
        byte[] message = "hello".getBytes();
        byte[] encoded = GrpcFrameCodec.encode(message);

        assertThat(encoded.length, is(5 + message.length));
        assertThat(encoded[0], is((byte) 0));
        assertThat(encoded[1], is((byte) 0));
        assertThat(encoded[2], is((byte) 0));
        assertThat(encoded[3], is((byte) 0));
        assertThat(encoded[4], is((byte) 5));
    }

    @Test
    public void shouldDecodeUncompressedMessage() {
        byte[] message = "hello".getBytes();
        byte[] encoded = GrpcFrameCodec.encode(message);

        List<byte[]> decoded = GrpcFrameCodec.decode(encoded);
        assertThat(decoded.size(), is(1));
        assertThat(decoded.get(0), is(message));
    }

    @Test
    public void shouldDecodeSingleMessage() {
        byte[] message = "test".getBytes();
        byte[] encoded = GrpcFrameCodec.encode(message);

        byte[] decoded = GrpcFrameCodec.decodeSingle(encoded);
        assertThat(decoded, is(message));
    }

    @Test
    public void shouldEncodeAndDecodeCompressedMessage() {
        byte[] message = "hello world, this is a test message for gzip compression".getBytes();
        byte[] encoded = GrpcFrameCodec.encode(message, true);

        assertThat(encoded[0], is((byte) 1));

        List<byte[]> decoded = GrpcFrameCodec.decode(encoded);
        assertThat(decoded.size(), is(1));
        assertThat(decoded.get(0), is(message));
    }

    @Test
    public void shouldDecodeMultipleFrames() {
        byte[] msg1 = "first".getBytes();
        byte[] msg2 = "second".getBytes();
        byte[] encoded1 = GrpcFrameCodec.encode(msg1);
        byte[] encoded2 = GrpcFrameCodec.encode(msg2);

        byte[] combined = new byte[encoded1.length + encoded2.length];
        System.arraycopy(encoded1, 0, combined, 0, encoded1.length);
        System.arraycopy(encoded2, 0, combined, encoded1.length, encoded2.length);

        List<byte[]> decoded = GrpcFrameCodec.decode(combined);
        assertThat(decoded.size(), is(2));
        assertThat(decoded.get(0), is(msg1));
        assertThat(decoded.get(1), is(msg2));
    }

    @Test
    public void shouldHandleEmptyMessage() {
        byte[] message = new byte[0];
        byte[] encoded = GrpcFrameCodec.encode(message);

        assertThat(encoded.length, is(5));
        assertThat(encoded[4], is((byte) 0));

        List<byte[]> decoded = GrpcFrameCodec.decode(encoded);
        assertThat(decoded.size(), is(1));
        assertThat(decoded.get(0).length, is(0));
    }

    @Test
    public void shouldReturnEmptyForEmptyInput() {
        byte[] decoded = GrpcFrameCodec.decodeSingle(new byte[0]);
        assertThat(decoded.length, is(0));
    }

    @Test
    public void shouldHandleIncompleteFrameGracefully() {
        byte[] incomplete = new byte[]{0, 0, 0, 0, 10};
        List<byte[]> decoded = GrpcFrameCodec.decode(incomplete);
        assertThat(decoded, is(empty()));
    }

    @Test(expected = GrpcException.class)
    public void shouldRejectOversizedFrame() {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(5);
        buffer.put((byte) 0);
        buffer.putInt(5 * 1024 * 1024);
        GrpcFrameCodec.decode(buffer.array());
    }

    @Test(expected = GrpcException.class)
    public void shouldRejectNegativeFrameLength() {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(5);
        buffer.put((byte) 0);
        buffer.putInt(-1);
        GrpcFrameCodec.decode(buffer.array());
    }
}
