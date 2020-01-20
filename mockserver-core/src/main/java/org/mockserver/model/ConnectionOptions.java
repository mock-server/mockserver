package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class ConnectionOptions extends ObjectWithJsonToString {

    private Boolean suppressContentLengthHeader = null;
    private Integer contentLengthHeaderOverride = null;
    private Boolean suppressConnectionHeader = null;
    private Boolean keepAliveOverride = null;
    private Boolean closeSocket = null;
    private Delay closeSocketDelay = null;

    public static ConnectionOptions connectionOptions() {
        return new ConnectionOptions();
    }

    public static boolean isFalseOrNull(Boolean aBoolean) {
        return aBoolean == null || !aBoolean;
    }

    /**
     * Prevent a "Content-Length" header from being added to the response
     *
     * @param suppressContentLengthHeader if true no "Content-Length" header will be added to the response
     */
    public ConnectionOptions withSuppressContentLengthHeader(Boolean suppressContentLengthHeader) {
        this.suppressContentLengthHeader = suppressContentLengthHeader;
        return this;
    }

    public Boolean getSuppressContentLengthHeader() {
        return suppressContentLengthHeader;
    }

    /**
     * Override the "Content-Length" header with the specified amount, if not set the "Content-Length"
     * header will have a value determined by the length of the body
     *
     * @param contentLengthHeaderOverride the value to use for the "Content-Length" header
     */
    public ConnectionOptions withContentLengthHeaderOverride(Integer contentLengthHeaderOverride) {
        this.contentLengthHeaderOverride = contentLengthHeaderOverride;
        return this;
    }

    public Integer getContentLengthHeaderOverride() {
        return contentLengthHeaderOverride;
    }

    /**
     * Prevent a "Connection" header from being added to the response
     *
     * @param suppressConnectionHeader if true no "Connection" header will be added to the response
     */
    public ConnectionOptions withSuppressConnectionHeader(Boolean suppressConnectionHeader) {
        this.suppressConnectionHeader = suppressConnectionHeader;
        return this;
    }

    public Boolean getSuppressConnectionHeader() {
        return suppressConnectionHeader;
    }

    /**
     * Override the "Connection" header:
     * if true the "Connection" header is specified with a value of "keep-alive"
     * if false the "Connection" header is specified with a value of "close"
     * if not set the "Connection" header will have a a value of "close" unless the request received is HTTP 1.1 and contains a "Connection" header with a value of "keep-alive"
     *
     * @param keepAliveOverride if true "keep-alive" is used if false "close" is used for the "Connection" header
     */
    public ConnectionOptions withKeepAliveOverride(Boolean keepAliveOverride) {
        this.keepAliveOverride = keepAliveOverride;
        return this;
    }

    public Boolean getKeepAliveOverride() {
        return keepAliveOverride;
    }

    /**
     * Override whether the socket is closed after a response is sent:
     * if true the socket will always be closed,
     * if false the socket will never be closed,
     * if not set the socket will be closed unless the request received is HTTP 1.1 and contains a "Connection" header with a value of "keep-alive"
     *
     * @param closeSocket set whether the socket is closed after a response is sent
     */
    public ConnectionOptions withCloseSocket(Boolean closeSocket) {
        this.closeSocket = closeSocket;
        return this;
    }

    public Boolean getCloseSocket() {
        return closeSocket;
    }

    /**
     * Override how long to delay before closing socket, this value is ignored if socket isn't going to be closed
     *
     * @param closeSocketDelay set delay before closing socket
     */
    public ConnectionOptions withCloseSocketDelay(Delay closeSocketDelay) {
        this.closeSocketDelay = closeSocketDelay;
        return this;
    }

    public Delay getCloseSocketDelay() {
        return closeSocketDelay;
    }
}
