package org.mockserver.proxy;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.*;
import org.mockserver.client.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.client.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.client.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.filters.Filters;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.logging.LogFormatter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.streams.IOStreamUtils;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;
import java.util.List;

import static com.google.common.base.Charsets.UTF_8;
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
    private final LogFormatter logFormatter = new LogFormatter(logger);
    // mockserver
    private RequestLogFilter requestLogFilter = new RequestLogFilter();
    private RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();
    private Filters filters = new Filters();
    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();
    // mappers
    private HttpServletRequestToMockServerRequestDecoder httpServletRequestToMockServerRequestDecoder = new HttpServletRequestToMockServerRequestDecoder();
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder = new MockServerResponseToHttpServletResponseEncoder();
    // serializers
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();
    private HttpRequestToJavaSerializer httpRequestToJavaSerializer = new HttpRequestToJavaSerializer();
    private ExpectationToJavaSerializer expectationToJavaSerializer = new ExpectationToJavaSerializer();
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
                IOStreamUtils.writeToOutputStream(portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())).getBytes(UTF_8), httpServletResponse);
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/bind")) {

                httpServletResponse.setStatus(NOT_IMPLEMENTED_501.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/clear")) {

                HttpRequest httpRequest = null;
                if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
                    httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
                }
                requestResponseLogFilter.clear(httpRequest);
                requestLogFilter.clear(httpRequest);
                logFormatter.infoLog("clearing expectations and request logs that match:{}", httpRequest);
                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/reset")) {

                requestResponseLogFilter.reset();
                requestLogFilter.reset();
                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);
                logFormatter.infoLog("resetting all expectations and request logs");

            } else if (request.matches("PUT", "/dumpToLog")) {

                HttpRequest httpRequest = null;
                if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
                    httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
                }
                boolean asJava = request.hasQueryStringParameter("type", "java") || request.hasQueryStringParameter("format", "java");
                requestResponseLogFilter.dumpToLog(httpRequest, asJava);
                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);
                logFormatter.infoLog("dumped all active expectations to the log in " + (asJava ? "java" : "json") + " that match:{}", httpRequest);

            } else if (request.matches("PUT", "/retrieve")) {

                HttpRequest httpRequest = null;
                if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
                    httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
                }
                StringBuilder responseBody = new StringBuilder();
                boolean asJava = request.hasQueryStringParameter("format", "java");
                boolean asExpectations = request.hasQueryStringParameter("type", "expectation");
                if (asExpectations) {
                    List<Expectation> expectations = requestResponseLogFilter.retrieveExpectations(httpRequest);
                    if (asJava) {
                        responseBody.append(expectationToJavaSerializer.serializeAsJava(0, expectations));
                    } else {
                        responseBody.append(expectationSerializer.serialize(expectations));
                    }
                } else {
                    HttpRequest[] httpRequests = requestLogFilter.retrieve(httpRequest);
                    if (asJava) {
                        responseBody.append(httpRequestToJavaSerializer.serializeAsJava(0, httpRequests));
                    } else {
                        responseBody.append(httpRequestSerializer.serialize(httpRequests));
                    }
                }
                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);
                httpServletResponse.setHeader(CONTENT_TYPE.toString(), JSON_UTF_8.toString().replace(asJava ? "json" : "", "java"));
                IOStreamUtils.writeToOutputStream(responseBody.toString().getBytes(UTF_8), httpServletResponse);
                logFormatter.infoLog("retrieving " + (asExpectations ? "expectations" : "requests") + " that match:{}", httpRequest);

            } else if (request.matches("PUT", "/verify")) {

                Verification verification = verificationSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verification);
                verifyResponse(httpServletResponse, result);
                addCORSHeadersForAPI(httpServletResponse);
                logFormatter.infoLog("verifying requests that match:{}", verification);

            } else if (request.matches("PUT", "/verifySequence")) {

                VerificationSequence verificationSequence = verificationSequenceSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verificationSequence);
                verifyResponse(httpServletResponse, result);
                addCORSHeadersForAPI(httpServletResponse);
                logFormatter.infoLog("verifying sequence that match:{}", verificationSequence);

            } else if (request.matches("PUT", "/stop")) {

                httpServletResponse.setStatus(NOT_IMPLEMENTED_501.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else {
                forwardRequest(request, httpServletResponse);
            }
        } catch (IllegalArgumentException iae) {
            httpServletResponse.setStatus(BAD_REQUEST_400.code());
            httpServletResponse.setHeader(CONTENT_TYPE.toString(), PLAIN_TEXT_UTF_8.toString());
            IOStreamUtils.writeToOutputStream(iae.getMessage().getBytes(Charsets.UTF_8), httpServletResponse);
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
            IOStreamUtils.writeToOutputStream(result.getBytes(UTF_8), httpServletResponse);
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
