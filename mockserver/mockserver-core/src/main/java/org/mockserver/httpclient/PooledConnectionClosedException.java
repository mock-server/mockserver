package org.mockserver.httpclient;

/**
 * A request that failed because the upstream closed the pooled keep-alive connection it was sent on before any of the
 * response arrived, and that was not sent again on a new connection because its method is not idempotent.
 * <p>
 * Its message and string form are its cause's, so a caller that logs or catches the failure sees what it saw before;
 * only {@link #in} tells it apart, so the forward's {@code 502} can name the cause.
 */
public class PooledConnectionClosedException extends SocketConnectionException {

    private static final long serialVersionUID = 1L;

    private final String method;

    PooledConnectionClosedException(String method, Throwable cause) {
        super(cause.getMessage(), cause);
        this.method = method;
    }

    /**
     * @return the method of the request that was not sent again
     */
    public String getMethod() {
        return method;
    }

    @Override
    public String toString() {
        return getCause().toString();
    }

    /**
     * @return the pooled connection close {@code failure} is, or was caused by, or null
     */
    public static PooledConnectionClosedException in(Throwable failure) {
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (failure instanceof PooledConnectionClosedException) {
                return (PooledConnectionClosedException) failure;
            }
        }
        return null;
    }
}
