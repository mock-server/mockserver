package org.mockserver.proxy.socks;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * @author jamesdbloom
 */
public class ByteBufferPool {
    private final ConcurrentMap<Integer, Queue<ByteBuffer>> directBuffers = new ConcurrentHashMap<Integer, Queue<ByteBuffer>>();
    private final ConcurrentMap<Integer, Queue<ByteBuffer>> heapBuffers = new ConcurrentHashMap<Integer, Queue<ByteBuffer>>();
    private final int factor;

    public ByteBufferPool() {
        this(1024);
    }

    public ByteBufferPool(int factor) {
        this.factor = factor;
    }

    public ByteBuffer acquire(int size, boolean direct) {
        int bucket = bucketFor(size);
        ConcurrentMap<Integer, Queue<ByteBuffer>> buffers = direct ? directBuffers : heapBuffers;

        ByteBuffer byteBuffer = null;
        Queue<ByteBuffer> byteBuffers = buffers.get(bucket);
        if (byteBuffers != null)
            byteBuffer = byteBuffers.poll();

        if (byteBuffer == null) {
            int capacity = bucket * factor;
            byteBuffer = direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
        }

        byteBuffer.position(0);
        byteBuffer.limit(0);
        return byteBuffer;
    }

    public void release(ByteBuffer byteBuffer) {
        if (byteBuffer == null)
            return; // nothing to do

        // validate that this byteBuffer is from this pool
        assert ((byteBuffer.capacity() % factor) == 0);

        int bucket = bucketFor(byteBuffer.capacity());
        ConcurrentMap<Integer, Queue<ByteBuffer>> buffers = byteBuffer.isDirect() ? directBuffers : heapBuffers;

        // Avoid to create a new queue every time, just to be discarded immediately
        Queue<ByteBuffer> byteBuffers = buffers.get(bucket);
        if (byteBuffers == null) {
            byteBuffers = new ConcurrentLinkedQueue<ByteBuffer>();
            Queue<ByteBuffer> existing = buffers.putIfAbsent(bucket, byteBuffers);
            if (existing != null)
                byteBuffers = existing;
        }

        byteBuffer.position(0);
        byteBuffer.limit(0);
        byteBuffers.offer(byteBuffer);
    }

    public void clear() {
        directBuffers.clear();
        heapBuffers.clear();
    }

    private int bucketFor(int size) {
        int bucket = size / factor;
        if (size % factor > 0)
            ++bucket;
        return bucket;
    }

}

