package org.mockserver.verify;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class VerificationTimes extends ObjectWithReflectiveEqualsHashCodeToString {

    private final Optional<Integer> lowerBound;
    private final Optional<Integer> upperBound;

    private VerificationTimes(Optional<Integer> lowerBound, Optional<Integer> upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public Optional<Integer> getLowerBound() {
        return lowerBound;
    }

    public Optional<Integer> getUpperBound() {
        return upperBound;
    }

    @JsonIgnore
    public Optional<Integer> getExactCount() {
        if (lowerBound.equals(upperBound)) {
            return lowerBound;
        } else {
            return Optional.absent();
        }
    }

    public static VerificationTimes between(int lowerBound, int upperBound) {
        Preconditions.checkArgument(lowerBound >= 0, "Negative lower bound");
        Preconditions.checkArgument(upperBound >= 0, "Negative upper bound");
        Preconditions.checkArgument(upperBound >= lowerBound, "Upper bound is smaller than lower bound");
        return new VerificationTimes(Optional.of(lowerBound), Optional.of(upperBound));
    }

    public static VerificationTimes never() {
        return exactly(0);
    }

    public static VerificationTimes once() {
        return exactly(1);
    }

    public static VerificationTimes exactly(int count) {
        return between(count, count);
    }

    public static VerificationTimes atLeast(int count) {
        Preconditions.checkArgument(count >= 0, "Negative count");
        Preconditions.checkArgument(count != 0, "Count is zero");
        return new VerificationTimes(Optional.of(count), Optional.<Integer>absent());
    }

    public static VerificationTimes atMost(int count) {
        Preconditions.checkArgument(count >= 0, "Negative count");
        return new VerificationTimes(Optional.<Integer>absent(), Optional.of(count));
    }

    public boolean matchesActualCount(Integer count) {
        return (!lowerBound.isPresent() || lowerBound.get() <= count) &&
                (!upperBound.isPresent() || upperBound.get() >= count);
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (getExactCount().isPresent()) {
          int count = getExactCount().get();
          if (count == 1) {
            sb.append("exactly once");
          } else if (count == 0) {
            sb.append("never");
          } else {
            sb.append("exactly ");
            sb.append(count);
            sb.append(" times");
          }
        } else if (lowerBound.isPresent() && upperBound.isPresent()) {
            sb.append("between ");
            sb.append(lowerBound.get());
            sb.append(" and ");
            sb.append(upperBound.get());
            sb.append(" times");
        } else if (lowerBound.isPresent()) {
            sb.append("at least ");
            if (lowerBound.get() == 1) {
                sb.append("once");
            } else {
                sb.append(lowerBound.get());
                sb.append(" times");
            }
        } else if (upperBound.isPresent()) {
            if (upperBound.get() == 0) {
                sb.append("never");
            } else {
                sb.append("at most ");
                if (upperBound.get() == 1) {
                    sb.append("once");
                } else {
                    sb.append(upperBound.get());
                    sb.append(" times");
                }
            }
        }
        return sb.toString();
    }
}
