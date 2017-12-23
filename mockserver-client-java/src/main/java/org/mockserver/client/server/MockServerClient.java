package org.mockserver.client.server;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import org.mockserver.client.AbstractClient;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Format;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RetrieveType;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class MockServerClient extends AbstractClient<MockServerClient> {

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * for example:
     * <p>
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080);
     *
     * @param host the host for the MockServer to communicate with
     * @param port the port for the MockServer to communicate with
     */
    public MockServerClient(String host, int port) {
        this(host, port, "");
    }

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * and contextPath for example:
     * <p>
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080, "/mockserver");
     *
     * @param host        the host for the MockServer to communicate with
     * @param port        the port for the MockServer to communicate with
     * @param contextPath the context path that the MockServer war is deployed to
     */
    public MockServerClient(String host, int port, String contextPath) {
        super(host, port, contextPath, MockServerClient.class);
    }

}
