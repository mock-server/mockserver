package org.mockserver.templates.engine.velocity;

import java.util.concurrent.TimeUnit;

/**
 * The velocityTemplateExecutionTimeout deadline of the Velocity render running on this thread. A render cannot
 * be interrupted from outside, so every construct that can repeat ({@code #foreach} iterations, macro calls,
 * {@code #parse}, {@code #evaluate} and renders of a {@code #define} block) calls {@link #check()}, and the
 * render fails once the deadline has passed. The clock is read once every {@link #CHECKS_PER_CLOCK_READ}
 * checks; a single long Java call made by the template (for example a backtracking regular expression) cannot
 * be stopped.
 */
public final class RenderDeadline {

    static final int CHECKS_PER_CLOCK_READ = 64;
    private static final ThreadLocal<RenderDeadline> CURRENT = new ThreadLocal<>();

    private final long deadlineNanos;
    private final long timeoutMillis;
    private int checksUntilClockRead = CHECKS_PER_CLOCK_READ;

    private RenderDeadline(long timeoutMillis) {
        this.deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Gives the renders on this thread, until the returned scope is closed, a deadline {@code timeoutMillis}
     * from now; 0 or negative means no deadline. Closing restores the enclosing deadline.
     */
    public static Scope start(long timeoutMillis) {
        Scope scope = new Scope(CURRENT.get());
        CURRENT.set(timeoutMillis > 0 ? new RenderDeadline(timeoutMillis) : null);
        return scope;
    }

    /**
     * @return the deadline of the render on this thread, or null when it has none
     */
    public static RenderDeadline current() {
        return CURRENT.get();
    }

    /**
     * Fails the render on this thread if its deadline has passed.
     */
    public static void check() {
        RenderDeadline deadline = CURRENT.get();
        if (deadline != null) {
            deadline.tick();
        }
    }

    /**
     * Fails the render if this deadline has passed; only the rendering thread may call it.
     */
    public void tick() {
        if (--checksUntilClockRead > 0) {
            return;
        }
        checksUntilClockRead = CHECKS_PER_CLOCK_READ;
        if (System.nanoTime() - deadlineNanos > 0) {
            throw new VelocityTemplateTimeoutException(timeoutMillis);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final RenderDeadline previous;

        private Scope(RenderDeadline previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }
}
