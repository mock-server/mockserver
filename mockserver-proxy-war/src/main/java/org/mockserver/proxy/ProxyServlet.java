package org.mockserver.proxy;

import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.PortBindingSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.client.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.filters.Filters;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.logging.LogFormatter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class ProxyServlet extends HttpServlet {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final LogFormatter logFormatter = new LogFormatter(logger);
    // mockserver
    private RequestLogFilter requestLogFilter = new RequestLogFilter();
    private Filters filters = new Filters();
    private NettyHttpClient httpClient = new NettyHttpClient();
    private HttpStateHandler httpStateHandler;
    // mappers
    private HttpServletRequestToMockServerRequestDecoder httpServletRequestToMockServerRequestDecoder = new HttpServletRequestToMockServerRequestDecoder();
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder = new MockServerResponseToHttpServletResponseEncoder();
    // serializers
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer();
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();
    // CORS
    private CORSHeaders addCORSHeaders = new CORSHeaders();

    public ProxyServlet() {
        filters.withFilter(request(), requestLogFilter);
        RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();
        filters.withFilter(request(), requestResponseLogFilter);
        filters.withFilter(request(), new HopByHopHeaderFilter());
        httpStateHandler = new HttpStateHandler(requestLogFilter, requestResponseLogFilter, null);
    }

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        HttpRequest request = null;
        try {

            request = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);
            logFormatter.traceLog("received request:{}" + NEW_LINE, request);

            if ((enableCORSForAPI() || enableCORSForAllResponses()) && request.getMethod().getValue().equals("OPTIONS") && !request.getFirstHeader("Origin").isEmpty()) {

                writeResponse(httpServletResponse, OK_200);

            } else if (request.matches("PUT", "/status")) {

                writeResponse(httpServletResponse, OK_200, portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())), "application/json");

            } else if (request.matches("PUT", "/bind")) {

                writeResponse(httpServletResponse, NOT_IMPLEMENTED_501);

            } else if (request.matches("PUT", "/clear")) {

                httpStateHandler.clear(request);
                writeResponse(httpServletResponse, OK_200);

            } else if (request.matches("PUT", "/reset")) {

                httpStateHandler.reset();
                writeResponse(httpServletResponse, OK_200);

            } else if (request.matches("PUT", "/dumpToLog")) {

                httpStateHandler.dumpRecordedRequestResponsesToLog(request);
                writeResponse(httpServletResponse, OK_200);

            } else if (request.matches("PUT", "/retrieve")) {

                writeResponse(httpServletResponse, OK_200, httpStateHandler.retrieve(request),
                        JSON_UTF_8.toString().replace(request.hasQueryStringParameter("format", "java") ? "json" : "", "java")
                );

            } else if (request.matches("PUT", "/verify")) {

                Verification verification = verificationSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verification);
                verifyResponse(httpServletResponse, result);
                logFormatter.infoLog("verifying requests that match:{}", verification);

            } else if (request.matches("PUT", "/verifySequence")) {

                VerificationSequence verificationSequence = verificationSequenceSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verificationSequence);
                verifyResponse(httpServletResponse, result);
                logFormatter.infoLog("verifying sequence that match:{}", verificationSequence);

            } else if (request.matches("PUT", "/stop")) {

                writeResponse(httpServletResponse, NOT_IMPLEMENTED_501);

            } else {

                String hostHeader = request.getFirstHeader("Host");
                if (!Strings.isNullOrEmpty(hostHeader)) {
                    writeResponse(httpServletResponse, sendRequest(request, determineRemoteAddress(request.isSecure(), hostHeader)));
                } else {
                    logger.error("Host header must be provided for requests being forwarded, the following request does not include the \"Host\" header:" + NEW_LINE + request);
                    throw new IllegalArgumentException("Host header must be provided for requests being forwarded");
                }

            }
        } catch (IllegalArgumentException iae) {
            logger.error("Exception processing " + request, iae);
            // send request without API CORS headers
            writeResponse(httpServletResponse, BAD_REQUEST_400, iae.getMessage(), MediaType.create("text", "plain").toString());
        } catch (Exception e) {
            logger.error("Exception processing " + request, e);
            writeResponse(httpServletResponse, response().withStatusCode(BAD_REQUEST_400.code()).withBody(e.getMessage()));
        }
    }

    private void verifyResponse(HttpServletResponse httpServletResponse, String result) {
        if (result.isEmpty()) {
            writeResponse(httpServletResponse, ACCEPTED_202);
        } else {
            writeResponse(httpServletResponse, NOT_ACCEPTABLE_406, result, MediaType.create("text", "plain").toString());
        }
    }

    private void writeResponse(HttpServletResponse httpServletResponse, HttpStatusCode responseStatus) {
        writeResponse(httpServletResponse, responseStatus, "", "application/json");
    }

    private void writeResponse(HttpServletResponse httpServletResponse, HttpStatusCode responseStatus, String body, String contentType) {
        HttpResponse response = response()
                .withStatusCode(responseStatus.code())
                .withBody(body);
        if (body != null && !body.isEmpty()) {
            response.updateHeader(header(CONTENT_TYPE.toString(), contentType + "; charset=utf-8"));
        }
        if (enableCORSForAPI()) {
            addCORSHeaders.addCORSHeaders(response);
        }
        writeResponse(httpServletResponse, response);
    }

    private void writeResponse(HttpServletResponse httpServletResponse, HttpResponse response) {
        if (enableCORSForAllResponses()) {
            addCORSHeaders.addCORSHeaders(response);
        }

        mockServerResponseToHttpServletResponseEncoder.mapMockServerResponseToHttpServletResponse(response, httpServletResponse);
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

    private InetSocketAddress determineRemoteAddress(Boolean isSecure, String hostHeader) {
        String[] hostHeaderParts = hostHeader.split(":");
        boolean isSsl = isSecure != null && isSecure;
        Integer port = (isSsl ? 443 : 80); // default
        if (hostHeaderParts.length > 1) {
            port = Integer.parseInt(hostHeaderParts[1]);  // non-default
        }
        return new InetSocketAddress(hostHeaderParts[0], port);
    }
}
