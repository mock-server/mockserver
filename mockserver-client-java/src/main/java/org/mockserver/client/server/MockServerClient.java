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

    /**
     * Specify an unlimited expectation that will respond regardless of the number of matching http
     * for example:
     * <p>
     * mockServerClient
     * .when(request().withPath("/some_path").withBody("some_request_body"))
     * .respond(response().withBody("some_response_body").withHeader("responseName", "responseValue"))
     *
     * @param httpRequest the http request that must be matched for this expectation to respond
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(HttpRequest httpRequest) {
        return when(httpRequest, Times.unlimited());
    }

    /**
     * Specify an limited expectation that will respond a specified number of times when the http is matched
     * for example:
     * <p>
     * mockServerClient
     * .when(request().withPath("/some_path").withBody("some_request_body"), Times.exactly(5))
     * .respond(response().withBody("some_response_body").withHeader("responseName", "responseValue"))
     *
     * @param httpRequest the http request that must be matched for this expectation to respond
     * @param times       the number of times to respond when this http is matched
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(HttpRequest httpRequest, Times times) {
        return new ForwardChainExpectation(this, new Expectation(httpRequest, times, TimeToLive.unlimited()));
    }

    /**
     * Specify an limited expectation that will respond a specified number of times when the http is matched
     * for example:
     * <p>
     * mockServerClient
     * .when(request().withPath("/some_path").withBody("some_request_body"), Times.exactly(5), TimeToLive.exactly(TimeUnit.SECONDS, 120))
     * .respond(response().withBody("some_response_body").withHeader("responseName", "responseValue"))
     *
     * @param httpRequest the http request that must be matched for this expectation to respond
     * @param times       the number of times to respond when this http is matched
     * @param timeToLive  the length of time from when the server receives the expectation that the expectation should be active
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(HttpRequest httpRequest, Times times, TimeToLive timeToLive) {
        return new ForwardChainExpectation(this, new Expectation(httpRequest, times, timeToLive));
    }

    void sendExpectation(Expectation expectation) {
        HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("expectation")).withBody(expectation != null ? expectationSerializer.serialize(expectation) : "", Charsets.UTF_8));
        if (httpResponse != null && httpResponse.getStatusCode() != 201) {
            throw new ClientException(formatErrorMessage(NEW_LINE + "error:%s" + NEW_LINE + "while submitted expectation:%s", httpResponse.getBody(), expectation));
        }
    }

    /**
     * Retrieve the already setup expectations match the httpRequest parameter, use null for the parameter to retrieve all expectations
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been setup
     */
    public Expectation[] retrieveActiveExpectations(HttpRequest httpRequest) {
        String activeExpectations = retrieveActiveExpectations(httpRequest, Format.JSON);
        if (!Strings.isNullOrEmpty(activeExpectations)) {
            return expectationSerializer.deserializeArray(activeExpectations);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve the already setup expectations match the httpRequest parameter, use null for the parameter to retrieve all expectations
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been setup
     */
    public String retrieveActiveExpectations(HttpRequest httpRequest, Format format) {
        HttpResponse httpResponse = sendRequest(
                request()
                        .withMethod("PUT")
                        .withPath(calculatePath("retrieve"))
                        .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                        .withQueryStringParameter("format", format.name())
                        .withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", Charsets.UTF_8)
        );
        return httpResponse.getBodyAsString();
    }
}
