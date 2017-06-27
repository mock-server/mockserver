package org.mockserver.mockserver;

import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
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
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
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
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), empty());
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), empty());
        assertThat(httpResponse.getHeader("X-CORS"), empty());
    }

    @Test
    public void shouldNotAddCORSHeadersForOptionsRequestIfCORSDisabled() {
        boolean originalValue = ConfigurationProperties.enableCORSForAPI();
        try {
            // given - a request
            HttpRequest request = request().withMethod("OPTIONS").withHeader("Origin", "some_origin");

            // and - cors disabled
            ConfigurationProperties.enableCORSForAPI(false);

            // when
            embeddedChannel.writeInbound(request);

            // then - correct response written to ChannelHandlerContext
            HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
            assertThat(httpResponse.getStatusCode(), is(404));
            assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), empty());
            assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), empty());
            assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), empty());
            assertThat(httpResponse.getHeader("Access-Control-Max-Age"), empty());
            assertThat(httpResponse.getHeader("X-CORS"), empty());
        } finally {
            ConfigurationProperties.enableCORSForAPI(originalValue);
        }
    }

    @Test
    public void shouldAddCORSHeadersForOptionsRequestIfEnabledForAllRequestButDisabledForAPI() {
        boolean originalValueForAPI = ConfigurationProperties.enableCORSForAPI();
        boolean originalValueForAllRequests = ConfigurationProperties.enableCORSForAllResponses();
        try {
            // given - a request
            HttpRequest request = request().withMethod("OPTIONS").withHeader("Origin", "some_origin");

            // and - cors for API disabled
            ConfigurationProperties.enableCORSForAPI(false);

            // but - cors for all request enabled
            ConfigurationProperties.enableCORSForAllResponses(true);

            // when
            embeddedChannel.writeInbound(request);

            // then - correct response written to ChannelHandlerContext
            HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
            assertThat(httpResponse.getStatusCode(), is(200));
            assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
            assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
            assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
            assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
            assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
            assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
        } finally {
            ConfigurationProperties.enableCORSForAPI(originalValueForAPI);
            ConfigurationProperties.enableCORSForAllResponses(originalValueForAllRequests);
        }
    }

    @Test
    public void shouldAddCORSHeadersToRandomRequestIfDisabledForAllRequest() {
        // given - a request
        HttpRequest request = request().withPath("/randomPath");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), empty());
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), empty());
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), empty());
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), empty());
        assertThat(httpResponse.getHeader("X-CORS"), empty());
    }

    @Test
    public void shouldAddCORSHeadersToRandomRequestIfEnabledForAllRequest() {
        boolean originalValue = ConfigurationProperties.enableCORSForAllResponses();
        try {
            // given - a request
            HttpRequest request = request().withPath("/randomPath");

            // and - cors disabled
            ConfigurationProperties.enableCORSForAllResponses(true);

            // when
            embeddedChannel.writeInbound(request);

            // then - correct response written to ChannelHandlerContext
            HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
            assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
            assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
            assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
            assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
            assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
            assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
        } finally {
            ConfigurationProperties.enableCORSForAllResponses(originalValue);
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
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldNotAddCORSHeadersForStatusRequestIfCORSDisabled() {
        boolean originalValue = ConfigurationProperties.enableCORSForAPI();
        try {
            // given - a request
            HttpRequest request = request().withMethod("PUT").withPath("/status");

            // and - cors disabled
            ConfigurationProperties.enableCORSForAPI(false);

            // when
            embeddedChannel.writeInbound(request);

            // then - correct response written to ChannelHandlerContext
            HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
            assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), empty());
            assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), empty());
            assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), empty());
            assertThat(httpResponse.getHeader("Access-Control-Max-Age"), empty());
            assertThat(httpResponse.getHeader("X-CORS"), empty());
        } finally {
            ConfigurationProperties.enableCORSForAPI(originalValue);
        }
    }

    @Test
    public void shouldNotAddCORSHeadersForInvalidRequest() {
        // given - a request with missing body
        HttpRequest request = request().withMethod("PUT").withPath("/bind").withBody("\n\n--- IGNORE THE FOLLOWING \"Exception while parsing PortBinding\" EXCEPTION ---\n\n");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getStatusCode(), is(BAD_REQUEST.code()));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), empty());
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), empty());
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), empty());
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), empty());
        assertThat(httpResponse.getHeader("X-CORS"), empty());
    }

    @Test
    public void shouldAddCORSHeadersToBindRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/bind").withBody("{\n" +
                "    \"ports\": [\n" +
                "        0\n" +
                "    ]\n" +
                "}");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
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
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
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
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
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
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
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
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToVerifyRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/verify");
        when(mockRequestLogFilter.verify(any(Verification.class))).thenReturn("some_verification_response");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToVerifySequenceRequest() {
        // given - a request
        HttpRequest request = request().withMethod("PUT").withPath("/verifySequence");
        when(mockRequestLogFilter.verify(any(VerificationSequence.class))).thenReturn("some_verification_response");

        // when
        embeddedChannel.writeInbound(request);

        // then - correct response written to ChannelHandlerContext
        HttpResponse httpResponse = (HttpResponse) embeddedChannel.readOutbound();
        assertThat(httpResponse.getHeader("Access-Control-Allow-Origin"), contains("*"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Methods"), contains("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpResponse.getHeader("Access-Control-Allow-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Expose-Headers"), contains("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpResponse.getHeader("Access-Control-Max-Age"), contains("1"));
        assertThat(httpResponse.getHeader("X-CORS"), contains("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

}
