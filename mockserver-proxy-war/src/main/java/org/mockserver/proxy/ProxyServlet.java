package org.mockserver.proxy;

import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.PortBindingSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.client.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.filters.*;
import org.mockserver.logging.LogFormatter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class ProxyServlet extends HttpServlet {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private Filters filters = new Filters();
    private RequestLogFilter requestLogFilter = new RequestLogFilter();
    private RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();
    private LogFormatter logFormatter = new LogFormatter(logger);
    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();
    // mappers
    private HttpServletRequestToMockServerRequestDecoder httpServletRequestToMockServerRequestDecoder = new HttpServletRequestToMockServerRequestDecoder();
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder = new MockServerResponseToHttpServletResponseEncoder();
    // serializers
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();
    // CORS
    private CORSHeaders addCORSHeaders = new CORSHeaders();

    public ProxyServlet() {
        filters.withFilter(request(), requestLogFilter);
        filters.withFilter(request(), requestResponseLogFilter);
        filters.withFilter(request(), new HopByHopHeaderFilter());
    }

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        HttpRequest request = null;
        try {
            request = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);

            if ((enableCORSForAPI() || enableCORSForAllResponses()) && request.getMethod().getValue().equals("OPTIONS") && !request.getFirstHeader("Origin").isEmpty()) {

                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/status")) {

                httpServletResponse.setStatus(OK_200.code());
                httpServletResponse.setHeader(CONTENT_TYPE.toString(), JSON_UTF_8.toString());
                IOStreamUtils.writeToOutputStream(portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())).getBytes(), httpServletResponse);
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/bind")) {

                httpServletResponse.setStatus(NOT_IMPLEMENTED_501.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/clear")) {

                requestLogFilter.clear(httpRequestSerializer.deserialize(request.getBodyAsString()));
                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/reset")) {

                requestLogFilter.reset();
                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/dumpToLog")) {

                requestResponseLogFilter.dumpToLog(httpRequestSerializer.deserialize(request.getBodyAsString()), request.hasQueryStringParameter("type", "java"));
                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/retrieve")) {

                addCORSHeadersForAPI(httpServletResponse);
                HttpRequest[] requests = requestLogFilter.retrieve(httpRequestSerializer.deserialize(request.getBodyAsString()));
                httpServletResponse.setStatus(OK_200.code());
                httpServletResponse.setHeader(CONTENT_TYPE.toString(), JSON_UTF_8.toString());
                IOStreamUtils.writeToOutputStream(httpRequestSerializer.serialize(requests).getBytes(), httpServletResponse);

            } else if (request.matches("PUT", "/verify")) {

                String result = requestLogFilter.verify(verificationSerializer.deserialize(request.getBodyAsString()));
                addCORSHeadersForAPI(httpServletResponse);
                verifyResponse(httpServletResponse, result);

            } else if (request.matches("PUT", "/verifySequence")) {

                String result = requestLogFilter.verify(verificationSequenceSerializer.deserialize(request.getBodyAsString()));
                addCORSHeadersForAPI(httpServletResponse);
                verifyResponse(httpServletResponse, result);

            } else if (request.matches("PUT", "/stop")) {

                httpServletResponse.setStatus(NOT_IMPLEMENTED_501.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else {
                forwardRequest(request, httpServletResponse);
            }
        } catch (Exception e) {
            logger.error("Exception processing " + (request != null ? request : httpServletRequest), e);
            httpServletResponse.setStatus(BAD_REQUEST_400.code());
        }
    }

    private void verifyResponse(HttpServletResponse httpServletResponse, String result) {
        if (result.isEmpty()) {
            httpServletResponse.setStatus(ACCEPTED_202.code());
        } else {
            httpServletResponse.setStatus(NOT_ACCEPTABLE_406.code());
            httpServletResponse.setHeader(CONTENT_TYPE.toString(), PLAIN_TEXT_UTF_8.toString());
            IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
        }
    }

    private void addCORSHeadersForAPI(HttpServletResponse httpServletResponse) {
        if (enableCORSForAPI()) {
            addCORSHeaders.addCORSHeaders(httpServletResponse);
        } else {
            addCORSHeadersForAllResponses(httpServletResponse);
        }
    }

    private void addCORSHeadersForAllResponses(HttpServletResponse httpServletResponse) {
        if (enableCORSForAllResponses()) {
            addCORSHeaders.addCORSHeaders(httpServletResponse);
        }
    }

    private void forwardRequest(HttpRequest httpRequest, HttpServletResponse httpServletResponse) {
        String hostHeader = httpRequest.getFirstHeader("Host");
        if (!Strings.isNullOrEmpty(hostHeader)) {
            HttpResponse httpResponse = sendRequest(httpRequest, determineRemoteAddress(httpRequest.isSecure(), hostHeader));
            addCORSHeadersForAllResponses(httpServletResponse);
            mockServerResponseToHttpServletResponseEncoder.mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);
        } else {
            logger.error("Host header must be provided for requests being forwarded, the following request does not include the \"Host\" header:" + NEW_LINE + httpRequest);
            throw new IllegalArgumentException("Host header must be provided for requests being forwarded");
        }
    }

    private InetSocketAddress determineRemoteAddress(Boolean isSecure, String hostHeader) {
        String[] hostHeaderParts = hostHeader.split(":");
        boolean isSsl = isSecure != null && isSecure;
        Integer port = (isSsl ? 443 : 80); // default
        if (hostHeaderParts.length > 1) {
            port = Integer.parseInt(hostHeaderParts[1]);  // non-default
        }
        return new InetSocketAddress(hostHeaderParts[0], port);
    }

    private HttpResponse sendRequest(HttpRequest httpRequest, InetSocketAddress remoteAddress) {
        HttpResponse httpResponse = notFoundResponse();
        HttpRequest filteredRequest = filters.applyOnRequestFilters(httpRequest);
        // allow for filter to set response to null
        if (filteredRequest != null) {
            httpResponse = filters.applyOnResponseFilters(httpRequest, httpClient.sendRequest(filteredRequest, remoteAddress));
            if (httpResponse == null) {
                httpResponse = notFoundResponse();
            }
            logFormatter.infoLog(
                    "returning response:{}" + NEW_LINE + " for request as json:{}" + NEW_LINE + " as curl:{}",
                    httpResponse,
                    httpRequest,
                    httpRequestToCurlSerializer.toCurl(httpRequest, remoteAddress)
            );
        }
        return httpResponse;
    }
}
