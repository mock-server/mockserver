package org.mockserver.cors;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.cors.CORSHeaders.isPreflightRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class CORSHeadersTest {

    private final Configuration configuration = configuration();

    @Test
    public void shouldDetectPreflightRequest() {
        assertThat(isPreflightRequest(
            configuration,
            request()
                .withMethod("OPTIONS")
                .withHeader("origin", "some_origin_header")
                .withHeader("access-control-request-method", "true")
        ), is(true));
        assertThat(isPreflightRequest(
            configuration,
            request()
                .withMethod("GET")
                .withHeader("origin", "some_origin_header")
                .withHeader("access-control-request-method", "true")
        ), is(false));
        assertThat(isPreflightRequest(
            configuration,
            request()
                .withMethod("OPTIONS")
                .withHeader("not_origin", "some_origin_header")
                .withHeader("access-control-request-method", "true")
        ), is(false));
        assertThat(isPreflightRequest(
            configuration,
            request()
                .withMethod("OPTIONS")
                .withHeader("origin", "some_origin_header")
                .withHeader("not_access-control-request-method", "true")
        ), is(false));
    }

    @Test
    public void shouldAddCORSSpecificDomain() {
        // given
        HttpRequest request = request();
        HttpResponse response = response();
        CORSHeaders corsHeaders = new CORSHeaders(
            "www.mock-server.com",
            "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization",
            "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE",
            true,
            300
        );

        // when
        corsHeaders.addCORSHeaders(request, response);

        // then
        assertThat(response.getFirstHeader("access-control-allow-origin"), is("www.mock-server.com"));
        assertThat(response.getFirstHeader("access-control-allow-methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"));
        assertThat(response.getFirstHeader("access-control-allow-headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getFirstHeader("access-control-expose-headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getFirstHeader("access-control-max-age"), is("300"));
    }

    @Test
    public void shouldAddCORSHeader() {
        // given
        HttpRequest request = request();
        HttpResponse response = response();
        CORSHeaders corsHeaders = new CORSHeaders(
            "*",
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
            "*",
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
            "*",
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
            "*",
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
            "*",
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

    @Test
    public void shouldReflectOriginAndDefaultMethodsHeadersWhenBlank() {
        // given - all CORS settings left blank (the defaults), no credentials, a
        // cross-origin browser request
        HttpRequest request = request()
            .withHeader("origin", "http://localhost:3000");
        HttpResponse response = response();
        CORSHeaders corsHeaders = new CORSHeaders(
            "",
            "",
            "",
            false,
            300
        );

        // when
        corsHeaders.addCORSHeaders(request, response);

        // then - origin reflected, and methods/headers default to usable values
        // (not empty) so a PUT preflight from the dashboard passes
        assertThat(response.getFirstHeader("access-control-allow-origin"), is("http://localhost:3000"));
        assertThat(response.getFirstHeader("access-control-allow-methods"), containsString("PUT"));
        assertThat(response.getFirstHeader("access-control-allow-headers"), containsString("Content-Type"));
    }

    @Test
    public void shouldFallBackToAnyOriginWhenAllowOriginBlankAndNoOrigin() {
        // given - default (blank) corsAllowOrigin, no Origin header on the request
        HttpRequest request = request();
        HttpResponse response = response();
        CORSHeaders corsHeaders = new CORSHeaders(
            "",
            "Allow, Content-Type",
            "GET, PUT, OPTIONS",
            false,
            300
        );

        // when
        corsHeaders.addCORSHeaders(request, response);

        // then
        assertThat(response.getFirstHeader("access-control-allow-origin"), is("*"));
    }


    @Test
    public void shouldKeepReflectingAnyOriginForMockResponsesWhenAllowOriginBlank() {
        // the corsAllowOrigin policy for mocked and proxied responses is unchanged: any Origin is reflected
        HttpRequest request = request()
            .withHeader("host", "localhost:1080")
            .withHeader("origin", "http://frontend:3000");
        HttpResponse response = response();

        new CORSHeaders("", "", "", false, 300).addCORSHeaders(request, response);

        assertThat(response.getFirstHeader("access-control-allow-origin"), is("http://frontend:3000"));
    }

    @Test
    public void shouldKeepExplicitAllowOriginForMockResponses() {
        HttpRequest request = request()
            .withHeader("host", "localhost:1080")
            .withHeader("origin", "https://attacker.example");
        HttpResponse response = response();

        new CORSHeaders("https://app.example.com", "", "", false, 300).addCORSHeaders(request, response);

        assertThat(response.getFirstHeader("access-control-allow-origin"), is("https://app.example.com"));
    }

    @Test
    public void shouldAddNoControlPlaneCORSHeadersForAForeignOrigin() {
        // a preflight from a page on another site
        HttpRequest request = request()
            .withMethod("OPTIONS")
            .withHeader("host", "localhost:1080")
            .withHeader("origin", "https://attacker.example")
            .withHeader("access-control-request-method", "PUT")
            .withHeader("access-control-request-headers", "content-type");
        HttpResponse response = response();

        new CORSHeaders(configuration()).addControlPlaneCORSHeaders(request, response);

        assertThat(response.getHeaderList().stream().anyMatch(header -> header.getName().getValue().toLowerCase().startsWith("access-control-")), is(false));
    }

    @Test
    public void shouldAddNoControlPlaneCORSHeadersForAForeignOriginEvenWithCredentialsOrCorsAllowOrigin() {
        HttpRequest request = request()
            .withHeader("host", "localhost:1080")
            .withHeader("origin", "https://attacker.example");
        HttpResponse response = response();

        new CORSHeaders(configuration().corsAllowOrigin("*").corsAllowCredentials(true)).addControlPlaneCORSHeaders(request, response);

        assertThat(response.containsHeader("access-control-allow-origin"), is(false));
        assertThat(response.containsHeader("access-control-allow-credentials"), is(false));
    }

    @Test
    public void shouldAddNoControlPlaneCORSHeadersForNullOrigin() {
        HttpRequest request = request()
            .withHeader("host", "localhost:1080")
            .withHeader("origin", "null");
        HttpResponse response = response();

        new CORSHeaders(configuration()).addControlPlaneCORSHeaders(request, response);

        assertThat(response.containsHeader("access-control-allow-origin"), is(false));
    }

    @Test
    public void shouldReflectLoopbackOriginsOnControlPlaneResponses() {
        for (String origin : new String[]{"http://localhost:3000", "https://localhost", "http://127.0.0.1:5173", "http://[::1]:8080"}) {
            HttpRequest request = request()
                .withHeader("host", "mockserver.internal:1080")
                .withHeader("origin", origin);
            HttpResponse response = response();

            new CORSHeaders(configuration()).addControlPlaneCORSHeaders(request, response);

            assertThat(origin, response.getFirstHeader("access-control-allow-origin"), is(origin));
            assertThat(origin, response.getFirstHeader("access-control-allow-methods"), containsString("PUT"));
        }
    }

    @Test
    public void shouldReflectSameHostOriginOnControlPlaneResponses() {
        HttpRequest request = request()
            .withHeader("host", "mockserver.default.svc.cluster.local:1080")
            .withHeader("origin", "https://mockserver.default.svc.cluster.local:1080");
        HttpResponse response = response();

        new CORSHeaders(configuration()).addControlPlaneCORSHeaders(request, response);

        assertThat(response.getFirstHeader("access-control-allow-origin"), is("https://mockserver.default.svc.cluster.local:1080"));
    }

    @Test
    public void shouldNotReflectASameHostOriginOnAPublicName() {
        HttpRequest request = request()
            .withHeader("host", "evil.example.com:1080")
            .withHeader("origin", "http://evil.example.com:1080");
        HttpResponse response = response();

        new CORSHeaders(configuration()).addControlPlaneCORSHeaders(request, response);

        assertThat(response.containsHeader("access-control-allow-origin"), is(false));
    }

    @Test
    public void shouldReflectASameHostOriginOnAConfiguredTrustedSuffix() {
        HttpRequest request = request()
            .withHeader("host", "mockserver.corp.example:1080")
            .withHeader("origin", "https://mockserver.corp.example:1080");
        HttpResponse response = response();

        new CORSHeaders(configuration().controlPlaneTrustedHostSuffixes("corp.example")).addControlPlaneCORSHeaders(request, response);

        assertThat(response.getFirstHeader("access-control-allow-origin"), is("https://mockserver.corp.example:1080"));
    }

    @Test
    public void shouldReflectAnOriginInTheControlPlaneAllowList() {
        HttpRequest request = request()
            .withHeader("host", "localhost:1080")
            .withHeader("origin", "https://app.example.com");
        HttpResponse response = response();

        new CORSHeaders(configuration().controlPlaneCorsAllowOrigin("https://other.example.com, https://app.example.com")).addControlPlaneCORSHeaders(request, response);

        assertThat(response.getFirstHeader("access-control-allow-origin"), is("https://app.example.com"));
    }

    @Test
    public void shouldAnswerControlPlaneRequestsWithoutOriginWithAnyOrigin() {
        HttpResponse response = response();

        new CORSHeaders(configuration()).addControlPlaneCORSHeaders(request(), response);

        assertThat(response.getFirstHeader("access-control-allow-origin"), is("*"));
    }

}
