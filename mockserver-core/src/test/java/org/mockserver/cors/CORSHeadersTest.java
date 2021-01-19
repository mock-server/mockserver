package org.mockserver.cors;

import org.junit.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.cors.CORSHeaders.isPreflightRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class CORSHeadersTest {

    @Test
    public void shouldDetectPreflightRequest() {
        assertThat(isPreflightRequest(
            request()
                .withMethod("OPTIONS")
                .withHeader("origin", "some_origin_header")
                .withHeader("access-control-request-method", "true")
        ), is(true));
        assertThat(isPreflightRequest(
            request()
                .withMethod("GET")
                .withHeader("origin", "some_origin_header")
                .withHeader("access-control-request-method", "true")
        ), is(false));
        assertThat(isPreflightRequest(
            request()
                .withMethod("OPTIONS")
                .withHeader("not_origin", "some_origin_header")
                .withHeader("access-control-request-method", "true")
        ), is(false));
        assertThat(isPreflightRequest(
            request()
                .withMethod("OPTIONS")
                .withHeader("origin", "some_origin_header")
                .withHeader("not_access-control-request-method", "true")
        ), is(false));
    }

    @Test
    public void shouldAddCORSHeader() {
        // given
        HttpRequest request = request();
        HttpResponse response = response();
        CORSHeaders corsHeaders = new CORSHeaders(
            "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization",
            "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE",
            true,
            300
        );

        // when
        corsHeaders.addCORSHeaders(request, response);

        // then
        assertThat(response.getFirstHeader("access-control-allow-origin"), is("*"));
        assertThat(response.getFirstHeader("access-control-allow-methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"));
        assertThat(response.getFirstHeader("access-control-allow-headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getFirstHeader("access-control-expose-headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getFirstHeader("access-control-max-age"), is("300"));
    }

    @Test
    public void shouldAddCORSHeaderForNullOrigin() {
        // given
        HttpRequest request = request()
            .withHeader("origin", "null");
        HttpResponse response = response();
        CORSHeaders corsHeaders = new CORSHeaders(
            "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization",
            "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE",
            true,
            300
        );


        // when
        corsHeaders.addCORSHeaders(request, response);

        // then
        assertThat(response.getFirstHeader("access-control-allow-origin"), is("null"));
        assertThat(response.getFirstHeader("access-control-allow-methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"));
        assertThat(response.getFirstHeader("access-control-allow-headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getFirstHeader("access-control-expose-headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getFirstHeader("access-control-max-age"), is("300"));
    }

    @Test
    public void shouldAddCORSHeaderForAllowCredentials() {
        // given
        HttpRequest request = request()
            .withHeader("origin", "some_origin_value");
        HttpResponse response = response();
        CORSHeaders corsHeaders = new CORSHeaders(
            "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization",
            "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE",
            true,
            300
        );


        // when
        corsHeaders.addCORSHeaders(request, response);

        // then
        assertThat(response.getFirstHeader("access-control-allow-origin"), is("some_origin_value"));
        assertThat(response.getFirstHeader("access-control-allow-credentials"), is("true"));
        assertThat(response.getFirstHeader("access-control-allow-methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"));
        assertThat(response.getFirstHeader("access-control-allow-headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getFirstHeader("access-control-expose-headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getFirstHeader("access-control-max-age"), is("300"));
    }

    @Test
    public void shouldAddCORSHeaderForAllowCredentialsWithoutOrigin() {
        // given
        HttpRequest request = request();
        HttpResponse response = response();
        CORSHeaders corsHeaders = new CORSHeaders(
            "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization",
            "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE",
            true,
            300
        );


        // when
        corsHeaders.addCORSHeaders(request, response);

        // then
        assertThat(response.getFirstHeader("access-control-allow-origin"), is("*"));
        assertThat(response.getFirstHeader("access-control-allow-methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"));
        assertThat(response.getFirstHeader("access-control-allow-headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getFirstHeader("access-control-expose-headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getFirstHeader("access-control-max-age"), is("300"));
    }

    @Test
    public void shouldAddCORSHeaderForExtraRequestHeaders() {
        // given
        HttpRequest request = request()
            .withMethod("OPTIONS")
            .withHeader("Access-Control-Request-Headers", "X-API-Key");
        HttpResponse response = response();
        CORSHeaders corsHeaders = new CORSHeaders(
            "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization",
            "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE",
            true,
            300
        );


        // when
        corsHeaders.addCORSHeaders(request, response);

        // then
        assertThat(response.getFirstHeader("access-control-allow-headers"), containsString("X-API-Key"));
    }

}
