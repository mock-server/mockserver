package org.mockserver.mock.action.http;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpForwardValidateAction;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.model.HttpForwardValidateAction.forwardValidate;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class HttpForwardValidateActionHandlerTest {

    private NettyHttpClient mockHttpClient;
    private HttpForwardValidateActionHandler handler;

    @Before
    public void setupMocks() {
        mockHttpClient = mock(NettyHttpClient.class);
        MockServerLogger mockLogFormatter = mock(MockServerLogger.class);
        handler = new HttpForwardValidateActionHandler(mockLogFormatter, new Configuration(), mockHttpClient);
        openMocks(this);
    }

    @Test
    public void shouldForwardRequestWhenNoSpec() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("upstream"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpForwardActionResult result = handler.handle(null, request("/somePath"));

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(200));
        assertThat(actualResponse.getBodyAsString(), is("upstream"));
    }

    @Test
    public void shouldForwardRequestWithHostAndPort() throws Exception {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        responseFuture.complete(response().withStatusCode(200).withBody("forwarded"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any())).thenReturn(responseFuture);

        HttpForwardActionResult result = handler.handle(
            forwardValidate()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withHost("localhost")
                .withPort(8080)
                .withValidateRequest(false)
                .withValidateResponse(false),
            request("/pets").withMethod("GET")
        );

        HttpResponse actualResponse = result.getHttpResponse().get();
        assertThat(actualResponse.getStatusCode(), is(200));
        assertThat(actualResponse.getBodyAsString(), is("forwarded"));
    }

    @Test
    public void shouldReturnActionType() {
        assertThat(forwardValidate().getType().name(), is("FORWARD_VALIDATE"));
    }

    @Test
    public void shouldSetAndGetAllProperties() {
        HttpForwardValidateAction action = forwardValidate()
            .withSpecUrlOrPayload("someSpec")
            .withHost("someHost")
            .withPort(9090)
            .withScheme(HttpForward.Scheme.HTTPS)
            .withValidateRequest(false)
            .withValidateResponse(true)
            .withValidationMode(HttpForwardValidateAction.ValidationMode.LOG_ONLY);

        assertThat(action.getSpecUrlOrPayload(), is("someSpec"));
        assertThat(action.getHost(), is("someHost"));
        assertThat(action.getPort(), is(9090));
        assertThat(action.getScheme(), is(HttpForward.Scheme.HTTPS));
        assertThat(action.getValidateRequest(), is(false));
        assertThat(action.getValidateResponse(), is(true));
        assertThat(action.getValidationMode(), is(HttpForwardValidateAction.ValidationMode.LOG_ONLY));
    }

    @Test
    public void shouldHaveDefaultValues() {
        HttpForwardValidateAction action = forwardValidate();

        assertThat(action.getPort(), is(80));
        assertThat(action.getScheme(), is(HttpForward.Scheme.HTTP));
        assertThat(action.getValidateRequest(), is(true));
        assertThat(action.getValidateResponse(), is(true));
        assertThat(action.getValidationMode(), is(HttpForwardValidateAction.ValidationMode.STRICT));
    }

    @Test
    public void shouldImplementEqualsAndHashCode() {
        HttpForwardValidateAction action1 = forwardValidate()
            .withSpecUrlOrPayload("spec1")
            .withHost("host1")
            .withPort(80);
        HttpForwardValidateAction action2 = forwardValidate()
            .withSpecUrlOrPayload("spec1")
            .withHost("host1")
            .withPort(80);
        HttpForwardValidateAction action3 = forwardValidate()
            .withSpecUrlOrPayload("spec2")
            .withHost("host2")
            .withPort(443);

        assertThat(action1.equals(action2), is(true));
        assertThat(action1.hashCode() == action2.hashCode(), is(true));
        assertThat(action1.equals(action3), is(false));
    }
}
