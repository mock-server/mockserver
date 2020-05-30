package org.mockserver.memory;

import org.mockserver.model.ObjectWithJsonToString;

public class Detail extends ObjectWithJsonToString {
    private long init;
    private long used;
    private long committed;
    private long max;

    public Detail plus(Detail detail) {
        return new Detail()
            .setInit(init + detail.init)
            .setUsed(used + detail.used)
            .setCommitted(committed + detail.committed)
            .setMax(max + detail.max);
    }

    public long getInit() {
        return init;
    }

    public Detail setInit(long init) {
        this.init = init;
        return this;
    }

    public long getUsed() {
        return used;
    }

    public Detail setUsed(long used) {
        this.used = used;
        return this;
    }

    public long getCommitted() {
        return committed;
    }

    public Detail setCommitted(long committed) {
        this.committed = committed;
        return this;
    }

    public long getMax() {
        return max;
    }

    public Detail setMax(long max) {
        this.max = max;
        return this;
    }
}