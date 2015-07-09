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

import static org.mockserver.model.OutboundHttpRequest.outboundRequest;

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
     *
     *   ProxyClient mockServerClient = new ProxyClient("localhost", 1080, "/proxy");
     *
     * @param host the host for the proxy to communicate with
     * @param port the port for the proxy to communicate with
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
        this.contextPath = cleanContextPath(contextPath);
    }

    private String cleanContextPath(String contextPath) {
        if (!Strings.isNullOrEmpty(contextPath)) {
            if (!contextPath.endsWith("/")) {
                contextPath += "/";
            }
            if (!contextPath.startsWith("/")) {
                contextPath = "/" + contextPath;
            }
        }
        return contextPath;
    }

    protected String calculatePath(String path) {
        return "/" + path;
    }

    protected HttpResponse sendRequest(HttpRequest httpRequest) {
        return nettyHttpClient.sendRequest(outboundRequest(host, port, contextPath, httpRequest));
    }

    protected String formatErrorMessage(String message, Object... objects) {
        Object[] indentedObjects = new String[objects.length];
        for (int i = 0; i < objects.length; i++) {
            indentedObjects[i] = System.getProperty("line.separator") + System.getProperty("line.separator") + String.valueOf(objects[i]).replaceAll("(?m)^", "\t") + System.getProperty("line.separator");
        }
        return String.format(System.getProperty("line.separator") + message + System.getProperty("line.separator"), indentedObjects);
    }
}
