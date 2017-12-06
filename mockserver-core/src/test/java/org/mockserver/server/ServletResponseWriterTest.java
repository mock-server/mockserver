package org.mockserver.server;

import io.netty.channel.ChannelFutureListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

/**
 * @author jamesdbloom
 */
public class ServletResponseWriterTest {

    @Mock
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder;
    @InjectMocks
    private ServletResponseWriter servletResponseWriter;
    private MockHttpServletResponse httpServletResponse;

    @Before
    public void setupTestFixture() {
        httpServletResponse = new MockHttpServletResponse();
        servletResponseWriter = new ServletResponseWriter(httpServletResponse);
        initMocks(this);
    }

    @Test
    public void shouldWriteBasicResponse() {
        // given
        HttpRequest request = request("some_request");
        HttpResponse response = response("some_response");

        // when
        servletResponseWriter.writeResponse(request.clone(), response.clone());

        // then
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(
                response("some_response"),
                httpServletResponse
        );
    }

    @Test
    public void shouldWriteContentTypeForStringBody() {
        // given
        HttpRequest request = request("some_request");
        HttpResponse response = response().withBody("some_response", UTF_8);

        // when
        servletResponseWriter.writeResponse(request.clone(), response.clone());

        // then
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(
                response()
                        .withHeader("content-type", "text/plain; charset=utf-8; charset=utf-8")
                        .withBody("some_response", UTF_8),
                httpServletResponse
        );
    }

    @Test
    public void shouldWriteContentTypeForJsonBody() {
        // given
        HttpRequest request = request("some_request");
        HttpResponse response = response().withBody(json("some_response"));

        // when
        servletResponseWriter.writeResponse(request.clone(), response.clone());

        // then
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(
                response()
                        .withHeader("content-type", "application/json")
                        .withBody(json("some_response")),
                httpServletResponse
        );
    }

    @Test
    public void shouldWriteNullResponse() {
        // given
        HttpRequest request = request("some_request");

        // when
        servletResponseWriter.writeResponse(request.clone(), (HttpResponse) null);

        // then
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(
                notFoundResponse(),
                httpServletResponse
        );
    }

    @Test
    public void shouldWriteAddCORSHeaders() {
        boolean enableCORSForAllResponses = enableCORSForAllResponses();
        try {
            // given
            enableCORSForAllResponses(true);
            HttpRequest request = request("some_request");
            HttpResponse response = response("some_response");

            // when
            servletResponseWriter.writeResponse(request.clone(), response.clone());

            // then
            verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(
                    response
                            .withHeader("Access-Control-Allow-Origin", "*")
                            .withHeader("Access-Control-Allow-Methods", "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE")
                            .withHeader("Access-Control-Allow-Headers", "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary")
                            .withHeader("Access-Control-Expose-Headers", "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary")
                            .withHeader("Access-Control-Max-Age", "300")
                            .withHeader("X-CORS", "MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"),
                    httpServletResponse
            );
        } finally {
            enableCORSForAllResponses(enableCORSForAllResponses);
        }
    }

}