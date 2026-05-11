package org.mockserver.grpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayInputStream;

public class GrpcFrameCodec {

    private static final int HEADER_LENGTH = 5;
    private static final int MAX_MESSAGE_SIZE = 4 * 1024 * 1024;
    private static final byte UNCOMPRESSED = 0;
    private static final byte COMPRESSED = 1;

    public static byte[] encode(byte[] message, boolean compress) {
        byte[] payload = message;
        byte flag = UNCOMPRESSED;
        if (compress) {
            payload = gzipCompress(message);
            flag = COMPRESSED;
        }
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + payload.length);
        buffer.put(flag);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    public static byte[] encode(byte[] message) {
        return encode(message, false);
    }

    public static List<byte[]> decode(byte[] data) {
        List<byte[]> messages = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.remaining() >= HEADER_LENGTH) {
            byte compressedFlag = buffer.get();
            if ((compressedFlag & ~1) != 0) {
                throw new GrpcException("gRPC frame has reserved flag bits set: " + compressedFlag);
            }
            int length = buffer.getInt();
            if (length < 0 || length > MAX_MESSAGE_SIZE) {
                throw new GrpcException("gRPC message size " + length + " exceeds maximum allowed " + MAX_MESSAGE_SIZE);
            }
            if (buffer.remaining() < length) {
                break;
            }
            byte[] payload = new byte[length];
            buffer.get(payload);
            if (compressedFlag == COMPRESSED) {
                payload = gzipDecompress(payload);
            }
            messages.add(payload);
        }
        return messages;
    }

    public static byte[] decodeSingle(byte[] data) {
        List<byte[]> messages = decode(data);
        if (messages.isEmpty()) {
            return new byte[0];
        }
        return messages.get(0);
    }

    private static byte[] gzipCompress(byte[] data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
                gos.write(data);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to gzip compress gRPC message", e);
        }
    }

    private static byte[] gzipDecompress(byte[] data) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            try (GZIPInputStream gis = new GZIPInputStream(bis)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = gis.read(buf)) != -1) {
                    bos.write(buf, 0, len);
                }
                return bos.toByteArray();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to gzip decompress gRPC message", e);
        }
    }
}
