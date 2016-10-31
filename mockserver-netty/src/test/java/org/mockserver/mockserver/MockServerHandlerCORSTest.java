package org.mockserver.mockserver;

import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class MockServerHandlerCORSTest extends MockServerHandlerTest {

    @Test
    public void shouldAddCORSHeadersForOptionsRequest() {
        // given - a request
        HttpRequest request = request().withMethod("OPTIONS").withHeader("Origin", "some_origin");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(200));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORS(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldNotAddCORSHeadersForOptionsRequestWithoutOrigin() {
        // given - a request
        HttpRequest request = request().withMethod("OPTIONS");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(404));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), empty());
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), empty());
        assertThat(httpResponse.getHeader("X-CORS"), empty());
    }

    @Test
    public void shouldNotAddCORSHeadersForOptionsRequestIfCORSDisabled() {
        boolean originalValue = ConfigurationProperties.enableCORS();
        try {
            // given - a request
            HttpRequest request = request().withMethod("OPTIONS").withHeader("Origin", "some_origin");

            // and - cors disabled
            ConfigurationProperties.enableCORS(false);

            // when
            embeddedChannel.writeInbound(request);

            // then - correct response written to ChannelHandlerContext
            HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
            assertThat(httpResponse.getStatusCode(), is(404));
            assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), empty());
            assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), empty());
            assertThat(httpResponse.getHeader("X-CORS"), empty());
        } finally {
            ConfigurationProperties.enableCORS(originalValue);
        }
    }

    @Test
    public void shouldAddCORSHeadersToStatusRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/status");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORS(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldNotAddCORSHeadersForStatusRequestIfCORSDisabled() {
        boolean originalValue = ConfigurationProperties.enableCORS();
        try {
            // given - a request
            HttpRequest request = request().withMethod("PUT").withPath("/status");

            // and - cors disabled
            ConfigurationProperties.enableCORS(false);

            // when
            embeddedChannel.writeInbound(request);

            // then - correct response written to ChannelHandlerContext
            HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
            assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), empty());
            assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), empty());
            assertThat(httpResponse.getHeader("X-CORS"), empty());
        } finally {
            ConfigurationProperties.enableCORS(originalValue);
        }
    }

    @Test
    public void shouldAddCORSHeadersToBindRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/bind");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORS(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToExpectationRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/expectation");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORS(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToResetRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/reset");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORS(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToDumpToLogRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/dumpToLog");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORS(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToRetriveRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/retrieve");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORS(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToVerifyRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/verify");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORS(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToVerifySequenceRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/verifySequence");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORS(false) or -Dmockserver.disableCORS=false"));
    }

}
