package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.RequestDefinitionSerializer;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class McpToolRegistry {

    private final HttpState httpState;
    private final LifeCycle server;
    private final ObjectMapper objectMapper;
    private final MockServerLogger mockServerLogger;
    private final Map<String, ToolDefinition> tools;
    private ExpectationSerializer expectationSerializer;
    private RequestDefinitionSerializer requestDefinitionSerializer;

    public McpToolRegistry(HttpState httpState, LifeCycle server) {
        this.httpState = httpState;
        this.server = server;
        this.objectMapper = ObjectMapperFactory.createObjectMapper();
        this.mockServerLogger = httpState.getMockServerLogger();
        this.tools = new LinkedHashMap<>();
        registerAllTools();
    }

    private ExpectationSerializer getExpectationSerializer() {
        if (expectationSerializer == null) {
            expectationSerializer = new ExpectationSerializer(mockServerLogger);
        }
        return expectationSerializer;
    }

    private RequestDefinitionSerializer getRequestDefinitionSerializer() {
        if (requestDefinitionSerializer == null) {
            requestDefinitionSerializer = new RequestDefinitionSerializer(mockServerLogger);
        }
        return requestDefinitionSerializer;
    }

    public Map<String, ToolDefinition> getTools() {
        return tools;
    }

    public JsonNode callTool(String name, JsonNode params) {
        ToolDefinition tool = tools.get(name);
        if (tool == null) {
            return null;
        }
        return tool.handler.apply(params != null ? params : objectMapper.createObjectNode());
    }

    private void registerAllTools() {
        registerCreateExpectation();
        registerVerifyRequest();
        registerRetrieveRecordedRequests();
        registerClearExpectations();
        registerReset();
        registerGetStatus();
        registerVerifyRequestSequence();
        registerRetrieveRequestResponses();
        registerCreateForwardExpectation();
        registerDebugRequestMismatch();
        registerCreateExpectationFromOpenApi();
        registerStopServer();
        registerRawExpectation();
        registerRawRetrieve();
        registerRawVerify();
    }

    private void registerCreateExpectation() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("method").put("type", "string").put("description", "HTTP method (GET, POST, PUT, DELETE, etc.)");
        properties.putObject("path").put("type", "string").put("description", "Request path to match");
        properties.putObject("statusCode").put("type", "integer").put("description", "Response status code (default 200)");
        ObjectNode responseBodyProp = properties.putObject("responseBody");
        responseBodyProp.put("description", "Response body as string or object");
        ArrayNode anyOf = responseBodyProp.putArray("anyOf");
        anyOf.add(objectMapper.createObjectNode().put("type", "string"));
        anyOf.add(objectMapper.createObjectNode().put("type", "object"));
        properties.putObject("responseHeaders").put("type", "object").put("description", "Response headers as key-value pairs");
        properties.putObject("times").put("type", "integer").put("description", "Number of times this expectation should match");
        properties.putObject("timeToLive").put("type", "string").put("description", "Time to live e.g. '60 SECONDS'");
        ArrayNode required = schema.putArray("required");
        required.add("method");
        required.add("path");

        tools.put("create_expectation", new ToolDefinition(
            "create_expectation",
            "Creates a mock expectation that defines how MockServer should respond to a matching request",
            schema,
            this::handleCreateExpectation
        ));
    }

    private JsonNode handleCreateExpectation(JsonNode params) {
        try {
            String method = params.path("method").asText(null);
            if (method == null || method.trim().isEmpty()) {
                return errorResult("'method' is required and must not be blank");
            }
            String path = params.path("path").asText(null);
            if (path == null || path.trim().isEmpty()) {
                return errorResult("'path' is required and must not be blank");
            }

            JsonNode statusCodeNode = params.path("statusCode");
            int statusCode = 200;
            if (!statusCodeNode.isMissingNode() && !statusCodeNode.isNull()) {
                if (!statusCodeNode.isIntegralNumber()) {
                    return errorResult("'statusCode' must be an integer");
                }
                statusCode = statusCodeNode.asInt();
                if (statusCode < 100 || statusCode > 999) {
                    return errorResult("'statusCode' must be between 100 and 999");
                }
            }

            HttpRequest httpRequest = request().withMethod(method).withPath(path);
            HttpResponse httpResponse = response().withStatusCode(statusCode);

            JsonNode responseBody = params.path("responseBody");
            if (!responseBody.isMissingNode() && !responseBody.isNull()) {
                if (responseBody.isTextual()) {
                    httpResponse.withBody(responseBody.asText());
                } else {
                    httpResponse.withBody(objectMapper.writeValueAsString(responseBody), MediaType.APPLICATION_JSON);
                }
            }

            JsonNode responseHeaders = params.path("responseHeaders");
            if (responseHeaders.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = responseHeaders.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    httpResponse.withHeader(entry.getKey(), entry.getValue().asText());
                }
            }

            Expectation expectation = Expectation.when(httpRequest).thenRespond(httpResponse);

            JsonNode timesNode = params.path("times");
            if (!timesNode.isMissingNode() && !timesNode.isNull()) {
                if (!timesNode.isIntegralNumber()) {
                    return errorResult("'times' must be an integer");
                }
                int timesValue = timesNode.asInt();
                if (timesValue < 0) {
                    return errorResult("'times' must be a non-negative integer");
                }
                expectation = Expectation.when(httpRequest, Times.exactly(timesValue), TimeToLive.unlimited()).thenRespond(httpResponse);
            }

            JsonNode ttlNode = params.path("timeToLive");
            if (!ttlNode.isMissingNode() && !ttlNode.isNull() && !ttlNode.isTextual()) {
                return errorResult("'timeToLive' must be a string in format '<number> <UNIT>' (e.g., '60 SECONDS')");
            }
            if (!ttlNode.isMissingNode() && !ttlNode.isNull() && ttlNode.isTextual()) {
                String ttlStr = ttlNode.asText().trim();
                String[] parts = ttlStr.split("\\s+", 2);
                if (parts.length != 2) {
                    return errorResult("'timeToLive' must be in format '<number> <UNIT>' (e.g., '60 SECONDS')");
                }
                long ttlValue;
                try {
                    ttlValue = Long.parseLong(parts[0]);
                } catch (NumberFormatException e) {
                    return errorResult("'timeToLive' value must be a number");
                }
                if (ttlValue <= 0) {
                    return errorResult("'timeToLive' value must be positive");
                }
                TimeUnit timeUnit;
                try {
                    timeUnit = TimeUnit.valueOf(parts[1].toUpperCase());
                } catch (IllegalArgumentException e) {
                    return errorResult("'timeToLive' unit must be one of: DAYS, HOURS, MINUTES, SECONDS, MILLISECONDS, MICROSECONDS, NANOSECONDS");
                }
                Times effectiveTimes = (timesNode.isMissingNode() || timesNode.isNull()) ? Times.unlimited() : Times.exactly(timesNode.asInt());
                expectation = Expectation.when(httpRequest, effectiveTimes, TimeToLive.exactly(timeUnit, ttlValue)).thenRespond(httpResponse);
            }

            List<Expectation> result = httpState.add(expectation);

            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("status", "created");
            resultNode.put("count", result.size());
            if (!result.isEmpty()) {
                resultNode.put("id", result.get(0).getId());
            }
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to create expectation", e);
        }
    }

    private void registerVerifyRequest() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("method").put("type", "string").put("description", "HTTP method to verify");
        properties.putObject("path").put("type", "string").put("description", "Request path to verify");
        properties.putObject("atLeast").put("type", "integer").put("description", "Minimum number of matching requests expected");
        properties.putObject("atMost").put("type", "integer").put("description", "Maximum number of matching requests expected");

        tools.put("verify_request", new ToolDefinition(
            "verify_request",
            "Verifies that a request matching the specified criteria was received a certain number of times",
            schema,
            this::handleVerifyRequest
        ));
    }

    private JsonNode handleVerifyRequest(JsonNode params) {
        try {
            HttpRequest httpRequest = request();
            JsonNode methodNode = params.path("method");
            if (!methodNode.isMissingNode() && !methodNode.isNull()) {
                httpRequest.withMethod(methodNode.asText());
            }
            JsonNode pathNode = params.path("path");
            if (!pathNode.isMissingNode() && !pathNode.isNull()) {
                httpRequest.withPath(pathNode.asText());
            }

            JsonNode atLeastNode = params.path("atLeast");
            if (!atLeastNode.isMissingNode() && !atLeastNode.isNull() && !atLeastNode.isIntegralNumber()) {
                return errorResult("'atLeast' must be an integer");
            }
            JsonNode atMostNode = params.path("atMost");
            if (!atMostNode.isMissingNode() && !atMostNode.isNull() && !atMostNode.isIntegralNumber()) {
                return errorResult("'atMost' must be an integer");
            }
            int atLeast = atLeastNode.asInt(1);
            int atMost = atMostNode.asInt(-1);
            if (atLeast < 0) {
                return errorResult("'atLeast' must be non-negative");
            }
            if (atMost != -1 && atMost < atLeast) {
                return errorResult("'atMost' must be >= 'atLeast'");
            }

            VerificationTimes times;
            if (atMost == -1) {
                times = VerificationTimes.atLeast(atLeast);
            } else {
                times = VerificationTimes.between(atLeast, atMost);
            }

            Verification verification = new Verification()
                .withRequest(httpRequest)
                .withTimes(times);

            Future<String> result = httpState.verify(verification);
            String verificationResult = result.get(10, TimeUnit.SECONDS);

            ObjectNode resultNode = objectMapper.createObjectNode();
            if (verificationResult == null || verificationResult.isEmpty()) {
                resultNode.put("verified", true);
                resultNode.put("message", "Verification passed");
            } else {
                resultNode.put("verified", false);
                resultNode.put("message", verificationResult);
            }
            return resultNode;
        } catch (Exception e) {
            return errorResult("Verification failed", e);
        }
    }

    private void registerRetrieveRecordedRequests() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("method").put("type", "string").put("description", "Filter by HTTP method");
        properties.putObject("path").put("type", "string").put("description", "Filter by request path");
        properties.putObject("limit").put("type", "integer").put("description", "Maximum number of requests to return (default 50)");

        tools.put("retrieve_recorded_requests", new ToolDefinition(
            "retrieve_recorded_requests",
            "Retrieves recorded requests that were received by MockServer, optionally filtered by method and path",
            schema,
            this::handleRetrieveRecordedRequests
        ));
    }

    private JsonNode handleRetrieveRecordedRequests(JsonNode params) {
        try {
            HttpRequest filterRequest = request();
            JsonNode methodNode = params.path("method");
            if (!methodNode.isMissingNode() && !methodNode.isNull()) {
                filterRequest.withMethod(methodNode.asText());
            }
            JsonNode pathNode = params.path("path");
            if (!pathNode.isMissingNode() && !pathNode.isNull()) {
                filterRequest.withPath(pathNode.asText());
            }
            int limit = params.path("limit").asInt(50);
            if (limit < 1 || limit > 500) {
                return errorResult("'limit' must be between 1 and 500");
            }

            HttpRequest retrieveRequest = request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", "REQUESTS")
                .withQueryStringParameter("format", "JSON")
                .withBody(getRequestDefinitionSerializer().serialize(filterRequest));

            HttpResponse retrieveResponse = httpState.retrieve(retrieveRequest);
            String body = retrieveResponse.getBodyAsString();

            if (body != null && !body.isEmpty()) {
                JsonNode allRequests = objectMapper.readTree(body);
                if (allRequests.isArray() && allRequests.size() > limit) {
                    ArrayNode limited = objectMapper.createArrayNode();
                    for (int i = allRequests.size() - limit; i < allRequests.size(); i++) {
                        limited.add(allRequests.get(i));
                    }
                    ObjectNode resultNode = objectMapper.createObjectNode();
                    resultNode.set("requests", limited);
                    resultNode.put("total", allRequests.size());
                    resultNode.put("returned", limited.size());
                    return resultNode;
                } else {
                    ObjectNode resultNode = objectMapper.createObjectNode();
                    resultNode.set("requests", allRequests);
                    resultNode.put("total", allRequests.isArray() ? allRequests.size() : 0);
                    resultNode.put("returned", allRequests.isArray() ? allRequests.size() : 0);
                    return resultNode;
                }
            }

            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.set("requests", objectMapper.createArrayNode());
            resultNode.put("total", 0);
            resultNode.put("returned", 0);
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to retrieve requests", e);
        }
    }

    private void registerClearExpectations() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("method").put("type", "string").put("description", "Filter by HTTP method");
        properties.putObject("path").put("type", "string").put("description", "Filter by request path");
        ObjectNode typeProp = properties.putObject("type");
        typeProp.put("type", "string").put("description", "What to clear: ALL, LOG, or EXPECTATIONS");
        ArrayNode typeEnum = typeProp.putArray("enum");
        typeEnum.add("ALL");
        typeEnum.add("LOG");
        typeEnum.add("EXPECTATIONS");

        tools.put("clear_expectations", new ToolDefinition(
            "clear_expectations",
            "Clears expectations and/or recorded requests from MockServer matching the specified criteria",
            schema,
            this::handleClearExpectations
        ));
    }

    private JsonNode handleClearExpectations(JsonNode params) {
        try {
            HttpRequest clearRequest = request()
                .withMethod("PUT")
                .withPath("/mockserver/clear");

            String type = params.path("type").asText("ALL");
            if (!"ALL".equals(type) && !"LOG".equals(type) && !"EXPECTATIONS".equals(type)) {
                return errorResult("'type' must be one of: ALL, LOG, EXPECTATIONS");
            }
            clearRequest.withQueryStringParameter("type", type);

            JsonNode methodNode = params.path("method");
            JsonNode pathNode = params.path("path");
            boolean hasFilter = (!methodNode.isMissingNode() && !methodNode.isNull()) ||
                (!pathNode.isMissingNode() && !pathNode.isNull());

            if (hasFilter) {
                HttpRequest filterRequest = request();
                if (!methodNode.isMissingNode() && !methodNode.isNull()) {
                    filterRequest.withMethod(methodNode.asText());
                }
                if (!pathNode.isMissingNode() && !pathNode.isNull()) {
                    filterRequest.withPath(pathNode.asText());
                }
                clearRequest.withBody(getRequestDefinitionSerializer().serialize(filterRequest));
            }

            httpState.clear(clearRequest);

            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("status", "cleared");
            resultNode.put("type", type);
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to clear", e);
        }
    }

    private void registerReset() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");

        tools.put("reset", new ToolDefinition(
            "reset",
            "Resets MockServer by clearing all expectations, recorded requests, and logs",
            schema,
            this::handleReset
        ));
    }

    private JsonNode handleReset(JsonNode params) {
        try {
            httpState.reset();
            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("status", "reset");
            resultNode.put("message", "MockServer has been reset");
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to reset", e);
        }
    }

    private void registerGetStatus() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");

        tools.put("get_status", new ToolDefinition(
            "get_status",
            "Returns the current status of MockServer including listening ports and running state",
            schema,
            this::handleGetStatus
        ));
    }

    private JsonNode handleGetStatus(JsonNode params) {
        try {
            ObjectNode resultNode = objectMapper.createObjectNode();
            List<Integer> ports = server.getLocalPorts();
            ArrayNode portsArray = resultNode.putArray("ports");
            for (Integer port : ports) {
                portsArray.add(port);
            }
            resultNode.put("running", server.isRunning());
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to get status", e);
        }
    }

    private void registerVerifyRequestSequence() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode requestsProp = properties.putObject("requests");
        requestsProp.put("type", "array").put("description", "Ordered list of requests to verify in sequence");
        ObjectNode requestItems = requestsProp.putObject("items");
        requestItems.put("type", "object");
        ObjectNode itemProps = requestItems.putObject("properties");
        itemProps.putObject("method").put("type", "string");
        itemProps.putObject("path").put("type", "string");
        schema.putArray("required").add("requests");

        tools.put("verify_request_sequence", new ToolDefinition(
            "verify_request_sequence",
            "Verifies that requests were received in the specified order",
            schema,
            this::handleVerifyRequestSequence
        ));
    }

    private JsonNode handleVerifyRequestSequence(JsonNode params) {
        try {
            JsonNode requestsNode = params.path("requests");
            if (!requestsNode.isArray() || requestsNode.size() == 0) {
                return errorResult("'requests' must be a non-empty array");
            }

            VerificationSequence sequence = new VerificationSequence();
            List<org.mockserver.model.RequestDefinition> requests = new ArrayList<>();
            for (JsonNode reqNode : requestsNode) {
                if (!reqNode.isObject()) {
                    return errorResult("Each element of 'requests' must be an object");
                }
                HttpRequest httpRequest = request();
                JsonNode methodNode = reqNode.path("method");
                if (!methodNode.isMissingNode() && !methodNode.isNull()) {
                    httpRequest.withMethod(methodNode.asText());
                }
                JsonNode pathNode = reqNode.path("path");
                if (!pathNode.isMissingNode() && !pathNode.isNull()) {
                    httpRequest.withPath(pathNode.asText());
                }
                requests.add(httpRequest);
            }
            sequence.withRequests(requests);

            Future<String> result = httpState.verify(sequence);
            String verificationResult = result.get(10, TimeUnit.SECONDS);

            ObjectNode resultNode = objectMapper.createObjectNode();
            if (verificationResult == null || verificationResult.isEmpty()) {
                resultNode.put("verified", true);
                resultNode.put("message", "Request sequence verification passed");
            } else {
                resultNode.put("verified", false);
                resultNode.put("message", verificationResult);
            }
            return resultNode;
        } catch (Exception e) {
            return errorResult("Sequence verification failed", e);
        }
    }

    private void registerRetrieveRequestResponses() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("method").put("type", "string").put("description", "Filter by HTTP method");
        properties.putObject("path").put("type", "string").put("description", "Filter by request path");
        properties.putObject("limit").put("type", "integer").put("description", "Maximum number of pairs to return (default 50)");

        tools.put("retrieve_request_responses", new ToolDefinition(
            "retrieve_request_responses",
            "Retrieves request-response pairs that were recorded by MockServer",
            schema,
            this::handleRetrieveRequestResponses
        ));
    }

    private JsonNode handleRetrieveRequestResponses(JsonNode params) {
        try {
            HttpRequest filterRequest = request();
            JsonNode methodNode = params.path("method");
            if (!methodNode.isMissingNode() && !methodNode.isNull()) {
                filterRequest.withMethod(methodNode.asText());
            }
            JsonNode pathNode = params.path("path");
            if (!pathNode.isMissingNode() && !pathNode.isNull()) {
                filterRequest.withPath(pathNode.asText());
            }
            int limit = params.path("limit").asInt(50);
            if (limit < 1 || limit > 500) {
                return errorResult("'limit' must be between 1 and 500");
            }

            HttpRequest retrieveRequest = request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", "REQUEST_RESPONSES")
                .withQueryStringParameter("format", "JSON")
                .withBody(getRequestDefinitionSerializer().serialize(filterRequest));

            HttpResponse retrieveResponse = httpState.retrieve(retrieveRequest);
            String body = retrieveResponse.getBodyAsString();

            if (body != null && !body.isEmpty()) {
                JsonNode allPairs = objectMapper.readTree(body);
                if (allPairs.isArray() && allPairs.size() > limit) {
                    ArrayNode limited = objectMapper.createArrayNode();
                    for (int i = allPairs.size() - limit; i < allPairs.size(); i++) {
                        limited.add(allPairs.get(i));
                    }
                    ObjectNode resultNode = objectMapper.createObjectNode();
                    resultNode.set("requestResponses", limited);
                    resultNode.put("total", allPairs.size());
                    resultNode.put("returned", limited.size());
                    return resultNode;
                } else {
                    ObjectNode resultNode = objectMapper.createObjectNode();
                    resultNode.set("requestResponses", allPairs);
                    resultNode.put("total", allPairs.isArray() ? allPairs.size() : 0);
                    resultNode.put("returned", allPairs.isArray() ? allPairs.size() : 0);
                    return resultNode;
                }
            }

            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.set("requestResponses", objectMapper.createArrayNode());
            resultNode.put("total", 0);
            resultNode.put("returned", 0);
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to retrieve request-response pairs", e);
        }
    }

    private void registerCreateForwardExpectation() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("path").put("type", "string").put("description", "Request path to match for forwarding");
        properties.putObject("host").put("type", "string").put("description", "Host to forward requests to");
        properties.putObject("port").put("type", "integer").put("description", "Port to forward requests to (default 443)");
        ObjectNode schemeProp = properties.putObject("scheme");
        schemeProp.put("type", "string").put("description", "Scheme for forwarding: HTTP or HTTPS (default HTTPS)");
        ArrayNode schemeEnum = schemeProp.putArray("enum");
        schemeEnum.add("HTTP");
        schemeEnum.add("HTTPS");
        ArrayNode required = schema.putArray("required");
        required.add("path");
        required.add("host");

        tools.put("create_forward_expectation", new ToolDefinition(
            "create_forward_expectation",
            "Creates a forward/proxy expectation that forwards matching requests to a specified host",
            schema,
            this::handleCreateForwardExpectation
        ));
    }

    private JsonNode handleCreateForwardExpectation(JsonNode params) {
        try {
            String path = params.path("path").asText(null);
            if (path == null || path.trim().isEmpty()) {
                return errorResult("'path' is required and must not be blank");
            }
            String host = params.path("host").asText(null);
            if (host == null || host.trim().isEmpty()) {
                return errorResult("'host' is required and must not be blank");
            }
            JsonNode portNode = params.path("port");
            int port = 443;
            if (!portNode.isMissingNode() && !portNode.isNull()) {
                if (!portNode.isIntegralNumber()) {
                    return errorResult("'port' must be an integer");
                }
                port = portNode.asInt();
                if (port < 1 || port > 65535) {
                    return errorResult("'port' must be between 1 and 65535");
                }
            }
            String scheme = params.path("scheme").asText("HTTPS");
            if (!"HTTP".equals(scheme) && !"HTTPS".equals(scheme)) {
                return errorResult("'scheme' must be HTTP or HTTPS");
            }

            HttpRequest httpRequest = request().withPath(path);
            HttpForward httpForward = HttpForward.forward()
                .withHost(host)
                .withPort(port)
                .withScheme(HttpForward.Scheme.valueOf(scheme.toUpperCase()));

            Expectation expectation = Expectation.when(httpRequest).thenForward(httpForward);
            List<Expectation> result = httpState.add(expectation);

            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("status", "created");
            resultNode.put("count", result.size());
            if (!result.isEmpty()) {
                resultNode.put("id", result.get(0).getId());
            }
            resultNode.put("forwardHost", host);
            resultNode.put("forwardPort", port);
            resultNode.put("forwardScheme", scheme);
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to create forward expectation", e);
        }
    }

    private void registerDebugRequestMismatch() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("method").put("type", "string").put("description", "HTTP method of the request to debug");
        properties.putObject("path").put("type", "string").put("description", "Path of the request to debug");
        properties.putObject("headers").put("type", "object").put("description", "Headers of the request to debug");
        ObjectNode bodyProp = properties.putObject("body");
        bodyProp.put("description", "Body of the request to debug");
        ArrayNode required = schema.putArray("required");
        required.add("method");
        required.add("path");

        tools.put("debug_request_mismatch", new ToolDefinition(
            "debug_request_mismatch",
            "Analyzes why a request does not match any active expectations, showing per-field match failures for each expectation",
            schema,
            this::handleDebugRequestMismatch
        ));
    }

    private JsonNode handleDebugRequestMismatch(JsonNode params) {
        try {
            HttpRequest httpRequest = request()
                .withMethod(params.path("method").asText())
                .withPath(params.path("path").asText());

            JsonNode headersNode = params.path("headers");
            if (headersNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = headersNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    httpRequest.withHeader(entry.getKey(), entry.getValue().asText());
                }
            }

            JsonNode bodyNode = params.path("body");
            if (!bodyNode.isMissingNode() && !bodyNode.isNull()) {
                if (bodyNode.isTextual()) {
                    httpRequest.withBody(bodyNode.asText());
                } else {
                    httpRequest.withBody(objectMapper.writeValueAsString(bodyNode));
                }
            }

            List<HttpRequestMatcher> matchers = httpState.getRequestMatchers().retrieveRequestMatchers(null);
            ArrayNode expectationResults = objectMapper.createArrayNode();

            for (HttpRequestMatcher matcher : matchers) {
                ObjectNode matchResult = objectMapper.createObjectNode();
                Expectation expectation = matcher.getExpectation();
                if (expectation != null) {
                    matchResult.put("expectationId", expectation.getId());
                    if (expectation.getHttpRequest() instanceof HttpRequest) {
                        HttpRequest expRequest = (HttpRequest) expectation.getHttpRequest();
                        matchResult.put("expectationPath", expRequest.getPath() != null ? expRequest.getPath().getValue() : "");
                        matchResult.put("expectationMethod", expRequest.getMethod() != null ? expRequest.getMethod().getValue() : "");
                    }
                }

                MatchDifference matchDifference = new MatchDifference(true, httpRequest);
                boolean matches = matcher.matches(matchDifference, httpRequest);
                matchResult.put("matches", matches);

                if (!matches) {
                    ObjectNode differences = objectMapper.createObjectNode();
                    Map<MatchDifference.Field, List<String>> allDifferences = matchDifference.getAllDifferences();
                    for (Map.Entry<MatchDifference.Field, List<String>> diffEntry : allDifferences.entrySet()) {
                        ArrayNode fieldDiffs = differences.putArray(diffEntry.getKey().getName());
                        for (String diff : diffEntry.getValue()) {
                            fieldDiffs.add(diff);
                        }
                    }
                    matchResult.set("differences", differences);
                }

                expectationResults.add(matchResult);
            }

            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("totalExpectations", matchers.size());
            resultNode.set("results", expectationResults);
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to debug request mismatch", e);
        }
    }

    private void registerCreateExpectationFromOpenApi() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("specUrlOrPayload").put("type", "string").put("description", "OpenAPI spec URL or JSON/YAML payload");
        properties.putObject("operationsAndResponses").put("type", "object").put("description", "Map of operationId to response status code");
        schema.putArray("required").add("specUrlOrPayload");

        tools.put("create_expectation_from_openapi", new ToolDefinition(
            "create_expectation_from_openapi",
            "Creates expectations from an OpenAPI specification, optionally specifying which operations and response codes to use",
            schema,
            this::handleCreateExpectationFromOpenApi
        ));
    }

    private JsonNode handleCreateExpectationFromOpenApi(JsonNode params) {
        try {
            String specUrlOrPayload = params.path("specUrlOrPayload").asText();
            OpenAPIExpectation openAPIExpectation = OpenAPIExpectation.openAPIExpectation(specUrlOrPayload);

            JsonNode opsNode = params.path("operationsAndResponses");
            if (opsNode.isObject()) {
                Map<String, String> operationsAndResponses = new LinkedHashMap<>();
                Iterator<Map.Entry<String, JsonNode>> fields = opsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    operationsAndResponses.put(entry.getKey(), entry.getValue().asText());
                }
                openAPIExpectation.withOperationsAndResponses(operationsAndResponses);
            }

            List<Expectation> result = httpState.add(openAPIExpectation);

            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("status", "created");
            resultNode.put("count", result.size());
            ArrayNode ids = resultNode.putArray("ids");
            for (Expectation exp : result) {
                ids.add(exp.getId());
            }
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to create expectations from OpenAPI", e);
        }
    }

    private void registerStopServer() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");

        tools.put("stop_server", new ToolDefinition(
            "stop_server",
            "Stops the MockServer instance",
            schema,
            this::handleStopServer
        ));
    }

    private JsonNode handleStopServer(JsonNode params) {
        try {
            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("status", "stopping");
            resultNode.put("message", "MockServer is shutting down");

            new Scheduler.SchedulerThreadFactory("MockServer MCP Stop").newThread(() -> server.stop()).start();

            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to stop server", e);
        }
    }

    private void registerRawExpectation() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("expectation").put("type", "object").put("description", "Full expectation JSON in MockServer format");
        schema.putArray("required").add("expectation");

        tools.put("raw_expectation", new ToolDefinition(
            "raw_expectation",
            "Creates an expectation using the full MockServer JSON format, supporting all features including templates, callbacks, and overrides",
            schema,
            this::handleRawExpectation
        ));
    }

    private JsonNode handleRawExpectation(JsonNode params) {
        try {
            JsonNode expectationNode = params.path("expectation");
            String expectationJson = objectMapper.writeValueAsString(expectationNode);

            Expectation[] expectations = getExpectationSerializer().deserializeArray(expectationJson, false);
            List<Expectation> allResults = new ArrayList<>();
            for (Expectation exp : expectations) {
                allResults.addAll(httpState.add(exp));
            }

            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("status", "created");
            resultNode.put("count", allResults.size());
            ArrayNode ids = resultNode.putArray("ids");
            for (Expectation exp : allResults) {
                ids.add(exp.getId());
            }
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to create raw expectation", e);
        }
    }

    private void registerRawRetrieve() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("requestDefinition").put("type", "object").put("description", "Request definition to filter results");
        ObjectNode typeProp = properties.putObject("type");
        typeProp.put("type", "string").put("description", "Type of data to retrieve");
        ArrayNode typeEnum = typeProp.putArray("enum");
        typeEnum.add("REQUESTS");
        typeEnum.add("REQUEST_RESPONSES");
        typeEnum.add("RECORDED_EXPECTATIONS");
        typeEnum.add("ACTIVE_EXPECTATIONS");
        typeEnum.add("LOGS");
        ObjectNode formatProp = properties.putObject("format");
        formatProp.put("type", "string").put("description", "Response format");
        ArrayNode formatEnum = formatProp.putArray("enum");
        formatEnum.add("JSON");
        formatEnum.add("JAVA");
        formatEnum.add("LOG_ENTRIES");

        tools.put("raw_retrieve", new ToolDefinition(
            "raw_retrieve",
            "Retrieves data from MockServer using the full retrieve API with complete control over type and format",
            schema,
            this::handleRawRetrieve
        ));
    }

    private JsonNode handleRawRetrieve(JsonNode params) {
        try {
            String type = params.path("type").asText("REQUESTS");
            if (!"REQUESTS".equals(type) && !"REQUEST_RESPONSES".equals(type) && !"RECORDED_EXPECTATIONS".equals(type) && !"ACTIVE_EXPECTATIONS".equals(type) && !"LOGS".equals(type)) {
                return errorResult("'type' must be one of: REQUESTS, REQUEST_RESPONSES, RECORDED_EXPECTATIONS, ACTIVE_EXPECTATIONS, LOGS");
            }
            String format = params.path("format").asText("JSON");
            if (!"JSON".equals(format) && !"JAVA".equals(format) && !"LOG_ENTRIES".equals(format)) {
                return errorResult("'format' must be one of: JSON, JAVA, LOG_ENTRIES");
            }

            HttpRequest retrieveRequest = request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", type)
                .withQueryStringParameter("format", format);

            JsonNode requestDefNode = params.path("requestDefinition");
            if (requestDefNode.isObject()) {
                retrieveRequest.withBody(objectMapper.writeValueAsString(requestDefNode));
            }

            HttpResponse retrieveResponse = httpState.retrieve(retrieveRequest);
            String body = retrieveResponse.getBodyAsString();

            if (body != null && !body.isEmpty()) {
                try {
                    JsonNode parsed = objectMapper.readTree(body);
                    ObjectNode resultNode = objectMapper.createObjectNode();
                    resultNode.set("data", parsed);
                    return resultNode;
                } catch (Exception e) {
                    ObjectNode resultNode = objectMapper.createObjectNode();
                    resultNode.put("data", body);
                    return resultNode;
                }
            }

            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("data", "");
            return resultNode;
        } catch (Exception e) {
            return errorResult("Failed to retrieve", e);
        }
    }

    private void registerRawVerify() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("verification").put("type", "object").put("description", "Full verification JSON in MockServer format");
        schema.putArray("required").add("verification");

        tools.put("raw_verify", new ToolDefinition(
            "raw_verify",
            "Performs request verification using the full MockServer verification JSON format",
            schema,
            this::handleRawVerify
        ));
    }

    private JsonNode handleRawVerify(JsonNode params) {
        try {
            JsonNode verificationNode = params.path("verification");
            String verificationJson = objectMapper.writeValueAsString(verificationNode);

            org.mockserver.serialization.VerificationSerializer verificationSerializer =
                new org.mockserver.serialization.VerificationSerializer(mockServerLogger);
            Verification verification = verificationSerializer.deserialize(verificationJson);

            Future<String> result = httpState.verify(verification);
            String verificationResult = result.get(10, TimeUnit.SECONDS);

            ObjectNode resultNode = objectMapper.createObjectNode();
            if (verificationResult == null || verificationResult.isEmpty()) {
                resultNode.put("verified", true);
                resultNode.put("message", "Verification passed");
            } else {
                resultNode.put("verified", false);
                resultNode.put("message", verificationResult);
            }
            return resultNode;
        } catch (Exception e) {
            return errorResult("Raw verification failed", e);
        }
    }

    private ObjectNode errorResult(String message) {
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("error", true);
        resultNode.put("message", message);
        return resultNode;
    }

    private ObjectNode errorResult(String message, Throwable throwable) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.WARN)
                .setMessageFormat("MCP tool error: {}")
                .setArguments(message)
                .setThrowable(throwable)
        );
        return errorResult(message);
    }

    public static class ToolDefinition {
        private final String name;
        private final String description;
        private final JsonNode inputSchema;
        private final Function<JsonNode, JsonNode> handler;

        public ToolDefinition(String name, String description, JsonNode inputSchema, Function<JsonNode, JsonNode> handler) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.handler = handler;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public JsonNode getInputSchema() {
            return inputSchema;
        }

        public Function<JsonNode, JsonNode> getHandler() {
            return handler;
        }
    }
}
