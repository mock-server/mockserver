package org.mockserver.integration.proxy;

import com.google.common.base.Charsets;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.hamcrest.core.Is;
import org.junit.Test;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.socket.KeyStoreFactory;
import org.mockserver.streams.IOStreamUtils;

import java.io.OutputStream;
import java.net.Socket;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.test.Assert.assertContains;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientProxyIntegrationTest {

    protected static String servletContext = "";

    protected HttpClient createHttpClient() throws Exception {
        return HttpClients
                .custom()
                .setSSLSocketFactory(new SSLConnectionSocketFactory(KeyStoreFactory.keyStoreFactory().sslContext(), NoopHostnameVerifier.INSTANCE))
                .setRoutePlanner(new DefaultProxyRoutePlanner(
                        new HttpHost(
                                System.getProperty("http.proxyHost", "localhost"),
                                Integer.parseInt(System.getProperty("http.proxyPort", String.valueOf(getProxyPort())))
                        )
                )).build();
    }

    public abstract int getProxyPort();

    public abstract ProxyClient getProxyClient();

    public abstract int getServerPort();

    protected String calculatePath(String path) {
        return ("/" + servletContext + "/" + path).replaceAll("///|//", "/");
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectly() throws Exception {
        Socket socket = null;
        try {
            socket = new Socket("localhost", getProxyPort());

            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request for headers only
            output.write(("" +
                    "GET " + calculatePath("test_headers_only") + " HTTP/1.1\r" + NEW_LINE +
                    "Host: localhost:" + getServerPort() + "\r" + NEW_LINE +
                    "x-test: test_headers_only\r" + NEW_LINE +
                    "Connection: keep-alive\r" + NEW_LINE +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "x-test: test_headers_only");

            // and
            getProxyClient().verify(
                    request()
                            .withMethod("GET")
                            .withPath("/test_headers_only"),
                    exactly(1)
            );

            // - send GET request for headers and body
            output.write(("" +
                    "GET " + calculatePath("test_headers_and_body") + " HTTP/1.1\r" + NEW_LINE +
                    "Host: localhost:" + getServerPort() + "\r" + NEW_LINE +
                    "Content-Length: " + "an_example_body".getBytes(Charsets.UTF_8).length + "\r" + NEW_LINE +
                    "x-test: test_headers_and_body\r" + NEW_LINE +
                    "\r" + NEW_LINE +
                    "an_example_body"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "x-test: test_headers_and_body");
            assertContains(response, "an_example_body");

            // and
            getProxyClient().verify(
                    request()
                            .withMethod("GET")
                            .withPath("/test_headers_and_body")
                            .withBody("an_example_body"),
                    exactly(1)
            );
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldForwardRequestsUsingHttpClient() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        HttpPost request = new HttpPost(
                new URIBuilder()
                        .setScheme("http")
                        .setHost("localhost")
                        .setPort(getServerPort())
                        .setPath(calculatePath("test_headers_and_body"))
                        .build()
        );
        request.setEntity(new StringEntity("an_example_body"));
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals("an_example_body", new String(EntityUtils.toByteArray(response.getEntity()), com.google.common.base.Charsets.UTF_8));

        // and
        getProxyClient().verify(
                request()
                        .withMethod("POST")
                        .withPath("/test_headers_and_body")
                        .withBody("an_example_body"),
                exactly(1)
        );
    }

    @Test
    public void shouldForwardRequestsWithComplexCookies() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();
        Header setCookieOneHeader = new BasicHeader("Set-Cookie", "personalization_59996b985e24ce008d3df3bd07e27c1b=\"\"; Expires=Thu, 01-Jan-1970 00:00:10 GMT; Path=/");
        Header setCookieTwoHeader = new BasicHeader("Set-Cookie", "anonymous_59996b985e24ce008d3df3bd07e27c1b=acgzEaAKOVR=mAY9yJhP7IrC9Am; Version=1; Comment=\"Anonymous cookie for site\"; Max-Age=15552000; Expires=Sat, 19-Mar-2016 18:43:26 GMT; Path=/");
        Header cookieOneHeader = new BasicHeader("Cookie", "personalization_59996b985e24ce008d3df3bd07e27c1b=\"\"");
        Header cookieTwoHeader = new BasicHeader("Cookie", "anonymous_59996b985e24ce008d3df3bd07e27c1b=\"acgzEaAKOVR=mAY9yJhP7IrC9Am\"");

        // when
        HttpPost request = new HttpPost(
                new URIBuilder()
                        .setScheme("http")
                        .setHost("localhost")
                        .setPort(getServerPort())
                        .setPath(calculatePath("test_headers_and_body"))
                        .build()
        );
        request.addHeader(setCookieOneHeader);
        request.addHeader(setCookieTwoHeader);
        request.addHeader(cookieOneHeader);
        request.addHeader(cookieTwoHeader);
        request.setEntity(new StringEntity("an_example_body"));
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertThat(response.getHeaders("Set-Cookie").length, is(2));
        assertThat(response.getHeaders("Set-Cookie")[0].getValue(), is(setCookieOneHeader.getValue()));
        assertThat(response.getHeaders("Set-Cookie")[1].getValue(), is(setCookieTwoHeader.getValue()));
        assertEquals("an_example_body", new String(EntityUtils.toByteArray(response.getEntity()), com.google.common.base.Charsets.UTF_8));

        // and
        getProxyClient().verify(
                request()
                        .withMethod("POST")
                        .withPath("/test_headers_and_body")
                        .withHeader(setCookieOneHeader.getName(), setCookieOneHeader.getValue())
                        .withHeader(setCookieTwoHeader.getName(), setCookieTwoHeader.getValue())
                        .withCookie("personalization_59996b985e24ce008d3df3bd07e27c1b", "")
                        .withCookie("anonymous_59996b985e24ce008d3df3bd07e27c1b", "acgzEaAKOVR=mAY9yJhP7IrC9Am")
                        .withBody("an_example_body"),
                exactly(1)
        );
    }

    @Test
    public void shouldForwardRequestsToUnknownPath() throws Exception {
        Socket socket = null;
        try {
            socket = new Socket("localhost", getProxyPort());
            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request
            output.write(("" +
                    "GET " + calculatePath("not_found") + " HTTP/1.1\r" + NEW_LINE +
                    "Host: localhost:" + getServerPort() + "\r" + NEW_LINE +
                    "Connection: close\r" + NEW_LINE +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 404 Not Found");

            // and
            getProxyClient().verify(
                    request()
                            .withMethod("GET")
                            .withPath("/not_found"),
                    exactly(1)
            );
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldVerifyRequests() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_and_body"))
                                .build()
                )
        );
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_only"))
                                .build()
                )
        );

        // then
        getProxyClient()
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body")
                );
        getProxyClient()
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body"),
                        exactly(1)
                );
        getProxyClient()
                .verify(
                        request()
                                .withPath("/test_headers_.*"),
                        atLeast(1)
                );
        getProxyClient()
                .verify(
                        request()
                                .withPath("/test_headers_.*"),
                        exactly(2)
                );
        getProxyClient()
                .verify(
                        request()
                                .withPath("/other_path"),
                        exactly(0)
                );
    }

    @Test
    public void shouldVerifyRequestsSequence() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_and_body"))
                                .build()
                )
        );
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_only"))
                                .build()
                )
        );

        // then
        getProxyClient()
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body"),
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_only")
                );
    }

    @Test
    public void shouldVerifyRequestsWithHopByHopHeaders() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        HttpGet httpGet = new HttpGet(
                new URIBuilder()
                        .setScheme("http")
                        .setHost("localhost")
                        .setPort(getServerPort())
                        .setPath(calculatePath("test_headers_only"))
                        .build()
        );
        httpGet.addHeader("Proxy-Authorization", "some-random_value");
        httpGet.addHeader("keep-alive", "false");
        httpClient.execute(httpGet);

        // then
        getProxyClient()
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_only")
                                .withHeader("Proxy-Authorization", "some-random_value")
                                .withHeader("keep-alive", "false")
                );
        getProxyClient()
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_only")
                                .withHeader("Proxy-Authorization")
                                .withHeader("keep-alive"),
                        exactly(1)
                );
    }

    @Test
    public void shouldVerifyZeroRequests() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_and_body"))
                                .build()
                )
        );

        // then
        try {
            getProxyClient()
                    .verify(
                            request()
                                    .withPath("/test_headers_and_body"), exactly(0)
                    );
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly 0 times, expected:<{" + NEW_LINE +
                    "  \"path\" : \"" + "/test_headers_and_body" + "\"" + NEW_LINE +
                    "}> but was:<{" + NEW_LINE +
                    "  \"method\" : \"GET\"," + NEW_LINE +
                    "  \"path\" : \"" + "/test_headers_and_body" + "\"," + NEW_LINE));
        }
    }

    @Test
    public void shouldVerifyNoRequestsExactly() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_and_body"))
                                .build()
                )
        );

        // then
        try {
            getProxyClient()
                    .verify(
                            request()
                                    .withPath("/other_path"),
                            exactly(1)
                    );
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly once, expected:<{" + NEW_LINE +
                    "  \"path\" : \"" + "/other_path" + "\"" + NEW_LINE +
                    "}> but was:<{" + NEW_LINE +
                    "  \"method\" : \"GET\"," + NEW_LINE +
                    "  \"path\" : \"" + "/test_headers_and_body" + "\"," + NEW_LINE));
        }
    }

    @Test
    public void shouldVerifyNoRequestsTimesNotSpecified() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_and_body"))
                                .build()
                )
        );

        // then
        try {
            getProxyClient()
                    .verify(
                            request()
                                    .withPath("/other_path")
                    );
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                    "  \"path\" : \"" + "/other_path" + "\"" + NEW_LINE +
                    "} ]> but was:<[ {" + NEW_LINE +
                    "  \"method\" : \"GET\"," + NEW_LINE +
                    "  \"path\" : \"" + "/test_headers_and_body" + "\"," + NEW_LINE));
        }
    }

    @Test
    public void shouldVerifyNotEnoughRequests() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_and_body"))
                                .build()
                )
        );
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_and_body"))
                                .build()
                )
        );

        // then
        try {
            getProxyClient()
                    .verify(
                            request()
                                    .withPath("/test_headers_and_body"),
                            atLeast(3)
                    );
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found at least 3 times, expected:<{" + NEW_LINE +
                    "  \"path\" : \"" + "/test_headers_and_body" + "\"" + NEW_LINE +
                    "}> but was:<[ {" + NEW_LINE +
                    "  \"method\" : \"GET\"," + NEW_LINE +
                    "  \"path\" : \"" + "/test_headers_and_body" + "\"," + NEW_LINE));
        }
    }

    @Test
    public void shouldVerifyRequestsSequenceNotFound() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_and_body"))
                                .build()
                )
        );
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_only"))
                                .build()
                )
        );

        // then
        try {
            getProxyClient()
                    .verify(
                            request()
                                    .withMethod("GET")
                                    .withPath("/test_headers_only"),
                            request()
                                    .withMethod("GET")
                                    .withPath("/test_headers_and_body")
                    );
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                    "  \"method\" : \"GET\"," + NEW_LINE +
                    "  \"path\" : \"/test_headers_only\"" + NEW_LINE +
                    "}, {" + NEW_LINE +
                    "  \"method\" : \"GET\"," + NEW_LINE +
                    "  \"path\" : \"/test_headers_and_body\"" + NEW_LINE +
                    "} ]> but was:<[ {" + NEW_LINE +
                    "  \"method\" : \"GET\"," + NEW_LINE +
                    "  \"path\" : \"/test_headers_and_body\"," + NEW_LINE +
                    "  \"headers\" : [ {"));
        }
    }

    @Test
    public void shouldClearRequests() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_and_body"))
                                .build()
                )
        );
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_only"))
                                .build()
                )
        );
        getProxyClient()
                .clear(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body")
                );

        // then
        getProxyClient()
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body"),
                        exactly(0)
                );
        getProxyClient()
                .verify(
                        request()
                                .withPath("/test_headers_.*"),
                        exactly(1)
                );
    }

    @Test
    public void shouldResetRequests() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_and_body"))
                                .build()
                )
        );
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath(calculatePath("test_headers_only"))
                                .build()
                )
        );
        getProxyClient().reset();

        // then
        getProxyClient()
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body"),
                        exactly(0)
                );
        getProxyClient()
                .verify(
                        request()
                                .withPath("/test_headers_.*"),
                        atLeast(0)
                );
        getProxyClient()
                .verify(
                        request()
                                .withPath("/test_headers_.*"),
                        exactly(0)
                );
    }

    @Test
    public void shouldReturnErrorForInvalidRequestToClear() {
        // when
        org.mockserver.model.HttpResponse httpResponse = new NettyHttpClient().sendRequest(
                request()
                        .withMethod("PUT")
                        .withHeader(HOST.toString(), "localhost:" + getProxyPort())
                        .withPath(calculatePath("/clear"))
                        .withBody("{" + NEW_LINE +
                                "    \"path\" : 500," + NEW_LINE +
                                "    \"method\" : true," + NEW_LINE +
                                "    \"keepAlive\" : \"false\"" + NEW_LINE +
                                "  }")
        );

        // then
        assertThat(httpResponse.getStatusCode(), Is.is(400));
        assertThat(httpResponse.getBodyAsString(), Is.is("3 errors:" + NEW_LINE +
                " - instance type (string) does not match any allowed primitive type (allowed: [\"boolean\"]) for field \"/keepAlive\"" + NEW_LINE +
                " - instance type (boolean) does not match any allowed primitive type (allowed: [\"string\"]) for field \"/method\"" + NEW_LINE +
                " - instance type (integer) does not match any allowed primitive type (allowed: [\"string\"]) for field \"/path\""));
    }

    @Test
    public void shouldReturnErrorForInvalidRequestToVerify() {
        // when
        org.mockserver.model.HttpResponse httpResponse = new NettyHttpClient().sendRequest(
                request()
                        .withMethod("PUT")
                        .withHeader(HOST.toString(), "localhost:" + getProxyPort())
                        .withPath(calculatePath("/verify"))
                        .withBody("{" + NEW_LINE +
                                "    \"httpRequest\": {" + NEW_LINE +
                                "        \"path\": \"/simple\"" + NEW_LINE +
                                "    }, " + NEW_LINE +
                                "    \"times\": 1" + NEW_LINE +
                                "}")
        );

        // then
        assertThat(httpResponse.getStatusCode(), Is.is(400));
        assertThat(httpResponse.getBodyAsString(), Is.is("1 error:" + NEW_LINE +
                " - instance type (integer) does not match any allowed primitive type (allowed: [\"object\"]) for field \"/times\""));
    }

    @Test
    public void shouldReturnErrorForInvalidRequestToVerifySequence() {
        // when
        org.mockserver.model.HttpResponse httpResponse = new NettyHttpClient().sendRequest(
                request()
                        .withMethod("PUT")
                        .withHeader(HOST.toString(), "localhost:" + getProxyPort())
                        .withPath(calculatePath("/verifySequence"))
                        .withBody("{" + NEW_LINE +
                                "    \"httpRequest\": {" + NEW_LINE +
                                "        \"path\": false" + NEW_LINE +
                                "    }," + NEW_LINE +
                                "    \"httpRequest\": {" + NEW_LINE +
                                "        \"path\": 10" + NEW_LINE +
                                "    }" + NEW_LINE +
                                "}")
        );

        // then
        assertThat(httpResponse.getStatusCode(), Is.is(400));
        assertThat(httpResponse.getBodyAsString(), Is.is("1 error:" + NEW_LINE +
                " - object instance has properties which are not allowed by the schema: [\"httpRequest\"]"));
    }
}
