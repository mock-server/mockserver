package org.mockserver.client.proxy;

import org.mockserver.client.AbstractClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;

/**
 * @author jamesdbloom
 */
public class ProxyClient extends AbstractClient<ProxyClient> {

    /**
     * Start the client communicating to a the proxy at the specified host and port
     * for example:
     *
     *   ProxyClient mockServerClient = new ProxyClient("localhost", 1080);
     *
     * @param host the host for the MockServer to communicate with
     * @param port the port for the MockServer to communicate with
     */
    public ProxyClient(String host, int port) {
        this(host, port, "");
    }

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
    public ProxyClient(String host, int port, String contextPath) {
        super(host, port, contextPath, ProxyClient.class);
    }

}
