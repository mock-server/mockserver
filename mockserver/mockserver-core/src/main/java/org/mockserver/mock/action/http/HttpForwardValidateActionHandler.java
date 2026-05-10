package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpForwardValidateAction;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.openapi.OpenAPIRequestValidator;
import org.mockserver.openapi.OpenAPIResponseValidator;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Function;

import static org.mockserver.log.model.LogEntry.LogMessageType.OPENAPI_RESPONSE_VALIDATION_FAILED;
import static org.mockserver.model.HttpResponse.response;

public class HttpForwardValidateActionHandler extends HttpForwardAction {

    public HttpForwardValidateActionHandler(MockServerLogger mockServerLogger, Configuration configuration, NettyHttpClient httpClient) {
        super(mockServerLogger, configuration, httpClient);
    }

    public HttpForwardActionResult handle(final HttpForwardValidateAction action, final HttpRequest request) {
        if (action != null && action.getSpecUrlOrPayload() != null) {
            boolean strict = action.getValidationMode() == HttpForwardValidateAction.ValidationMode.STRICT;

            if (Boolean.TRUE.equals(action.getValidateRequest())) {
                List<String> requestErrors = OpenAPIRequestValidator.validate(action.getSpecUrlOrPayload(), request, mockServerLogger);
                if (!requestErrors.isEmpty()) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(OPENAPI_RESPONSE_VALIDATION_FAILED)
                            .setLogLevel(Level.WARN)
                            .setHttpRequest(request)
                            .setMessageFormat("OpenAPI request validation failed for request{}errors:{}")
                            .setArguments(request, String.join("; ", requestErrors))
                    );
                    if (strict) {
                        return rejectRequest(request, 400, "OpenAPI request validation failed: " + String.join("; ", requestErrors));
                    }
                }
            }

            Function<HttpResponse, HttpResponse> responseValidator = httpResponse -> {
                if (Boolean.TRUE.equals(action.getValidateResponse()) && httpResponse != null) {
                    List<String> responseErrors = OpenAPIResponseValidator.validate(
                        action.getSpecUrlOrPayload(),
                        findOperationId(action, request),
                        httpResponse,
                        mockServerLogger
                    );
                    if (!responseErrors.isEmpty()) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(OPENAPI_RESPONSE_VALIDATION_FAILED)
                                .setLogLevel(Level.WARN)
                                .setHttpRequest(request)
                                .setHttpResponse(httpResponse)
                                .setMessageFormat("OpenAPI response validation failed for request{}response{}errors:{}")
                                .setArguments(request, httpResponse, String.join("; ", responseErrors))
                        );
                        if (strict) {
                            return response()
                                .withStatusCode(502)
                                .withBody("OpenAPI response validation failed: " + String.join("; ", responseErrors));
                        }
                    }
                }
                return httpResponse;
            };

            HttpRequest requestToSend = request.clone();
            if (action.getHost() != null) {
                InetSocketAddress remoteAddress = new InetSocketAddress(action.getHost(), action.getPort() != null ? action.getPort() : 80);
                requestToSend
                    .withSocketAddress(action.getHost(), action.getPort() != null ? action.getPort() : 80,
                        action.getScheme() == org.mockserver.model.HttpForward.Scheme.HTTPS
                            ? org.mockserver.model.SocketAddress.Scheme.HTTPS
                            : org.mockserver.model.SocketAddress.Scheme.HTTP);
                adjustHostHeader(requestToSend);

                return sendRequest(requestToSend, remoteAddress, responseValidator);
            } else {
                return sendRequest(requestToSend, null, responseValidator);
            }
        }
        return sendRequest(request, null, httpResponse -> httpResponse);
    }

    private String findOperationId(HttpForwardValidateAction action, HttpRequest request) {
        try {
            String requestPath = request.getPath() != null ? request.getPath().getValue() : "/";
            String requestMethod = request.getMethod() != null ? request.getMethod().getValue().toLowerCase() : "get";
            io.swagger.v3.oas.models.OpenAPI openAPI = org.mockserver.openapi.OpenAPIParser.buildOpenAPI(action.getSpecUrlOrPayload(), mockServerLogger);
            return openAPI
                .getPaths()
                .entrySet()
                .stream()
                .filter(entry -> {
                    StringBuilder regex = new StringBuilder();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{[^}]+}").matcher(entry.getKey());
                    int lastEnd = 0;
                    while (m.find()) {
                        regex.append(java.util.regex.Pattern.quote(entry.getKey().substring(lastEnd, m.start())));
                        regex.append("[^/]+");
                        lastEnd = m.end();
                    }
                    regex.append(java.util.regex.Pattern.quote(entry.getKey().substring(lastEnd)));
                    return requestPath.matches(regex.toString());
                })
                .flatMap(entry -> org.mockserver.openapi.OpenAPIParser.mapOperations(entry.getValue()).stream())
                .filter(pair -> pair.getLeft().equalsIgnoreCase(requestMethod))
                .map(pair -> pair.getRight().getOperationId())
                .findFirst()
                .orElse("unknown");
        } catch (Throwable throwable) {
            return "unknown";
        }
    }

    private HttpForwardActionResult rejectRequest(HttpRequest request, int statusCode, String message) {
        java.util.concurrent.CompletableFuture<HttpResponse> future = new java.util.concurrent.CompletableFuture<>();
        future.complete(response().withStatusCode(statusCode).withBody(message));
        return new HttpForwardActionResult(request, future, null);
    }
}
