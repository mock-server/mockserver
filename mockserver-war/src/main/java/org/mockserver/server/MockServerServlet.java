package org.mockserver.server;

import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.PortBindingSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.logging.LogFormatter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.*;
import org.mockserver.socket.KeyAndCertificateFactory;
import org.mockserver.streams.IOStreamUtils;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {

    private static final String NOT_SUPPORTED_MESSAGE = " is not supported by MockServer deployable WAR due to limitations in the JEE specification; use mockserver-netty to enable these features";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final LogFormatter logFormatter = new LogFormatter(logger);
    // mockserver
    private MockServerMatcher mockServerMatcher = new MockServerMatcher();
    private RequestLogFilter requestLogFilter = new RequestLogFilter();
    private ActionHandler actionHandler = new ActionHandler(requestLogFilter);
    private HttpStateHandler httpStateHandler;
    // mappers
    private HttpServletRequestToMockServerRequestDecoder httpServletRequestToMockServerRequestDecoder = new HttpServletRequestToMockServerRequestDecoder();
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder = new MockServerResponseToHttpServletResponseEncoder();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();
    // CORS
    private CORSHeaders addCORSHeaders = new CORSHeaders();

    public MockServerServlet() {
        httpStateHandler = new HttpStateHandler(requestLogFilter, null, mockServerMatcher);
    }

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        HttpRequest request = null;
        try {

            request = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);
            logFormatter.traceLog("received request:{}" + NEW_LINE, request);

            if ((enableCORSForAPI() || enableCORSForAllResponses()) && request.getMethod().getValue().equals("OPTIONS") && !request.getFirstHeader("Origin").isEmpty()) {

                writeResponse(httpServletResponse, OK_200);

            } else if (request.getPath().getValue().equals("/_mockserver_callback_websocket")) {

                writeNotSupportedResponse(ExpectationCallback.class, httpServletResponse);

            } else if (request.matches("PUT", "/status")) {

                writeResponse(httpServletResponse, OK_200, portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())), "application/json");

            } else if (request.matches("PUT", "/bind")) {

                writeResponse(httpServletResponse, NOT_IMPLEMENTED_501);

            } else if (request.matches("PUT", "/expectation")) {

                for (Expectation expectation : expectationSerializer.deserializeArray(request.getBodyAsString())) {
                    Action action = expectation.getAction();
                    if (validateSupportedFeatures(action, httpServletResponse)) {
                        KeyAndCertificateFactory.addSubjectAlternativeName(expectation.getHttpRequest().getFirstHeader(HOST.toString()));
                        mockServerMatcher
                                .when(expectation.getHttpRequest(), expectation.getTimes(), expectation.getTimeToLive())
                                .thenRespond(expectation.getHttpResponse())
                                .thenRespond(expectation.getHttpResponseTemplate())
                                .thenForward(expectation.getHttpForward())
                                .thenCallback(expectation.getHttpClassCallback());
                        logFormatter.infoLog("creating expectation:{}", expectation);
                    }
                }
                writeResponse(httpServletResponse, CREATED_201);

            } else if (request.matches("PUT", "/clear")) {

                httpStateHandler.clear(request);
                writeResponse(httpServletResponse, OK_200);

            } else if (request.matches("PUT", "/reset")) {

                httpStateHandler.reset();
                writeResponse(httpServletResponse, OK_200);

            } else if (request.matches("PUT", "/dumpToLog")) {

                httpStateHandler.dumpExpectationsToLog(request);
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

                Action action = mockServerMatcher.retrieveAction(request);
                if (validateSupportedFeatures(action, httpServletResponse)) {
                    HttpResponse response = actionHandler.processAction(action, request);
                    writeResponse(httpServletResponse, response);
                    logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}", response, request);
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
        if (response == null) {
            response = notFoundResponse();
        }
        if (enableCORSForAllResponses()) {
            addCORSHeaders.addCORSHeaders(response);
        }

        addContentTypeHeader(response);

        mockServerResponseToHttpServletResponseEncoder.mapMockServerResponseToHttpServletResponse(response, httpServletResponse);
    }

    private void addContentTypeHeader(HttpResponse response) {
        if (response.getBody() != null && Strings.isNullOrEmpty(response.getFirstHeader(CONTENT_TYPE.toString()))) {
            Charset bodyCharset = response.getBody().getCharset(null);
            String bodyContentType = response.getBody().getContentType();
            if (bodyCharset != null) {
                response.updateHeader(header(CONTENT_TYPE.toString(), bodyContentType + "; charset=" + bodyCharset.name().toLowerCase()));
            } else if (bodyContentType != null) {
                response.updateHeader(header(CONTENT_TYPE.toString(), bodyContentType));
            }
        }
    }

    private boolean validateSupportedFeatures(Action action, HttpServletResponse httpServletResponse) {
        boolean valid = true;
        if (action instanceof HttpResponse && ((HttpResponse) action).getConnectionOptions() != null) {
            writeNotSupportedResponse(ConnectionOptions.class, httpServletResponse);
            valid = false;
        } else if (action instanceof HttpObjectCallback) {
            writeNotSupportedResponse(HttpObjectCallback.class, httpServletResponse);
            valid = false;
        } else if (action instanceof HttpError) {
            writeNotSupportedResponse(HttpError.class, httpServletResponse);
            valid = false;
        }
        return valid;
    }

    private void writeNotSupportedResponse(Class<?> notSupportedFeature, HttpServletResponse httpServletResponse) {
        httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
        IOStreamUtils.writeToOutputStream((notSupportedFeature.getSimpleName() + NOT_SUPPORTED_MESSAGE).getBytes(UTF_8), httpServletResponse);
    }
}
