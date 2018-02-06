package org.mockserver.integration.server;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.MockServerClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.model.*;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public abstract class AbstractMockingIntegrationTestBase {

    protected static MockServerClient mockServerClient;
    protected static String servletContext = "";
    protected static List<String> headersToIgnore = ImmutableList.of(
        HttpHeaderNames.SERVER.toString(),
        HttpHeaderNames.EXPIRES.toString(),
        HttpHeaderNames.DATE.toString(),
        HttpHeaderNames.HOST.toString(),
        HttpHeaderNames.CONNECTION.toString(),
        HttpHeaderNames.USER_AGENT.toString(),
        HttpHeaderNames.CONTENT_LENGTH.toString(),
        HttpHeaderNames.ACCEPT_ENCODING.toString(),
        HttpHeaderNames.TRANSFER_ENCODING.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(),
        HttpHeaderNames.VARY.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(),
        HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(),
        HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(),
        "version",
        "x-cors"
    );
    static NettyHttpClient httpClient = new NettyHttpClient();

    @BeforeClass
    public static void resetServletContext() {
        servletContext = "";
    }

    public abstract int getServerPort();

    public int getServerSecurePort() {
        return getServerPort();
    }

    public abstract int getEchoServerPort();

    @Before
    public void resetServer() {
        mockServerClient.reset();
    }

    protected String calculatePath(String path) {
        return (!path.startsWith("/") ? "/" : "") + path;
    }

    String addContextToPath(String path) {
        String cleanedPath = path;
        if (!Strings.isNullOrEmpty(servletContext)) {
            cleanedPath =
                (!servletContext.startsWith("/") ? "/" : "") +
                    servletContext +
                    (!servletContext.endsWith("/") ? "/" : "") +
                    (cleanedPath.startsWith("/") ? cleanedPath.substring(1) : cleanedPath);
        }
        return (!cleanedPath.startsWith("/") ? "/" : "") + cleanedPath;
    }

    protected void verifyRequestsMatches(HttpRequest[] httpRequests, HttpRequest... httpRequestMatchers) {
        if (httpRequests.length != httpRequestMatchers.length) {
            throw new AssertionError("Number of request matchers does not match number of requests, expected:<" + httpRequestMatchers.length + "> but was:<" + httpRequests.length + ">");
        } else {
            for (int i = 0; i < httpRequestMatchers.length; i++) {
                if (!new HttpRequestMatcher(httpRequestMatchers[i], new MockServerLogger(this.getClass())).matches(null, httpRequests[i])) {
                    throw new AssertionError("Request does not match request matcher, expected:<" + httpRequestMatchers[i] + "> but was:<" + httpRequests[i] + ">");
                }
            }
        }
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest, Collection<String> headersToIgnore) {
        try {
            boolean isSsl = httpRequest.isSecure() != null && httpRequest.isSecure();
            int port = (isSsl ? getServerSecurePort() : getServerPort());
            httpRequest.withPath(addContextToPath(httpRequest.getPath().getValue()));
            httpRequest.withHeader(HOST.toString(), "localhost:" + port);
            boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;
            HttpResponse httpResponse = httpClient.sendRequest(httpRequest, new InetSocketAddress("localhost", port))
                .get(30, (isDebug ? TimeUnit.MINUTES : TimeUnit.SECONDS));
            Headers headers = new Headers();
            for (Header header : httpResponse.getHeaderList()) {
                if (!headersToIgnore.contains(header.getName().getValue().toLowerCase())) {
                    if (header.getName().getValue().equalsIgnoreCase(CONTENT_TYPE.toString())) {
                        // this fixes Tomcat which removes the space between
                        // media type and charset in the Content-Type header
                        for (NottableString value : new ArrayList<NottableString>(header.getValues())) {
                            header.getValues().clear();
                            header.addValues(value.getValue().replace(";charset", "; charset"));
                        }
                        header = header(header.getName().lowercase(), header.getValues());
                    }
                    headers.withEntry(header);
                }
            }
            httpResponse.withHeaders(headers);
            return httpResponse;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
