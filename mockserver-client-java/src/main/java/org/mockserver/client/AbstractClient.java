package org.mockserver.client;

import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClient {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final String host;
    protected final int port;
    protected final String contextPath;
    protected NettyHttpClient nettyHttpClient = new NettyHttpClient();
    protected HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    protected ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    protected VerificationSerializer verificationSerializer = new VerificationSerializer();
    protected VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();

    /**
     * Start the client communicating to the proxy at the specified host and port
     * and contextPath for example:
     * <p>
     * ProxyClient mockServerClient = new ProxyClient("localhost", 1080, "/proxy");
     *
     * @param host        the host for the proxy to communicate with
     * @param port        the port for the proxy to communicate with
     * @param contextPath the context path that the proxy war is deployed to
     */
    protected AbstractClient(String host, int port, String contextPath) {
        if (StringUtils.isEmpty(host)) {
            throw new IllegalArgumentException("Host can not be null or empty");
        }
        if (contextPath == null) {
            throw new IllegalArgumentException("ContextPath can not be null");
        }
        this.host = host;
        this.port = port;
        this.contextPath = contextPath;
    }

    protected String calculatePath(String path) {
        String cleanedPath = path;
        if (!Strings.isNullOrEmpty(contextPath)) {
            cleanedPath =
                    (!contextPath.startsWith("/") ? "/" : "") +
                            contextPath +
                            (!contextPath.endsWith("/") ? "/" : "") +
                            (cleanedPath.startsWith("/") ? cleanedPath.substring(1) : cleanedPath);
        }
        return (!cleanedPath.startsWith("/") ? "/" : "") + cleanedPath;
    }

    protected HttpResponse sendRequest(HttpRequest httpRequest) {
        HttpResponse httpResponse = nettyHttpClient.sendRequest(
                httpRequest.withHeader(HOST.toString(), host + ":" + port)
        );
        if (httpResponse != null &&
                httpResponse.getStatusCode() != null &&
                httpResponse.getStatusCode() == BAD_REQUEST.code()) {
            throw new IllegalArgumentException(httpResponse.getBodyAsString());
        }
        return httpResponse;
    }

    protected String formatErrorMessage(String message, Object... objects) {
        Object[] indentedObjects = new String[objects.length];
        for (int i = 0; i < objects.length; i++) {
            indentedObjects[i] = NEW_LINE + NEW_LINE + String.valueOf(objects[i]).replaceAll("(?m)^", "\t") + NEW_LINE;
        }
        return String.format(NEW_LINE + message + NEW_LINE, indentedObjects);
    }

    public InetSocketAddress remoteAddress() {
        return new InetSocketAddress(host, port);
    }

    public String contextPath() {
        return contextPath;
    }
}
