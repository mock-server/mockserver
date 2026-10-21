package org.mockserver.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Body;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.LogEventRequestAndResponse;
import org.mockserver.model.MediaType;
import org.mockserver.model.NottableMatcherString;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.RequestDefinition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Exports a list of {@link Expectation}s into third-party tooling formats —
 * OpenAPI 3 spec, Postman v2.1 collection, or a Bruno collection ZIP. Used
 * by the dashboard's Library / Export sub-tab and by direct API consumers
 * of {@code PUT /mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=...}.
 *
 * <p>The conversions are deliberately pragmatic: positive-string matchers
 * round-trip cleanly, while NottableString negation, regex bodies, callbacks,
 * forwards, templates, and errors are exported as best-effort placeholders.
 * Importers will not see the full MockServer matcher semantics — these
 * formats are designed for client requests + response examples, not full
 * mock-server expectation graphs.
 */
public class ExpectationExportSerializer {

    private static final String OPENAPI_VERSION = "3.0.3";

    /**
     * The fixed set of HTTP methods OpenAPI 3.0 permits as path-item operation
     * keys (lower-case). A recorded {@code CONNECT} (proxy mode) or any
     * arbitrary/whitespace method is not one of these and must NOT be emitted as
     * an operation key — doing so produces a schema-invalid document.
     */
    private static final Set<String> OPENAPI_METHODS = new HashSet<>(Arrays.asList(
        "get", "put", "post", "delete", "options", "head", "patch", "trace"));

    /** Matches OpenAPI path-template placeholders, e.g. the {@code {orderId}} in {@code /orders/{orderId}}. */
    private static final Pattern PATH_TEMPLATE = Pattern.compile("\\{([^/{}]+)}");
    private static final String POSTMAN_SCHEMA =
        "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";
    private static final String COLLECTION_NAME = "MockServer Exported Expectations";
    private static final String COLLECTION_DESCRIPTION =
        "Generated from MockServer's active expectations via the /mockserver/retrieve endpoint. " +
            "Matcher semantics (NottableString.not, regex bodies, etc.) are not exported.";

    private final MockServerLogger mockServerLogger;
    private final ObjectMapper objectMapper;

    public ExpectationExportSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.objectMapper = ObjectMapperFactory.createObjectMapper();
    }

    // -----------------------------------------------------------------------
    // OpenAPI 3
    // -----------------------------------------------------------------------

    public String serializeAsOpenApi(List<Expectation> expectations) {
        StringWriter writer = new StringWriter();
        try {
            writeOpenApi(expectations, writer);
            return writer.toString();
        } catch (IOException e) {
            return openApiFailure(e);
        }
    }

    /**
     * As {@link #serializeAsOpenApi}, writing the document to {@code writer} rather than building it as one
     * String. Operations are merged by path and method, so the document is built whole before it is written.
     * A failure part-way leaves what was written: discard it and answer {@link #openApiFailure} instead.
     */
    public void writeOpenApi(List<Expectation> expectations, Writer writer) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("openapi", OPENAPI_VERSION);
        ObjectNode info = root.putObject("info");
        info.put("title", COLLECTION_NAME);
        info.put("version", "1.0.0");
        info.put("description", COLLECTION_DESCRIPTION);

        ObjectNode paths = root.putObject("paths");
        // Defect 3: operationId must be unique across the whole document.
        Set<String> emittedOperationIds = new HashSet<>();
        for (Expectation expectation : expectations) {
            addOpenApiOperation(paths, expectation, emittedOperationIds);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, root);
    }

    /**
     * Logs a failure to write an OpenAPI document and returns the document answered in its place.
     */
    public String openApiFailure(IOException e) {
        if (mockServerLogger.isEnabledForInstance(org.slf4j.event.Level.INFO)) {
            mockServerLogger.logEvent(new org.mockserver.log.model.LogEntry()
                .setType(org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION)
                .setMessageFormat("exception while serialising expectations as OpenAPI: " + e.getMessage()));
        }
        // Return a minimal but schema-VALID OpenAPI document rather than "{}".
        return "{\"openapi\":\"" + OPENAPI_VERSION + "\","
            + "\"info\":{\"title\":\"" + COLLECTION_NAME + "\",\"version\":\"1.0\"},"
            + "\"paths\":{}}";
    }

    private void addOpenApiOperation(ObjectNode paths, Expectation expectation, Set<String> emittedOperationIds) {
        if (!(expectation.getHttpRequest() instanceof HttpRequest)) {
            return;
        }
        HttpRequest req = (HttpRequest) expectation.getHttpRequest();

        // Defect 2: never export the POSITIVE form of a negated path/method
        // matcher — that would invert the meaning. Schema matchers
        // and date-time matchers (NottableMatcherString) likewise carry no literal path/method string.
        if (isNegatedOrSchema(req.getPath()) || isNegatedOrSchema(req.getMethod())) {
            if (mockServerLogger.isEnabledForInstance(org.slf4j.event.Level.INFO)) {
                mockServerLogger.logEvent(new org.mockserver.log.model.LogEntry()
                    .setType(org.mockserver.log.model.LogEntry.LogMessageType.INFO)
                    .setMessageFormat("skipping OpenAPI export of expectation " + expectation.getId()
                        + " because a negated or schema path/method matcher cannot be represented as an OpenAPI operation"));
            }
            return;
        }

        // Defect 4: OpenAPI path keys must always start with "/", and an empty
        // path must normalise to "/".
        String rawPath = stringValue(req.getPath(), "/");
        if (rawPath.isEmpty()) {
            rawPath = "/";
        }
        String path = rawPath.startsWith("/") ? rawPath : "/" + rawPath;

        String method = stringValue(req.getMethod(), "get").trim().toLowerCase();
        if (method.isEmpty()) {
            method = "get";
        }
        // Defect 2: OpenAPI 3.0 only permits a fixed set of methods as path-item
        // operation keys. A recorded CONNECT (proxy mode) or an arbitrary/whitespace
        // method would produce a schema-invalid document, so skip the operation.
        if (!OPENAPI_METHODS.contains(method)) {
            if (mockServerLogger.isEnabledForInstance(org.slf4j.event.Level.INFO)) {
                mockServerLogger.logEvent(new org.mockserver.log.model.LogEntry()
                    .setType(org.mockserver.log.model.LogEntry.LogMessageType.INFO)
                    .setMessageFormat("skipping OpenAPI export of expectation " + expectation.getId()
                        + " because HTTP method \"" + stringValue(req.getMethod(), "")
                        + "\" is not a valid OpenAPI 3.0 operation"));
            }
            return;
        }

        ObjectNode pathItem = paths.has(path)
            ? (ObjectNode) paths.get(path)
            : paths.putObject(path);

        // Defect 3: same path + method must not overwrite — merge responses
        // into the existing operation instead of replacing it.
        if (pathItem.has(method)) {
            ObjectNode existing = (ObjectNode) pathItem.get(method);
            ObjectNode existingResponses = existing.has("responses")
                ? (ObjectNode) existing.get("responses")
                : existing.putObject("responses");
            addOpenApiResponses(existingResponses, expectation.getHttpResponse());
            return;
        }

        ObjectNode operation = pathItem.putObject(method);
        operation.put("summary", method.toUpperCase() + " " + path);
        // Defect 3: operationId must be unique across the whole document. A
        // distinct operation that reuses an explicit expectation id is
        // disambiguated with a deterministic numeric suffix.
        operation.put("operationId", uniqueOperationId(expectation.getId(), emittedOperationIds));

        // Parameters: query + path + headers. Negated/schema matchers are
        // omitted rather than exported in their positive form (Defect 2).
        ArrayNode parameters = operation.putArray("parameters");
        addOpenApiParameters(parameters, req.getQueryStringParameters(), "query", path);
        Set<String> emittedPathParams = addOpenApiParameters(parameters, req.getPathParameters(), "path", path);
        if (req.getHeaders() != null) {
            for (Header header : req.getHeaders().getEntries()) {
                if (isNegatedOrSchema(header.getName())) {
                    continue;
                }
                ObjectNode p = parameters.addObject();
                p.put("name", header.getName().getValue());
                p.put("in", "header");
                p.put("required", false);
                ObjectNode schema = p.putObject("schema");
                schema.put("type", "string");
            }
        }
        // Defect 1: every {name} placeholder in the path key MUST have a matching
        // in:path parameter, else the document is schema-invalid. Synthesise one
        // for any placeholder not already covered by an emitted path parameter.
        addSyntheticPathParameters(parameters, path, emittedPathParams);
        if (parameters.isEmpty()) {
            operation.remove("parameters");
        }

        // Response: take the httpResponse on the expectation as the canonical
        // example. Other action types (forward / template / etc.) export an
        // empty 200 response since they're dynamic.
        ObjectNode responses = operation.putObject("responses");
        addOpenApiResponses(responses, expectation.getHttpResponse());
    }

    private void addOpenApiResponses(ObjectNode responses, HttpResponse response) {
        if (response != null) {
            int code = response.getStatusCode() != null ? response.getStatusCode() : 200;
            String codeKey = String.valueOf(code);
            ObjectNode respNode = responses.has(codeKey)
                ? (ObjectNode) responses.get(codeKey)
                : responses.putObject(codeKey);
            if (!respNode.has("description")) {
                respNode.put("description",
                    response.getReasonPhrase() != null ? response.getReasonPhrase() : "OK");
            }
            addOpenApiBody(respNode, response.getBody(), response.getBodyAsText(),
                response.getFirstHeader("content-type"));
        } else if (responses.isEmpty()) {
            ObjectNode okNode = responses.putObject("200");
            okNode.put("description", "Dynamic response (forward / callback / template / error / LLM)");
        }
    }

    /**
     * Defect 5: emit a media-type entry with the correct body fidelity.
     * Prefers an explicit Content-Type header for the media-type key; branches
     * on the {@link Body} subtype; never emits a matcher pattern as a literal
     * example and represents binary bodies as a binary schema rather than a
     * base64 text example.
     */
    private void addOpenApiBody(ObjectNode respNode, Body<?> body,
                                String bodyString, String contentTypeHeader) {
        if (body == null) {
            if (bodyString == null || bodyString.isEmpty()) {
                return;
            }
            // No structured body but a string is available (e.g. captured pairs).
            String mediaType = isNotBlank(contentTypeHeader) ? contentTypeHeader : detectContentType(bodyString);
            emitExample(respNode, mediaType, bodyString);
            return;
        }

        Body.Type type = body.getType();
        switch (type) {
            case BINARY: {
                String mediaType = isNotBlank(contentTypeHeader) ? contentTypeHeader : "application/octet-stream";
                ObjectNode mediaTypeNode = respNode.putObject("content").putObject(mediaType);
                ObjectNode schema = mediaTypeNode.putObject("schema");
                schema.put("type", "string");
                schema.put("format", "binary");
                return;
            }
            case JSON: {
                String mediaType = isNotBlank(contentTypeHeader) ? contentTypeHeader : "application/json";
                emitExample(respNode, mediaType, bodyString);
                return;
            }
            case XML: {
                String mediaType = isNotBlank(contentTypeHeader) ? contentTypeHeader : "application/xml";
                emitExample(respNode, mediaType, bodyString);
                return;
            }
            case PARAMETERS: {
                String mediaType = isNotBlank(contentTypeHeader) ? contentTypeHeader : "application/x-www-form-urlencoded";
                emitExample(respNode, mediaType, bodyString);
                return;
            }
            case STRING: {
                String mediaType = isNotBlank(contentTypeHeader) ? contentTypeHeader : "text/plain";
                emitExample(respNode, mediaType, bodyString);
                return;
            }
            case REGEX:
            case FUZZY:
            case JSON_SCHEMA:
            case JSON_PATH:
            case XPATH:
            case XML_SCHEMA: {
                // Matcher-only bodies: never emit the raw pattern as a literal
                // example. Describe it instead so the meaning isn't inverted.
                String mediaType = isNotBlank(contentTypeHeader) ? contentTypeHeader : "text/plain";
                ObjectNode mediaTypeNode = respNode.putObject("content").putObject(mediaType);
                mediaTypeNode.putObject("schema")
                    .put("type", "string")
                    .put("description", "matcher body (" + type + ") — pattern not exported as a literal value");
                return;
            }
            default: {
                if (bodyString != null && !bodyString.isEmpty()) {
                    String mediaType = isNotBlank(contentTypeHeader) ? contentTypeHeader : detectContentType(bodyString);
                    emitExample(respNode, mediaType, bodyString);
                }
            }
        }
    }

    private void emitExample(ObjectNode respNode, String mediaType, String bodyString) {
        ObjectNode mediaTypeNode = respNode.putObject("content").putObject(mediaType);
        // A JSON example must be the JSON value itself: written as a string, an importer serves the string literal.
        JsonNode jsonExample = MediaType.parse(mediaType).isJson() ? parseJsonOrNull(bodyString) : null;
        if (jsonExample != null) {
            mediaTypeNode.set("example", jsonExample);
        } else {
            mediaTypeNode.put("example", bodyString != null ? bodyString : "");
        }
    }

    private JsonNode parseJsonOrNull(String bodyString) {
        if (bodyString == null || bodyString.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(bodyString);
        } catch (JsonProcessingException notJson) {
            return null;
        }
    }

    /**
     * Emits parameters for one location and returns the set of {@code in:path}
     * parameter names actually emitted (empty for non-path locations). The
     * caller uses that set to synthesise any uncovered path placeholders
     * (Defect 1).
     */
    private Set<String> addOpenApiParameters(ArrayNode parameters,
                                             org.mockserver.model.Parameters params,
                                             String location,
                                             String path) {
        Set<String> emittedPathParams = new LinkedHashSet<>();
        if (params == null) {
            return emittedPathParams;
        }
        for (Parameter parameter : params.getEntries()) {
            // Omit negated/schema parameter matchers rather than emitting their
            // positive form (Defect 2).
            if (isNegatedOrSchema(parameter.getName())) {
                continue;
            }
            String name = parameter.getName().getValue();
            // Defect 1: an in:path parameter is only valid when the path key
            // actually contains the matching {name} template segment. Omit it
            // otherwise rather than emitting a schema-INVALID parameter.
            if ("path".equals(location) && (name == null || !path.contains("{" + name + "}"))) {
                continue;
            }
            ObjectNode p = parameters.addObject();
            p.put("name", name);
            p.put("in", location);
            p.put("required", "path".equals(location));
            ObjectNode schema = p.putObject("schema");
            schema.put("type", "string");
            if (parameter.getValues() != null && !parameter.getValues().isEmpty()) {
                NottableString first = parameter.getValues().get(0);
                if (!isNegatedOrSchema(first)) {
                    schema.put("example", first.getValue());
                }
            }
            if ("path".equals(location)) {
                emittedPathParams.add(name);
            }
        }
        return emittedPathParams;
    }

    /**
     * Defect 1: every {@code {name}} placeholder in the path key must be declared
     * as an {@code in:path} parameter, otherwise swagger-parser rejects the
     * document ("Declared path parameter ... needs to be defined as a path
     * parameter"). Synthesise a {@code required:true, type:string} parameter for
     * any placeholder not already covered by an emitted path parameter.
     */
    private void addSyntheticPathParameters(ArrayNode parameters, String path, Set<String> emittedPathParams) {
        Set<String> seen = new LinkedHashSet<>(emittedPathParams);
        Matcher matcher = PATH_TEMPLATE.matcher(path);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (seen.add(name)) {
                ObjectNode p = parameters.addObject();
                p.put("name", name);
                p.put("in", "path");
                p.put("required", true);
                p.putObject("schema").put("type", "string");
            }
        }
    }

    /**
     * Defect 3: returns an operationId that has not yet been emitted in this
     * document. If {@code base} is already taken (a DISTINCT operation reusing an
     * explicit expectation id), a deterministic numeric suffix is appended.
     */
    private String uniqueOperationId(String base, Set<String> emittedOperationIds) {
        String candidate = base;
        int suffix = 2;
        while (!emittedOperationIds.add(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    /**
     * True when the matcher is negated ({@code NottableString.isNot()}) or is a
     * schema or date-time matcher ({@link NottableMatcherString}) whose value is not
     * a literal string. Either case means the raw value must NOT be
     * emitted as a positive literal in the exported OpenAPI document.
     */
    private boolean isNegatedOrSchema(NottableString s) {
        if (s == null) {
            return false;
        }
        return Boolean.TRUE.equals(s.isNot()) || s instanceof NottableMatcherString;
    }

    // -----------------------------------------------------------------------
    // Postman Collection v2.1
    // -----------------------------------------------------------------------

    public String serializeAsPostmanCollection(List<Expectation> expectations) {
        StringWriter writer = new StringWriter();
        try {
            writePostmanCollection(expectations, writer);
            return writer.toString();
        } catch (IOException e) {
            return postmanFailure(e);
        }
    }

    /**
     * As {@link #serializeAsPostmanCollection}, writing the collection to {@code writer} one item at a time
     * rather than building it as one tree and one String. A failure part-way leaves what was written:
     * discard it and answer {@link #postmanFailure} instead.
     */
    public void writePostmanCollection(List<Expectation> expectations, Writer writer) throws IOException {
        try (JsonGenerator generator = objectMapper.writerWithDefaultPrettyPrinter().createGenerator(writer)) {
            generator.writeStartObject();
            ObjectNode info = objectMapper.createObjectNode();
            info.put("name", COLLECTION_NAME);
            info.put("description", COLLECTION_DESCRIPTION);
            info.put("schema", POSTMAN_SCHEMA);
            generator.writeFieldName("info");
            objectMapper.writeTree(generator, info);

            generator.writeArrayFieldStart("item");
            for (Expectation expectation : expectations) {
                ObjectNode item = objectMapper.createObjectNode();
                addPostmanItem(item, expectation);
                objectMapper.writeTree(generator, item);
            }
            generator.writeEndArray();

            // Suggest a baseUrl variable so the collection isn't pinned to localhost.
            ArrayNode variables = objectMapper.createArrayNode();
            ObjectNode baseUrlVar = variables.addObject();
            baseUrlVar.put("key", "baseUrl");
            baseUrlVar.put("value", "http://localhost:1080");
            generator.writeFieldName("variable");
            objectMapper.writeTree(generator, variables);
            generator.writeEndObject();
        }
    }

    /**
     * Logs a failure to write a Postman collection and returns the document answered in its place.
     */
    public String postmanFailure(IOException e) {
        if (mockServerLogger.isEnabledForInstance(org.slf4j.event.Level.INFO)) {
            mockServerLogger.logEvent(new org.mockserver.log.model.LogEntry()
                .setType(org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION)
                .setMessageFormat("exception while serialising expectations as Postman: " + e.getMessage()));
        }
        return "{}";
    }

    private void addPostmanItem(ObjectNode item, Expectation expectation) {
        HttpRequest req = expectation.getHttpRequest() instanceof HttpRequest
            ? (HttpRequest) expectation.getHttpRequest()
            : new HttpRequest();
        String method = stringValue(req.getMethod(), "GET");
        String path = stringValue(req.getPath(), "/");
        item.put("name", method.toUpperCase() + " " + path);

        ObjectNode request = item.putObject("request");
        request.put("method", method.toUpperCase());

        // Headers
        ArrayNode headerArr = request.putArray("header");
        if (req.getHeaders() != null) {
            for (Header header : req.getHeaders().getEntries()) {
                String name = header.getName().getValue();
                String value = header.getValues() != null && !header.getValues().isEmpty()
                    ? header.getValues().get(0).getValue() : "";
                ObjectNode h = headerArr.addObject();
                h.put("key", name);
                h.put("value", value);
            }
        }

        // URL
        ObjectNode url = request.putObject("url");
        String pathSlash = path.startsWith("/") ? path : "/" + path;
        url.put("raw", "{{baseUrl}}" + pathSlash);
        ArrayNode hostArr = url.putArray("host");
        hostArr.add("{{baseUrl}}");
        ArrayNode pathSegments = url.putArray("path");
        for (String segment : pathSlash.split("/")) {
            if (!segment.isEmpty()) {
                pathSegments.add(segment);
            }
        }
        if (req.getQueryStringParameters() != null
            && !req.getQueryStringParameters().getEntries().isEmpty()) {
            ArrayNode queryArr = url.putArray("query");
            for (Parameter p : req.getQueryStringParameters().getEntries()) {
                ObjectNode q = queryArr.addObject();
                q.put("key", p.getName().getValue());
                q.put("value", p.getValues() != null && !p.getValues().isEmpty()
                    ? p.getValues().get(0).getValue() : "");
            }
        }

        // Body
        String requestBody = req.getBodyAsText();
        if (requestBody != null && !requestBody.isEmpty()) {
            ObjectNode body = request.putObject("body");
            body.put("mode", "raw");
            body.put("raw", requestBody);
            ObjectNode options = body.putObject("options");
            ObjectNode raw = options.putObject("raw");
            raw.put("language", "json");
        }

        // Response example
        HttpResponse response = expectation.getHttpResponse();
        ArrayNode responseArr = item.putArray("response");
        if (response != null) {
            ObjectNode resp = responseArr.addObject();
            int code = response.getStatusCode() != null ? response.getStatusCode() : 200;
            resp.put("name", "Example response");
            resp.put("status", response.getReasonPhrase() != null ? response.getReasonPhrase() : HttpResponseStatus.valueOf(code).reasonPhrase());
            resp.put("code", code);
            String bodyString = response.getBodyAsText();
            if (bodyString != null) {
                resp.put("body", bodyString);
            }
            ArrayNode respHeaders = resp.putArray("header");
            if (response.getHeaders() != null) {
                for (Header h : response.getHeaders().getEntries()) {
                    ObjectNode hn = respHeaders.addObject();
                    hn.put("key", h.getName().getValue());
                    hn.put("value", h.getValues() != null && !h.getValues().isEmpty()
                        ? h.getValues().get(0).getValue() : "");
                }
            }
            // The body's content type is served as Content-Type when no header sets one, so the example carries it.
            if (!response.containsHeader("Content-Type") && response.getBody() != null && isNotBlank(response.getBody().getContentType())) {
                ObjectNode hn = respHeaders.addObject();
                hn.put("key", "Content-Type");
                hn.put("value", response.getBody().getContentType());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Bruno (collection ZIP — multiple .bru files + bruno.json)
    // -----------------------------------------------------------------------

    public byte[] serializeAsBrunoCollection(List<Expectation> expectations) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            writeBrunoCollection(expectations, baos);
            return baos.toByteArray();
        } catch (IOException e) {
            return brunoFailure(e);
        }
    }

    /**
     * As {@link #serializeAsBrunoCollection}, writing the zip to {@code out} rather than into an array of its
     * own; {@code out} is closed. A failure part-way leaves what was written: discard it and answer
     * {@link #brunoFailure} instead.
     */
    public void writeBrunoCollection(List<Expectation> expectations, OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            // Manifest
            writeZipEntry(zip, "bruno.json", brunoManifest());
            // Environment with baseUrl
            writeZipEntry(zip, "environments/local.bru", brunoEnvironment());

            // One .bru file per expectation
            Map<String, Integer> nameCounts = new LinkedHashMap<>();
            int seq = 1;
            for (Expectation expectation : expectations) {
                String filename = brunoFilename(expectation, nameCounts);
                writeZipEntry(zip, filename, brunoRequestFile(expectation, seq++));
            }

            zip.finish();
        }
    }

    /**
     * Logs a failure to write a Bruno collection and returns the bytes answered in its place.
     */
    public byte[] brunoFailure(IOException e) {
        if (mockServerLogger.isEnabledForInstance(org.slf4j.event.Level.INFO)) {
            mockServerLogger.logEvent(new org.mockserver.log.model.LogEntry()
                .setType(org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION)
                .setMessageFormat("exception while serialising expectations as Bruno: " + e.getMessage()));
        }
        return new byte[0];
    }

    private String brunoManifest() {
        try {
            ObjectNode manifest = objectMapper.createObjectNode();
            manifest.put("version", "1");
            manifest.put("name", COLLECTION_NAME);
            manifest.put("type", "collection");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
        } catch (JsonProcessingException e) {
            return "{\"version\":\"1\",\"type\":\"collection\"}";
        }
    }

    private String brunoEnvironment() {
        return "vars {\n  baseUrl: http://localhost:1080\n}\n";
    }

    private String brunoFilename(Expectation expectation, Map<String, Integer> nameCounts) {
        HttpRequest req = expectation.getHttpRequest() instanceof HttpRequest
            ? (HttpRequest) expectation.getHttpRequest()
            : new HttpRequest();
        String method = stringValue(req.getMethod(), "GET").toLowerCase();
        String path = stringValue(req.getPath(), "root");
        String base = sanitiseFilename(method + path);
        int count = nameCounts.merge(base, 1, Integer::sum);
        return count == 1 ? base + ".bru" : base + "-" + count + ".bru";
    }

    private String sanitiseFilename(String name) {
        // Bruno filenames map onto disk; strip non-alphanumeric to dashes.
        String sanitised = name.replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("^-|-$", "");
        if (sanitised.isEmpty()) {
            sanitised = "request";
        }
        if (sanitised.length() > 80) {
            sanitised = sanitised.substring(0, 80);
        }
        return sanitised;
    }

    private String brunoRequestFile(Expectation expectation, int seq) {
        HttpRequest req = expectation.getHttpRequest() instanceof HttpRequest
            ? (HttpRequest) expectation.getHttpRequest()
            : new HttpRequest();
        String method = stringValue(req.getMethod(), "GET").toLowerCase();
        if (method.isEmpty()) {
            method = "get";
        }
        String path = stringValue(req.getPath(), "/");
        String pathSlash = path.startsWith("/") ? path : "/" + path;

        StringBuilder out = new StringBuilder();
        out.append("meta {\n");
        out.append("  name: ").append(method.toUpperCase()).append(' ').append(path).append('\n');
        out.append("  type: http\n");
        out.append("  seq: ").append(seq).append('\n');
        out.append("}\n\n");

        out.append(method).append(" {\n");
        out.append("  url: {{baseUrl}}").append(pathSlash);
        if (req.getQueryStringParameters() != null
            && !req.getQueryStringParameters().getEntries().isEmpty()) {
            out.append('?');
            boolean first = true;
            for (Parameter p : req.getQueryStringParameters().getEntries()) {
                if (!first) {
                    out.append('&');
                }
                first = false;
                out.append(p.getName().getValue()).append('=');
                if (p.getValues() != null && !p.getValues().isEmpty()) {
                    out.append(p.getValues().get(0).getValue());
                }
            }
        }
        out.append('\n');
        out.append("}\n");

        if (req.getHeaders() != null && !req.getHeaders().getEntries().isEmpty()) {
            out.append("\nheaders {\n");
            for (Header h : req.getHeaders().getEntries()) {
                String name = h.getName().getValue();
                String value = h.getValues() != null && !h.getValues().isEmpty()
                    ? h.getValues().get(0).getValue() : "";
                out.append("  ").append(name).append(": ").append(value).append('\n');
            }
            out.append("}\n");
        }

        String requestBody = req.getBodyAsText();
        if (requestBody != null && !requestBody.isEmpty()) {
            String mode = detectContentType(requestBody).contains("json") ? "json" : "text";
            out.append("\nbody:").append(mode).append(" {\n");
            for (String line : requestBody.split("\n", -1)) {
                out.append("  ").append(line).append('\n');
            }
            out.append("}\n");
        }

        // Documentation block — captures the response example so the user has
        // it next to the request even though Bruno doesn't surface examples
        // as a first-class concept in the .bru format.
        HttpResponse response = expectation.getHttpResponse();
        if (response != null) {
            out.append("\ndocs {\n  Expected response: ")
                .append(response.getStatusCode() != null ? response.getStatusCode() : 200)
                .append('\n');
            String bodyString = response.getBodyAsText();
            if (bodyString != null && !bodyString.isEmpty()) {
                out.append("  Body: ").append(bodyString.replace("\n", " ")).append('\n');
            }
            out.append("}\n");
        }

        return out.toString();
    }

    private void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    private String stringValue(NottableString s, String fallback) {
        if (s == null) {
            return fallback;
        }
        String v = s.getValue();
        return v != null ? v : fallback;
    }

    private String detectContentType(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "application/json";
        }
        if (trimmed.startsWith("<")) {
            return "application/xml";
        }
        return "text/plain";
    }

    // -----------------------------------------------------------------------
    // Convenience: support captured-traffic export (Recorded Requests panel
    // in the dashboard's Library/Export sub-tab). Each captured request +
    // response pair, or each request on its own, is wrapped into a synthetic
    // Expectation so all three formats can share the same generation code.
    // -----------------------------------------------------------------------

    public String serializeRequestResponsesAsOpenApi(List<LogEventRequestAndResponse> pairs) {
        return serializeAsOpenApi(expectationsFromPairs(pairs));
    }

    public String serializeRequestResponsesAsPostman(List<LogEventRequestAndResponse> pairs) {
        return serializeAsPostmanCollection(expectationsFromPairs(pairs));
    }

    public byte[] serializeRequestResponsesAsBruno(List<LogEventRequestAndResponse> pairs) {
        return serializeAsBrunoCollection(expectationsFromPairs(pairs));
    }

    public String serializeRequestsAsOpenApi(List<? extends RequestDefinition> requests) {
        return serializeAsOpenApi(expectationsFromRequests(requests));
    }

    public String serializeRequestsAsPostman(List<? extends RequestDefinition> requests) {
        return serializeAsPostmanCollection(expectationsFromRequests(requests));
    }

    public byte[] serializeRequestsAsBruno(List<? extends RequestDefinition> requests) {
        return serializeAsBrunoCollection(expectationsFromRequests(requests));
    }

    /**
     * Each captured pair with a request as an expectation that answers its response, as every format exports it.
     */
    public List<Expectation> expectationsFromPairs(List<LogEventRequestAndResponse> pairs) {
        List<Expectation> result = new ArrayList<>(pairs.size());
        for (LogEventRequestAndResponse pair : pairs) {
            HttpRequest request = pair.getHttpRequest();
            if (request == null) {
                continue;
            }
            Expectation expectation = new Expectation(request);
            if (pair.getHttpResponse() != null) {
                expectation.thenRespond(pair.getHttpResponse());
            }
            result.add(expectation);
        }
        return result;
    }

    /**
     * Each captured HTTP request as an expectation, as every format exports it.
     */
    public List<Expectation> expectationsFromRequests(List<? extends RequestDefinition> requests) {
        List<Expectation> result = new ArrayList<>(requests.size());
        for (RequestDefinition definition : requests) {
            if (definition instanceof HttpRequest) {
                result.add(new Expectation((HttpRequest) definition));
            }
        }
        return result;
    }
}
