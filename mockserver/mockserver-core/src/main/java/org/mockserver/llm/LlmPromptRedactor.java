package org.mockserver.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.mockserver.fixture.FixtureRedactor;
import org.mockserver.imports.ImportRedaction;
import org.mockserver.model.Body;
import org.mockserver.model.Header;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.Parameters;
import org.mockserver.model.RequestDefinition;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts credentials out of the text of a prompt before it is sent to an external
 * LLM backend (the {@code /generateExpectation} stub-generation path, the
 * semantic drift enrichment path and the explain endpoints' {@code ?narrate=true}).
 * <p>
 * Redaction here is <b>always on</b> and independent of {@code redactSecretsInLog}:
 * a prompt leaves the process to a third-party service, so credentials in the
 * unmatched request or context expectations must never be included. It operates on
 * copies / strings only — the served request, the event log and the returned
 * expectations are never mutated.
 * <p>
 * Reuses {@link FixtureRedactor} for header, query-string and JSON-body-field
 * masking (and its {@link FixtureRedactor#REDACTED_PLACEHOLDER}); adds four checks
 * the fixture path does not need because a prompt is free text rather than a
 * structured expectation: JWT-shaped tokens anywhere in a body, credentials embedded
 * in URL userinfo ({@code scheme://user:pass@host}), {@code key=value} pairs in
 * non-JSON (form / plain-text) bodies, and credentials hidden inside a stringified
 * JSON value carried by a non-sensitive field (e.g. {@code {"data":"{\"password\":\"x\"}"}}).
 */
public class LlmPromptRedactor {

    /** Substituted for a JSON-looking body that cannot be parsed to locate its fields (fail closed). */
    public static final String BODY_OMITTED_UNPARSEABLE = "[body omitted: could not be parsed for redaction]";

    private static final int MAX_BODY_LENGTH = 2000;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    // Sensitive request headers redacted for prompts on top of FixtureRedactor's
    // defaults. Kept here, not in FixtureRedactor, so other features' fixture
    // redaction is unchanged.
    private static final List<String> EXTRA_SENSITIVE_HEADERS = List.of(
        "X-Auth-Token", "X-Access-Token", "X-Amz-Security-Token", "X-Csrf-Token"
    );

    // A JWT is three base64url segments separated by dots. Anchoring on the "eyJ"
    // header prefix (base64url of the opening {" of a JSON header) matches real
    // tokens while avoiding false positives on ordinary dotted strings such as
    // version numbers or hostnames.
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    // scheme://user:pass@host (or scheme://user@host) — mask the whole userinfo.
    private static final Pattern URL_USERINFO = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)([^/?#\\s@]+)@");

    private static final String DRIFT_HEADER_FIELD_PREFIX = "header.";

    // An OAuth authorization code is sent as "code", a name too common to mask on its
    // own (?code=GB), so it is masked only beside an OAuth signal: one of these
    // parameters, grant_type=authorization_code, or a callback/oauth/authorize path.
    private static final String OAUTH_CODE = "code";
    private static final Set<String> OAUTH_SIGNAL_PARAMETERS = caseInsensitiveSet(
        "state", "session_state", "redirect_uri", "client_id", "code_verifier", "iss"
    );
    private static final String OAUTH_GRANT_TYPE = "grant_type";
    private static final String AUTHORIZATION_CODE_GRANT = "authorization_code";
    private static final String OAUTH_PATH_SEGMENT = "/[^/?#;\\s]*(?:callback|oauth|oidc|authori[sz]e)";
    private static final Pattern OAUTH_PATH = Pattern.compile("(?i)" + OAUTH_PATH_SEGMENT);
    private static final Pattern OAUTH_TEXT_SIGNAL = Pattern.compile("(?i)(?<![A-Za-z0-9_-])(?:(?:"
        + String.join("|", OAUTH_SIGNAL_PARAMETERS) + ")\\s*=|"
        + OAUTH_GRANT_TYPE + "\\s*=\\s*" + AUTHORIZATION_CODE_GRANT + "\\b)|" + OAUTH_PATH_SEGMENT);
    // "code" must start the text or follow a separator, so error_code= is not matched
    private static final Pattern OAUTH_CODE_PAIR = Pattern.compile("(?i)(?<![^?&;#\\s])(" + OAUTH_CODE + "\\s*=\\s*)([^&#\\s]+)");

    private final FixtureRedactor fixtureRedactor;
    private final Set<String> sensitiveHeaders;
    private final Set<String> sensitiveBodyFields;
    private final Pattern keyValueCredential;

    /**
     * @param additionalSensitiveHeaderName the configured data-plane API-key header
     *                                       name to redact in addition to the default
     *                                       set, or {@code null}/blank when none is configured
     */
    public LlmPromptRedactor(String additionalSensitiveHeaderName) {
        Set<String> headers = new LinkedHashSet<>(FixtureRedactor.defaultSensitiveHeaders());
        headers.addAll(EXTRA_SENSITIVE_HEADERS);
        if (additionalSensitiveHeaderName != null && !additionalSensitiveHeaderName.isBlank()) {
            headers.add(additionalSensitiveHeaderName);
        }
        this.sensitiveHeaders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        this.sensitiveHeaders.addAll(headers);
        this.sensitiveBodyFields = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        this.sensitiveBodyFields.addAll(ImportRedaction.DEFAULT_SENSITIVE_BODY_FIELDS);
        this.sensitiveBodyFields.add("id_token");
        this.sensitiveBodyFields.add("idToken");
        this.sensitiveBodyFields.add("code_verifier");
        Set<String> queryParams = new LinkedHashSet<>(FixtureRedactor.defaultSensitiveQueryParams());
        queryParams.addAll(this.sensitiveBodyFields);
        this.fixtureRedactor = new FixtureRedactor(headers, this.sensitiveBodyFields, queryParams);
        this.keyValueCredential = compileKeyValuePattern(this.sensitiveBodyFields);
    }

    private static Set<String> caseInsensitiveSet(String... values) {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(List.of(values));
        return set;
    }

    private static Pattern compileKeyValuePattern(Set<String> fields) {
        StringBuilder alternation = new StringBuilder();
        for (String field : fields) {
            if (alternation.length() > 0) {
                alternation.append('|');
            }
            alternation.append(Pattern.quote(field));
        }
        // Left boundary: the field must NOT be preceded by an ASCII letter or digit, so a
        // separated prefix (my_password=, x-api_key=, a.token=) still matches while an
        // unrelated word is not split. Right boundary: a trailing \b keeps the field a
        // whole word, so "tokenizer=on" (token + izer) is not treated as "token".
        return Pattern.compile("(?i)(?<![A-Za-z0-9])((?:" + alternation + ")\\b\\s*=\\s*)([^&\\s]+)");
    }

    /**
     * Redact sensitive headers and query-string parameters in a request, returning a
     * clone. The original is never mutated. Values of sensitive headers and of query
     * parameters named like a credential (the query defaults plus the body-field set)
     * are replaced whole; every other header and query value has {@code key=value}
     * credentials, JWTs and URL userinfo masked. A {@code code} parameter is masked
     * when the request carries an OAuth signal (see {@link #isOAuthRequest}). The
     * clone's body is not used by callers
     * (bodies are handled by {@link #redactBodyForPrompt}), so only the header/query
     * masking of the returned clone is relied upon.
     */
    public HttpRequest redactHeadersAndQuery(HttpRequest request) {
        RequestDefinition redactedDefinition = fixtureRedactor.redactRequestDefinition(request);
        if (!(redactedDefinition instanceof HttpRequest)) {
            return request;
        }
        HttpRequest redacted = (HttpRequest) redactedDefinition;
        boolean oauthRequest = isOAuthRequest(request);
        List<Header> headerList = redacted.getHeaderList();
        if (headerList != null && !headerList.isEmpty()) {
            Headers headers = new Headers();
            for (Header header : headerList) {
                headers.withEntry(header.getName(), maskValues(header.getValues()));
            }
            redacted.withHeaders(headers);
        }
        List<Parameter> parameterList = redacted.getQueryStringParameterList();
        if (parameterList != null && !parameterList.isEmpty()) {
            Parameters parameters = new Parameters();
            for (Parameter parameter : parameterList) {
                if (oauthRequest && OAUTH_CODE.equalsIgnoreCase(parameter.getName().getValue())) {
                    parameters.withEntry(parameter.getName(), NottableString.string(FixtureRedactor.REDACTED_PLACEHOLDER));
                } else {
                    parameters.withEntry(parameter.getName(), maskValues(parameter.getValues()));
                }
            }
            redacted.withQueryStringParameters(parameters);
        }
        return redacted;
    }

    /**
     * Whether a request looks like part of an OAuth authorization-code flow: its path
     * names a callback, oauth, oidc or authorize endpoint, or its query string carries
     * {@code state}, {@code session_state}, {@code redirect_uri}, {@code client_id},
     * {@code code_verifier}, {@code iss} (RFC 9207) or {@code grant_type=authorization_code}.
     */
    private static boolean isOAuthRequest(HttpRequest request) {
        if (request.getPath() != null && request.getPath().getValue() != null
            && OAUTH_PATH.matcher(request.getPath().getValue()).find()) {
            return true;
        }
        List<Parameter> parameters = request.getQueryStringParameterList();
        if (parameters != null) {
            for (Parameter parameter : parameters) {
                String name = parameter.getName().getValue();
                if (OAUTH_SIGNAL_PARAMETERS.contains(name)) {
                    return true;
                }
                if (OAUTH_GRANT_TYPE.equalsIgnoreCase(name)) {
                    for (NottableString value : parameter.getValues()) {
                        if (AUTHORIZATION_CODE_GRANT.equalsIgnoreCase(value.getValue())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private List<NottableString> maskValues(List<NottableString> values) {
        List<NottableString> masked = new ArrayList<>(values.size());
        for (NottableString value : values) {
            masked.add(NottableString.string(maskFreeText(value.getValue()), value.isNot()));
        }
        return masked;
    }

    /**
     * Redact any credentials embedded in URL userinfo within a path / host / URL
     * string (e.g. an absolute-URI proxy path {@code http://user:pass@host/v1}).
     */
    public String maskUrlUserInfo(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return URL_USERINFO.matcher(value).replaceAll("$1" + Matcher.quoteReplacement(FixtureRedactor.REDACTED_PLACEHOLDER) + "@");
    }

    /**
     * Mask credentials in a request or expectation path: URL userinfo, JWTs (such as a
     * magic-link token) and {@code key=value} credential pairs (such as a
     * {@code ;token=} matrix parameter).
     */
    public String maskPath(String path) {
        return maskFreeText(path);
    }

    private String maskFreeText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return finishText(maskOAuthCode(maskKeyValue(value), false));
    }

    /**
     * Mask each {@code code=} value when the text, or the request it came from
     * ({@code oauthContext}), carries an OAuth signal.
     */
    private static String maskOAuthCode(String value, boolean oauthContext) {
        Matcher codePair = OAUTH_CODE_PAIR.matcher(value);
        if (!codePair.find() || !(oauthContext || OAUTH_TEXT_SIGNAL.matcher(value).find())) {
            return value;
        }
        return codePair.replaceAll("$1" + Matcher.quoteReplacement(FixtureRedactor.REDACTED_PLACEHOLDER));
    }

    /**
     * Produce the request-body text to include in the prompt, redacted and truncated
     * to {@value #MAX_BODY_LENGTH} characters. Returns {@link Optional#empty()} when
     * there is no body to include (absent, empty, or binary — binary bodies are
     * omitted rather than sent).
     */
    public Optional<String> redactBodyForPrompt(HttpRequest request) {
        Body<?> body = request.getBody();
        if (body != null && body.getType() == Body.Type.BINARY) {
            return Optional.empty();
        }
        String bodyString = request.getBodyAsText();
        if (bodyString == null || bodyString.isEmpty()) {
            return Optional.empty();
        }
        boolean looksLikeJson = body != null && body.getType() == Body.Type.JSON;
        return Optional.of(truncate(redactBodyText(bodyString, looksLikeJson, isOAuthRequest(request))));
    }

    /**
     * Redact a free-text body (JSON field values, JWTs, URL userinfo and, for non-JSON
     * text, {@code key=value} credential pairs). Used by the drift enrichment path,
     * which only ever holds string bodies. Does not truncate — the caller applies its
     * own body-length cap.
     *
     * @param body the body text (may be {@code null})
     * @return the redacted text, {@link #BODY_OMITTED_UNPARSEABLE} for a JSON-looking
     * but unparseable body, or {@code null} when {@code body} is null
     */
    public String redactText(String body) {
        if (body == null) {
            return null;
        }
        return redactBodyText(body, false, false);
    }

    private String redactBodyText(String body, boolean declaredJson, boolean oauthContext) {
        String trimmed = body.trim();
        boolean looksLikeJson = declaredJson || trimmed.startsWith("{") || trimmed.startsWith("[");
        if (looksLikeJson) {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(body);
                if (root != null && (root.isObject() || root.isArray())) {
                    redactJsonTree(root, oauthContext);
                    return finishText(OBJECT_MAPPER.writeValueAsString(root));
                }
                // a scalar JSON value carries no field — treat as plain text below
            } catch (Exception unparseableJson) {
                // Fail closed: a body that declares or looks like JSON but cannot be
                // parsed might hide a credential we cannot mask structurally, so drop it.
                return BODY_OMITTED_UNPARSEABLE;
            }
        }
        return finishText(maskOAuthCode(maskKeyValue(body), oauthContext));
    }

    /**
     * Recursively mask credentials in a parsed JSON tree: the value of any field whose
     * name is credential-like (at any depth) becomes the placeholder; every string leaf
     * is additionally passed through {@link #redactStringLeaf} so a credential hidden in
     * a stringified-JSON value or in a {@code key=value} pair inside a string is masked.
     * A {@code code} field is masked when the request or a sibling field is an OAuth signal.
     */
    private void redactJsonTree(JsonNode node, boolean oauthContext) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            boolean oauthObject = oauthContext || hasOAuthSignalField(object);
            Iterator<String> names = object.fieldNames();
            List<String> stringLeaves = new ArrayList<>();
            List<String> redactWhole = new ArrayList<>();
            while (names.hasNext()) {
                String name = names.next();
                if (sensitiveBodyFields.contains(name) || (oauthObject && OAUTH_CODE.equalsIgnoreCase(name))) {
                    redactWhole.add(name);
                } else {
                    JsonNode child = object.get(name);
                    if (child.isTextual()) {
                        stringLeaves.add(name);
                    } else {
                        redactJsonTree(child, oauthContext);
                    }
                }
            }
            for (String name : redactWhole) {
                object.put(name, FixtureRedactor.REDACTED_PLACEHOLDER);
            }
            for (String name : stringLeaves) {
                object.put(name, redactStringLeaf(object.get(name).asText(), oauthContext));
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode child = array.get(i);
                if (child.isTextual()) {
                    array.set(i, TextNode.valueOf(redactStringLeaf(child.asText(), oauthContext)));
                } else {
                    redactJsonTree(child, oauthContext);
                }
            }
        }
    }

    private static boolean hasOAuthSignalField(ObjectNode object) {
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (OAUTH_SIGNAL_PARAMETERS.contains(field.getKey())
                || (OAUTH_GRANT_TYPE.equalsIgnoreCase(field.getKey()) && AUTHORIZATION_CODE_GRANT.equalsIgnoreCase(field.getValue().asText()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mask credentials in a single JSON string leaf. If the leaf is itself a JSON
     * object/array (a stringified JSON payload), it is parsed and redacted recursively;
     * otherwise {@code key=value} credential pairs and JWTs within the string are masked.
     */
    private String redactStringLeaf(String value, boolean oauthContext) {
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode nested = OBJECT_MAPPER.readTree(value);
                if (nested != null && (nested.isObject() || nested.isArray())) {
                    redactJsonTree(nested, oauthContext);
                    return OBJECT_MAPPER.writeValueAsString(nested);
                }
            } catch (Exception notNestedJson) {
                // fall through: not a stringified JSON document, mask as plain text
            }
        }
        return maskJwts(maskOAuthCode(maskKeyValue(value), oauthContext));
    }

    private String maskKeyValue(String value) {
        return keyValueCredential.matcher(value).replaceAll("$1" + Matcher.quoteReplacement(FixtureRedactor.REDACTED_PLACEHOLDER));
    }

    private String maskJwts(String value) {
        return JWT.matcher(value).replaceAll(Matcher.quoteReplacement(FixtureRedactor.REDACTED_PLACEHOLDER));
    }

    private String finishText(String value) {
        return maskUrlUserInfo(maskJwts(value));
    }

    /**
     * Mask a single drift-record value — the drift prompt lists field names with their
     * expected/actual values verbatim. The whole value is masked when the field is a
     * credential-like body field or a sensitive response header ({@code header.<name>});
     * otherwise {@code key=value} credentials, JWTs and URL userinfo within it are masked.
     */
    public String maskDriftValue(String field, String value) {
        if (value == null) {
            return null;
        }
        if (field != null && (sensitiveBodyFields.contains(field) || isSensitiveHeaderField(field))) {
            return FixtureRedactor.REDACTED_PLACEHOLDER;
        }
        return maskFreeText(value);
    }

    /**
     * The credential values these requests carry in the headers, cookies, query parameters and body fields this
     * redactor masks, for scrubbing free text that quotes them, such as a match difference's "found" text.
     */
    public List<String> sensitiveValues(Collection<RequestDefinition> requests) {
        return fixtureRedactor.sensitiveValues(requests, List.of());
    }

    private boolean isSensitiveHeaderField(String field) {
        int prefixLength = DRIFT_HEADER_FIELD_PREFIX.length();
        return field.regionMatches(true, 0, DRIFT_HEADER_FIELD_PREFIX, 0, prefixLength)
            && sensitiveHeaders.contains(field.substring(prefixLength));
    }

    private static String truncate(String body) {
        if (body.length() > MAX_BODY_LENGTH) {
            return body.substring(0, MAX_BODY_LENGTH) + "...[truncated]";
        }
        return body;
    }
}
