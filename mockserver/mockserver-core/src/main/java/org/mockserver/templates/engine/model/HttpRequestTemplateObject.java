package org.mockserver.templates.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.codec.MultipartFormDataDecoder;
import org.mockserver.codec.MultipartFormDataDecoder.DecodedPart;
import org.mockserver.codec.MultipartFormDataDecoder.DecodedParts;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.BodyDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author jamesdbloom
 */
public class HttpRequestTemplateObject extends RequestDefinition {
    private int hashCode;
    private String method = "";
    private String path = "";
    private final Map<String, List<String>> pathParameters = new HashMap<>();
    // Regex capture groups from the matched expectation's path pattern, 1-based aligned with
    // java.util.regex group numbering (index 0 is the whole match, index 1 the first capturing group),
    // so a template can reference e.g. $request.pathGroups[1] (Velocity), {{ request.pathGroups.1 }}
    // (Mustache) or request.pathGroups[1] (JavaScript). Empty when the matched path had no capture groups.
    private final List<String> pathGroups = new ArrayList<>();
    // Java named groups (?<name>...) from the matched expectation's path pattern, keyed by name.
    private final Map<String, String> namedPathGroups = new HashMap<>();
    private final Map<String, List<String>> queryStringParameters = new HashMap<>();
    private final Map<String, String> cookies = new HashMap<>();
    // Case-insensitive on the header NAME (issue #2575): a template can read $request.headers.Host
    // regardless of the casing the client sent, and vice-versa, consistent with HTTP field-name
    // case-insensitivity (RFC 9110). Insertion order and original casing are preserved.
    private final Map<String, List<String>> headers = new CaseInsensitiveHeaderMap();
    private BodyDTO body = null;
    private Boolean secure = null;
    private String jsonRpcId = null;
    private String jsonRpcRawId = null;
    private String jsonRpcMethod = null;
    // The derived fields are computed from the source request on first read and memoised in a holder allocated
    // only then, so a template that never reads them costs one field store; their getters are @JsonIgnore so
    // toString (which the JavaScript engine parses into its request object) neither computes them nor changes shape.
    // Plain source values (addresses, keepAlive, certificates) are read through source rather than copied, so
    // holding source, the logger and the holder does not grow the object a template render allocates.
    private final HttpRequest source;
    private final MockServerLogger mockServerLogger;
    private Derived derived;
    static final int MAX_FORM_FIELDS = 1000;
    static final int MAX_MULTIPART_PARTS = 100;
    static final int MAX_PARSED_BODY_BYTES = 10 * 1024 * 1024;
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestTemplateObject.class);
    private static final ObjectMapper JSON_OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public HttpRequestTemplateObject(HttpRequest httpRequest) {
        this(httpRequest, null);
    }

    /**
     * @param mockServerLogger receives a WARN entry, which a dry run reports as a template warning, when a size cap
     *                         stops {@code formData} or {@code parts} reading the whole body; may be null
     */
    public HttpRequestTemplateObject(HttpRequest httpRequest, MockServerLogger mockServerLogger) {
        source = httpRequest;
        this.mockServerLogger = mockServerLogger;
        if (httpRequest != null) {
            method = httpRequest.getMethod().getValue();
            path = httpRequest.getPath().getValue();
            for (Parameter parameter : httpRequest.getPathParameterList()) {
                pathParameters.put(parameter.getName().getValue(), parameter.getValues().stream().map(NottableString::getValue).collect(Collectors.toList()));
            }
            if (httpRequest.getPathGroups() != null) {
                pathGroups.addAll(httpRequest.getPathGroups());
            }
            if (httpRequest.getNamedPathGroups() != null) {
                namedPathGroups.putAll(httpRequest.getNamedPathGroups());
            }
            for (Parameter parameter : httpRequest.getQueryStringParameterList()) {
                queryStringParameters.put(parameter.getName().getValue(), parameter.getValues().stream().map(NottableString::getValue).collect(Collectors.toList()));
            }
            for (Header header : httpRequest.getHeaderList()) {
                headers.put(header.getName().getValue(), header.getValues().stream().map(NottableString::getValue).collect(Collectors.toList()));
            }
            for (Cookie cookie : httpRequest.getCookieList()) {
                cookies.put(cookie.getName().getValue(), cookie.getValue().getValue());
            }
            body = BodyDTO.createDTO(httpRequest.getBody());
            secure = httpRequest.isSecure();
            setNot(httpRequest.getNot());
            extractJsonRpcFields(httpRequest);
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, List<String>> getPathParameters() {
        return pathParameters;
    }

    /**
     * The numbered regex capture groups from the matched expectation's path pattern, 1-based aligned with
     * {@link java.util.regex.Matcher} group numbering (index 0 is the whole match). Empty when the matched
     * path had no capturing groups. Reference as {@code $request.pathGroups[1]} (Velocity),
     * {@code {{ request.pathGroups.1 }}} (Mustache) or {@code request.pathGroups[1]} (JavaScript).
     */
    public List<String> getPathGroups() {
        return pathGroups;
    }

    /**
     * The named regex capture groups {@code (?<name>...)} from the matched expectation's path pattern,
     * keyed by name. Empty when the matched path had no named groups.
     */
    public Map<String, String> getNamedPathGroups() {
        return namedPathGroups;
    }

    public Map<String, List<String>> getQueryStringParameters() {
        return queryStringParameters;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public String getBody() {
        return BodyDTO.toString(body);
    }

    @Deprecated
    public String getBodyAsString() {
        return BodyDTO.toString(body);
    }

    public Boolean getSecure() {
        return secure;
    }

    public List<X509Certificate> getClientCertificateChain() {
        return source != null ? source.getClientCertificateChain() : null;
    }

    public String getLocalAddress() {
        return source != null ? source.getLocalAddress() : null;
    }

    public String getRemoteAddress() {
        return source != null ? source.getRemoteAddress() : null;
    }

    public Boolean getKeepAlive() {
        return source != null ? source.isKeepAlive() : null;
    }

    public String getJsonRpcId() {
        return jsonRpcId;
    }

    public String getJsonRpcRawId() {
        return jsonRpcRawId;
    }

    public String getJsonRpcMethod() {
        return jsonRpcMethod;
    }

    /**
     * This request's id: the log correlation id MockServer gives every request it receives, the same id its
     * log entries carry. Null when the request did not arrive through the server (for example a dry run).
     */
    @JsonIgnore
    public String getId() {
        return source != null ? source.getLogCorrelationId() : null;
    }

    /**
     * {@code https} when the request arrived over TLS, otherwise {@code http}.
     */
    @JsonIgnore
    public String getScheme() {
        return Boolean.TRUE.equals(secure) ? "https" : "http";
    }

    /**
     * The host the client addressed, from the {@code Host} header (which an HTTP/2 or HTTP/3 {@code :authority}
     * becomes), falling back to the local address the request arrived on.
     */
    @JsonIgnore
    public String getHost() {
        return hostAndPort().host;
    }

    /**
     * The port the client addressed: the {@code Host} header's port, the scheme's default port when the header
     * names none, or the local address's port when there is no {@code Host} header.
     */
    @JsonIgnore
    public Integer getPort() {
        return hostAndPort().port;
    }

    /**
     * {@code scheme://authority}, the authority being the {@code Host} header as sent, else the local address.
     */
    @JsonIgnore
    public String getBaseUrl() {
        String hostHeader = firstHeaderValue("host");
        if (hostHeader != null && !hostHeader.isBlank()) {
            return getScheme() + "://" + hostHeader.trim();
        }
        Derived address = hostAndPort();
        if (address.host == null) {
            return null;
        }
        return getScheme() + "://" + (address.host.indexOf(':') >= 0 ? "[" + address.host + "]" : address.host) + (address.port != null ? ":" + address.port : "");
    }

    /**
     * The raw request body bytes, base64 encoded; empty when there is no body.
     */
    @JsonIgnore
    public String getBodyAsBase64() {
        Derived derived = derived();
        if (derived.bodyAsBase64 == null) {
            derived.bodyAsBase64 = source != null ? Base64.getEncoder().encodeToString(source.getBodyAsRawBytes()) : "";
        }
        return derived.bodyAsBase64;
    }

    /**
     * The fields of an {@code application/x-www-form-urlencoded} body, by name, in body order; empty for any other
     * body. At most {@value #MAX_FORM_FIELDS} fields are decoded; an undecodable body gives an empty map.
     */
    @JsonIgnore
    public Map<String, List<String>> getFormData() {
        Derived derived = derived();
        if (derived.formData == null) {
            derived.formData = decodeFormData();
        }
        return derived.formData;
    }

    /**
     * The parts of a {@code multipart/form-data} body, by part name, in body order; empty for any other body. A body
     * over {@value #MAX_PARSED_BODY_BYTES} bytes or with more than {@value #MAX_MULTIPART_PARTS} parts, or one
     * that cannot be decoded, gives an empty map.
     */
    @JsonIgnore
    public Map<String, List<MultipartPartTemplateObject>> getParts() {
        Derived derived = derived();
        if (derived.parts == null) {
            derived.parts = decodeParts();
        }
        return derived.parts;
    }

    /**
     * The named derived field as JSON, so the JavaScript engine can expose each one lazily on its parsed request
     * object without the field appearing in {@link #toString()}. Unknown names give {@code null}.
     */
    public String derivedFieldAsJson(String name) {
        try {
            Object value;
            switch (String.valueOf(name)) {
                case "id":
                    value = getId();
                    break;
                case "host":
                    value = getHost();
                    break;
                case "port":
                    value = getPort();
                    break;
                case "scheme":
                    value = getScheme();
                    break;
                case "baseUrl":
                    value = getBaseUrl();
                    break;
                case "bodyAsBase64":
                    value = getBodyAsBase64();
                    break;
                case "formData":
                    value = getFormData();
                    break;
                case "parts":
                    Map<String, List<Map<String, Object>>> described = new LinkedHashMap<>();
                    getParts().forEach((partName, partList) -> described.put(partName, partList.stream().map(MultipartPartTemplateObject::describeWithoutBody).collect(Collectors.toList())));
                    value = described;
                    break;
                default:
                    value = null;
            }
            return JSON_OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            LOGGER.debug("failed to serialise template request field {}: {}", name, e.getMessage());
            return "null";
        }
    }

    private String firstHeaderValue(String name) {
        List<String> values = headers.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    private Derived derived() {
        if (derived == null) {
            derived = new Derived();
        }
        return derived;
    }

    private Derived hostAndPort() {
        Derived derived = derived();
        if (!derived.hostAndPortDerived) {
            derived.hostAndPortDerived = true;
            deriveHostAndPort(derived);
        }
        return derived;
    }

    private void deriveHostAndPort(Derived derived) {
        String hostHeader = firstHeaderValue("host");
        if (hostHeader != null && !hostHeader.isBlank()) {
            String[] hostAndPort = splitHostAndPort(hostHeader.trim());
            if (hostAndPort != null) {
                derived.host = hostAndPort[0];
                derived.port = hostAndPort.length > 1 ? parsePort(hostAndPort[1]) : Integer.valueOf(Boolean.TRUE.equals(secure) ? 443 : 80);
                return;
            }
        }
        String localAddress = getLocalAddress();
        if (localAddress != null && !localAddress.isBlank()) {
            // a resolved InetSocketAddress renders as "hostname/ip:port"; the part after the slash is the address
            String address = localAddress.substring(localAddress.lastIndexOf('/') + 1).trim();
            String[] hostAndPort = splitHostAndPort(address);
            if (hostAndPort != null) {
                derived.host = hostAndPort[0];
                derived.port = hostAndPort.length > 1 ? parsePort(hostAndPort[1]) : null;
            }
        }
    }

    private static String[] splitHostAndPort(String hostPort) {
        if (hostPort.isEmpty()) {
            return null;
        }
        String[] parts = HttpRequest.splitHostPort(hostPort);
        if (parts.length == 0 || parts.length > 2 || parts[0].isEmpty()) {
            // an unbracketed IPv6 address: no port can be told apart from it
            return new String[]{hostPort};
        }
        return parts;
    }

    private static Integer parsePort(String port) {
        try {
            int value = Integer.parseInt(port.trim());
            return value >= 0 && value <= 65535 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private MediaType contentType() {
        String contentType = firstHeaderValue("content-type");
        return contentType != null ? MediaType.parse(contentType) : null;
    }

    private Map<String, List<String>> decodeFormData() {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        try {
            if (source.getBody() instanceof ParameterBody) {
                Map<String, List<String>> fields = new LinkedHashMap<>();
                for (Parameter parameter : ((ParameterBody) source.getBody()).getValue().getEntries()) {
                    fields.put(parameter.getName().getValue(), parameter.getValues().stream().map(NottableString::getValue).collect(Collectors.toList()));
                }
                return fields;
            }
            MediaType mediaType = contentType();
            if (mediaType == null || !"application".equalsIgnoreCase(mediaType.getType()) || !"x-www-form-urlencoded".equalsIgnoreCase(mediaType.getSubtype())) {
                return new LinkedHashMap<>();
            }
            byte[] bytes = bodyBytesWithinCap(() -> new LogEntry().setMessageFormat("request.formData is empty because the request body is larger than{}bytes, the most a template decodes"));
            if (bytes == null) {
                return new LinkedHashMap<>();
            }
            Charset charset = mediaType.getCharset() != null ? mediaType.getCharset() : StandardCharsets.UTF_8;
            String form = new String(bytes, charset);
            if (countOf('&', form) >= MAX_FORM_FIELDS) {
                warn(new LogEntry().setMessageFormat("request.formData read only the first{}fields of the request body").setArguments(MAX_FORM_FIELDS));
            }
            return new LinkedHashMap<>(new QueryStringDecoder(form, charset, false, MAX_FORM_FIELDS, true).parameters());
        } catch (RuntimeException e) {
            LOGGER.debug("request body could not be decoded as form data for a template: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Map<String, List<MultipartPartTemplateObject>> decodeParts() {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        try {
            String contentType = firstHeaderValue("content-type");
            MediaType mediaType = contentType != null ? MediaType.parse(contentType) : null;
            if (mediaType == null || !"multipart".equalsIgnoreCase(mediaType.getType()) || !"form-data".equalsIgnoreCase(mediaType.getSubtype())) {
                return new LinkedHashMap<>();
            }
            byte[] bytes = bodyBytesWithinCap(() -> new LogEntry().setMessageFormat("request.parts is empty because the request body is larger than{}bytes, the most a template decodes"));
            if (bytes == null) {
                return new LinkedHashMap<>();
            }
            DecodedParts decoded = new MultipartFormDataDecoder().decodeParts(contentType, bytes, MAX_MULTIPART_PARTS);
            if (decoded == null) {
                LOGGER.debug("request body could not be decoded as multipart/form-data for a template");
                return new LinkedHashMap<>();
            }
            if (decoded.tooManyParts()) {
                warn(new LogEntry().setMessageFormat("request.parts is empty because the request body has more than{}parts").setArguments(MAX_MULTIPART_PARTS));
                return new LinkedHashMap<>();
            }
            Map<String, List<MultipartPartTemplateObject>> byName = new LinkedHashMap<>();
            for (DecodedPart part : decoded.parts()) {
                byName.computeIfAbsent(part.getName(), name -> new ArrayList<>(1)).add(new MultipartPartTemplateObject(part));
            }
            return byName;
        } catch (RuntimeException e) {
            LOGGER.debug("request body could not be decoded as multipart/form-data for a template: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * The value of one part's {@code body} or {@code bodyAsBase64}, so the JavaScript engine copies a part's content
     * only when a template reads it; null for an unknown part or field.
     */
    public String partField(String name, int index, String field) {
        List<MultipartPartTemplateObject> partList = getParts().get(name);
        if (partList == null || index < 0 || index >= partList.size()) {
            return null;
        }
        if ("body".equals(field)) {
            return partList.get(index).getBody();
        }
        return "bodyAsBase64".equals(field) ? partList.get(index).getBodyAsBase64() : null;
    }

    /**
     * The body bytes, or null when there are none or there are more than {@value #MAX_PARSED_BODY_BYTES}, in which
     * case a warning names the cap. A segmented body's size is checked before its segments are joined into a copy.
     */
    private byte[] bodyBytesWithinCap(Supplier<LogEntry> oversizeWarning) {
        Body<?> body = source.getBody();
        SegmentedBytes segments = body instanceof BinaryBody ? ((BinaryBody) body).getSegmentedBytes() : body instanceof StringBody ? ((StringBody) body).getSegmentedBytes() : null;
        if (segments != null && segments.size() > MAX_PARSED_BODY_BYTES) {
            warn(oversizeWarning.get().setArguments(MAX_PARSED_BODY_BYTES));
            return null;
        }
        byte[] bytes = source.getBodyAsRawBytes();
        if (bytes.length > MAX_PARSED_BODY_BYTES) {
            warn(oversizeWarning.get().setArguments(MAX_PARSED_BODY_BYTES));
            return null;
        }
        return bytes.length == 0 ? null : bytes;
    }

    private void warn(LogEntry warning) {
        if (mockServerLogger != null) {
            mockServerLogger.logEvent(warning.setLogLevel(Level.WARN).setHttpRequest(source));
        }
    }

    private static int countOf(char character, String text) {
        int count = 0;
        for (int i = text.indexOf(character); i >= 0; i = text.indexOf(character, i + 1)) {
            count++;
        }
        return count;
    }

    private void extractJsonRpcFields(HttpRequest httpRequest) {
        try {
            String bodyString = httpRequest.getBodyAsString();
            if (bodyString != null && !bodyString.isEmpty() && bodyString.trim().startsWith("{")) {
                JsonNode root = JSON_OBJECT_MAPPER.readTree(bodyString);
                if (root.has("jsonrpc") && root.has("method")) {
                    JsonNode idNode = root.get("id");
                    if (idNode != null) {
                        jsonRpcId = idNode.isTextual() ? idNode.asText() : idNode.toString();
                        jsonRpcRawId = idNode.toString();
                    } else {
                        // A JSON-RPC notification carries no id. Every MCP and A2A mock builder emits
                        // "id": $!{request.jsonRpcRawId}, so leaving this unset rendered to nothing and
                        // produced unparseable JSON ("id": followed by the closing brace). Render the
                        // JSON null literal so the response is at least well-formed. jsonRpcId is
                        // deliberately left null -- it is a matching field, not a rendering one.
                        jsonRpcRawId = "null";
                    }
                    JsonNode methodNode = root.get("method");
                    if (methodNode != null) {
                        jsonRpcMethod = methodNode.asText();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.trace("failed to extract JSON-RPC fields from request body", e);
        }
    }

    public HttpRequestTemplateObject shallowClone() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        HttpRequestTemplateObject that = (HttpRequestTemplateObject) o;
        return Objects.equals(method, that.method) &&
            Objects.equals(path, that.path) &&
            Objects.equals(pathParameters, that.pathParameters) &&
            Objects.equals(pathGroups, that.pathGroups) &&
            Objects.equals(namedPathGroups, that.namedPathGroups) &&
            Objects.equals(queryStringParameters, that.queryStringParameters) &&
            Objects.equals(cookies, that.cookies) &&
            Objects.equals(headers, that.headers) &&
            Objects.equals(body, that.body) &&
            Objects.equals(secure, that.secure) &&
            Objects.equals(getLocalAddress(), that.getLocalAddress()) &&
            Objects.equals(getRemoteAddress(), that.getRemoteAddress()) &&
            Objects.equals(getKeepAlive(), that.getKeepAlive()) &&
            Objects.equals(jsonRpcId, that.jsonRpcId) &&
            Objects.equals(jsonRpcRawId, that.jsonRpcRawId) &&
            Objects.equals(jsonRpcMethod, that.jsonRpcMethod);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), method, path, pathParameters, pathGroups, namedPathGroups, queryStringParameters, cookies, headers, body, secure, getLocalAddress(), getRemoteAddress(), getKeepAlive(), jsonRpcId, jsonRpcRawId, jsonRpcMethod);
        }
        return hashCode;
    }

    private static final class Derived {
        private boolean hostAndPortDerived;
        private String host;
        private Integer port;
        private String bodyAsBase64;
        private Map<String, List<String>> formData;
        private Map<String, List<MultipartPartTemplateObject>> parts;
    }
}
