package org.mockserver.client.proxy;

/**
 * @author jamesdbloom
 */
public class Times {

    private final int count;
    private final boolean exact;

    private Times(int count, boolean exact) {
        this.count = count;
        this.exact = exact;
    }

    public static Times once() {
        return new Times(1, true);
    }

    public static Times exactly(int count) {
        return new Times(count, true);
    }

    public static Times atLeast(int count) {
        return new Times(count, false);
    }

    public int getCount() {
        return count;
    }

    public boolean isExact() {
        return exact;
    }
}
