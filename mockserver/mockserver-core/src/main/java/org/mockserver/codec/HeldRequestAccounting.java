package org.mockserver.codec;

/**
 * How {@link Http1PipeliningGate} tells the server's in-flight request count about the requests it holds, so a graceful
 * stop waits for a request held behind another, and drops, rather than processes, one still held when that wait ends.
 * Called on the connection's event loop only.
 */
public interface HeldRequestAccounting {

    /**
     * For a gate whose server keeps no in-flight count.
     */
    HeldRequestAccounting NONE = new HeldRequestAccounting() {
        @Override
        public void held() {
        }

        @Override
        public void released() {
        }

        @Override
        public boolean discardHeldOnClose() {
            return false;
        }
    };

    /**
     * A request has been held: count it as in flight until the matching {@link #released()}.
     */
    void held();

    /**
     * A held request has been handed on whole (its handling has started, so it is counted there), or dropped.
     */
    void released();

    /**
     * Whether the server's stop has stopped waiting for in-flight requests, so held requests must be dropped, not
     * processed, and none released.
     */
    boolean discardHeldOnClose();
}
