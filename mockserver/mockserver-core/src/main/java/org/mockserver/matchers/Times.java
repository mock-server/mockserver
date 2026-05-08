package org.mockserver.matchers;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jamesdbloom
 */
public class Times extends ObjectWithReflectiveEqualsHashCodeToString {

    private static final Times TIMES_UNLIMITED = new Times(-1, true) {
        public final int getRemainingTimes() {
            return -1;
        }

        public final boolean isUnlimited() {
            return true;
        }

        public final boolean greaterThenZero() {
            return true;
        }

        public final boolean decrement() {
            return false;
        }

        public final boolean decrementAndCheckGreaterThanZero() {
            return true;
        }
    };

    private int hashCode;
    private final AtomicInteger remainingTimes;
    private final boolean unlimited;

    private Times(int remainingTimes, boolean unlimited) {
        this.remainingTimes = new AtomicInteger(remainingTimes);
        this.unlimited = unlimited;
    }

    public static Times unlimited() {
        return TIMES_UNLIMITED;
    }

    public static Times once() {
        return new Times(1, false);
    }

    public static Times exactly(int count) {
        return new Times(count, false);
    }

    public int getRemainingTimes() {
        return remainingTimes.get();
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    public boolean greaterThenZero() {
        return unlimited || remainingTimes.get() > 0;
    }

    public boolean decrement() {
        if (!unlimited) {
            remainingTimes.decrementAndGet();
            return true;
        }
        return false;
    }

    public boolean decrementAndCheckGreaterThanZero() {
        if (unlimited) {
            return true;
        }
        return remainingTimes.decrementAndGet() >= 0;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Times clone() {
        if (unlimited) {
            return Times.unlimited();
        } else {
            return Times.exactly(remainingTimes.get());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        Times times = (Times) o;
        return remainingTimes.get() == times.remainingTimes.get() &&
            unlimited == times.unlimited;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(remainingTimes.get(), unlimited);
        }
        return hashCode;
    }
}
