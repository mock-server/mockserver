package org.mockserver.cache;

public class Entry<T> {

    private final long ttlInMillis;
    private final T value;
    private long expiryInMillis;

    public Entry(long ttlInMillis, long expiryInMillis, T value) {
        this.ttlInMillis = ttlInMillis;
        this.expiryInMillis = expiryInMillis;
        this.value = value;
    }

    public long getTtlInMillis() {
        return ttlInMillis;
    }

    public long getExpiryInMillis() {
        return expiryInMillis;
    }

    public Entry<T> updateExpiryInMillis(long expiryInMillis) {
        this.expiryInMillis = expiryInMillis;
        return this;
    }

    public T getValue() {
        return value;
    }
}
