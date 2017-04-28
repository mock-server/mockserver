package org.mockserver.maven;

import com.google.common.util.concurrent.AbstractFuture;

public class SettableFuture<V> extends AbstractFuture<V> {

    private SettableFuture() {
    }

    public static <V> SettableFuture<V> create() {
        return new SettableFuture<V>();
    }

    @Override
    public boolean set(V value) {
        return super.set(value);
    }

    @Override
    public boolean setException(Throwable throwable) {
        return super.setException(throwable);
    }
}