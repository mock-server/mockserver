package org.mockserver.imports;

import org.mockserver.fixture.FixtureRedactor;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.AfterAction;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Body;
import org.mockserver.model.FileBody;
import org.mockserver.model.JsonBody;
import org.mockserver.model.JsonPathBody;
import org.mockserver.model.MediaType;
import org.mockserver.model.RegexBody;
import org.mockserver.model.StringBody;
import org.mockserver.model.XPathBody;
import org.mockserver.model.XmlBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RequestDefinition;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies {@link FixtureRedactor} masking to expectations produced by the import
 * pipeline ({@link HarImporter}, {@link PostmanCollectionImporter}) before they
 * are persisted, so real credentials in captured traffic never land in the
 * expectation store.
 *
 * <p>Redaction is <strong>on by default</strong>. Imported requests and responses
 * have sensitive headers ({@code Authorization}, {@code X-Api-Key}, {@code Cookie},
 * {@code Set-Cookie}, {@code Proxy-Authorization}, etc. — see
 * {@link FixtureRedactor#defaultSensitiveHeaders()}) and well-known sensitive JSON
 * body fields replaced with {@link FixtureRedactor#REDACTED_PLACEHOLDER}. The
 * placeholder keeps the expectation structurally intact so it still matches.
 *
 * <p>Callers can disable redaction or extend the sensitive-key list via
 * {@link Options}. The expectation {@code id} assigned by the importer is preserved
 * across redaction (the underlying {@link FixtureRedactor} rebuilds expectations
 * without copying the id).
 */
public class ImportRedaction {

    /**
     * Default JSON body field names redacted on import (case-insensitive, matched
     * at any depth). Covers the common secret-bearing field names seen in captured
     * API traffic.
     */
    public static final Set<String> DEFAULT_SENSITIVE_BODY_FIELDS = Set.of(
        "api_key", "apiKey", "apikey",
        "access_token", "accessToken",
        "refresh_token", "refreshToken",
        "client_secret", "clientSecret",
        "password", "passwd", "pwd",
        "secret", "token", "authorization"
    );

    /**
     * Redaction options for an import. Defaults to redaction enabled with the
     * built-in sensitive header and body-field lists.
     */
    public static class Options {

        private boolean enabled = true;
        private final Set<String> additionalSensitiveHeaders = new LinkedHashSet<>();
        private final Set<String> additionalSensitiveBodyFields = new LinkedHashSet<>();

        /**
         * @return options with redaction enabled and the default sensitive lists
         */
        public static Options enabled() {
            return new Options();
        }

        /**
         * @return options with redaction disabled (imported values are kept verbatim)
         */
        public static Options disabled() {
            return new Options().withEnabled(false);
        }

        public Options withEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Add header names to redact on top of {@link FixtureRedactor#defaultSensitiveHeaders()}.
         */
        public Options withAdditionalSensitiveHeaders(Collection<String> headerNames) {
            if (headerNames != null) {
                this.additionalSensitiveHeaders.addAll(headerNames);
            }
            return this;
        }

        /**
         * Add JSON body field names to redact on top of {@link #DEFAULT_SENSITIVE_BODY_FIELDS}.
         */
        public Options withAdditionalSensitiveBodyFields(Collection<String> bodyFields) {
            if (bodyFields != null) {
                this.additionalSensitiveBodyFields.addAll(bodyFields);
            }
            return this;
        }

        Set<String> additionalSensitiveHeaders() {
            return additionalSensitiveHeaders;
        }

        Set<String> additionalSensitiveBodyFields() {
            return additionalSensitiveBodyFields;
        }
    }

    /**
     * Redact the supplied expectations according to {@code options}. When
     * redaction is disabled the input list is returned unchanged; otherwise a new
     * list of redacted expectations (preserving each expectation's id) is returned.
     *
     * @param expectations expectations produced by an importer (may be empty)
     * @param options      redaction options; defaults to enabled if {@code null}
     * @return the redacted (or original, when disabled) expectations
     */
    public static List<Expectation> redact(List<Expectation> expectations, Options options) {
        if (expectations == null || expectations.isEmpty()) {
            return expectations;
        }
        Options effectiveOptions = options != null ? options : Options.enabled();
        if (!effectiveOptions.isEnabled()) {
            return expectations;
        }

        Set<String> sensitiveHeaders = new LinkedHashSet<>(FixtureRedactor.defaultSensitiveHeaders());
        sensitiveHeaders.addAll(effectiveOptions.additionalSensitiveHeaders());

        Set<String> sensitiveBodyFields = new LinkedHashSet<>(DEFAULT_SENSITIVE_BODY_FIELDS);
        sensitiveBodyFields.addAll(effectiveOptions.additionalSensitiveBodyFields());

        FixtureRedactor redactor = new FixtureRedactor(sensitiveHeaders, sensitiveBodyFields);

        Expectation[] redacted = redactor.redact(expectations.toArray(new Expectation[0]));

        // FixtureRedactor rebuilds expectations without copying the id; restore the
        // importer-assigned ids so persisted expectations remain stable/upsertable.
        List<Expectation> result = new ArrayList<>(redacted.length);
        for (int i = 0; i < redacted.length; i++) {
            Expectation original = expectations.get(i);
            Expectation masked = redacted[i];
            if (original.getId() != null) {
                masked.withId(original.getId());
            }
            result.add(masked);
        }
        return result;
    }

    /**
     * Redact expectations produced by a migration importer ({@link WireMockImporter},
     * {@link MountebankImporter}, {@link MockoonImporter}) <strong>while preserving the action
     * type and matching constraints</strong>.
     *
     * <p>{@link #redact(List, Options)} delegates to {@link FixtureRedactor#redact(Expectation[])},
     * which only carries over response actions and rebuilds each expectation with default
     * {@code Times}/{@code TimeToLive} — fine for HAR/Postman (single static responses) but lossy
     * for the migration importers, which also emit {@code httpForward} (proxy), {@code httpError}
     * (fault), sequential/random multi-responses and {@code Times} constraints ({@code repeat}).
     * This method redacts the request and each response individually (via the granular
     * {@link FixtureRedactor#redactRequestDefinition}/{@link FixtureRedactor#redactResponseObject}
     * clones) and re-attaches them to a rebuilt expectation that keeps the original action type
     * (including a response template), {@code Times}, {@code TimeToLive}, priority, id, scenario state,
     * response mode and after-actions (whose webhook requests are redacted like the request matcher).
     *
     * @param expectations expectations produced by a migration importer (may be empty)
     * @param options      redaction options; defaults to enabled if {@code null}
     * @return the redacted (or original, when disabled) expectations
     */
    public static List<Expectation> redactPreservingActions(List<Expectation> expectations, Options options) {
        return redactPreservingActions(expectations, options, null, null);
    }

    /**
     * As {@link #redactPreservingActions(List, Options)}, also adding a warning (item taken from
     * {@code locators}, by position) for each request body matcher that redaction masked, which then
     * matches only a request carrying the placeholder, or that mentions a sensitive field but is an
     * expression or fragment redaction cannot mask, so a secret in it is kept.
     */
    public static List<Expectation> redactPreservingActions(List<Expectation> expectations, Options options, List<String> locators, List<ImportWarning> warnings) {
        if (expectations == null || expectations.isEmpty()) {
            return expectations;
        }
        Options effectiveOptions = options != null ? options : Options.enabled();
        if (!effectiveOptions.isEnabled()) {
            return expectations;
        }

        FixtureRedactor redactor = redactor(effectiveOptions);
        Set<String> sensitiveBodyFields = sensitiveBodyFields(effectiveOptions);

        List<Expectation> result = new ArrayList<>(expectations.size());
        for (int i = 0; i < expectations.size(); i++) {
            Expectation source = expectations.get(i);
            Expectation redacted = redactPreservingAction(source, redactor);
            if (warnings != null && locators != null && i < locators.size()) {
                String warning = bodyMatcherWarning(source.getHttpRequest(), redacted.getHttpRequest(), sensitiveBodyFields);
                if (warning != null) {
                    warnings.add(new ImportWarning(locators.get(i), "bodyPatterns", warning));
                }
            }
            result.add(redacted);
        }
        return result;
    }

    /**
     * True when redaction is on and the text mentions a sensitive body field name, so a literal that an
     * importer can separate from its selector (an XPath comparison value, say) should be masked.
     */
    static boolean mentionsSensitiveBodyField(String text, Options options) {
        Options effectiveOptions = options != null ? options : Options.enabled();
        return effectiveOptions.isEnabled() && sensitiveFieldIn(text, sensitiveBodyFields(effectiveOptions)) != null;
    }

    private static String bodyMatcherWarning(RequestDefinition source, RequestDefinition redacted, Set<String> sensitiveBodyFields) {
        if (!(source instanceof HttpRequest) || !(redacted instanceof HttpRequest)) {
            return null;
        }
        Body<?> sourceBody = ((HttpRequest) source).getBody();
        Body<?> redactedBody = ((HttpRequest) redacted).getBody();
        String redactedText = bodyText(redactedBody);
        if (redactedText != null && (redactedText.contains(FixtureRedactor.REDACTED_PLACEHOLDER) || redactedText.contains(FixtureRedactor.UNPARSEABLE_BODY_PLACEHOLDER))) {
            return "secret values in the body matcher were replaced by the redaction placeholder, so it now matches only a request carrying the placeholder;"
                + " import with redactSensitiveData=false, or adjust additionalRedactedBodyFields, to keep them";
        }
        boolean unreachable = sourceBody instanceof XPathBody || sourceBody instanceof JsonPathBody || sourceBody instanceof RegexBody || sourceBody instanceof StringBody;
        String field = unreachable ? sensitiveFieldIn(bodyText(redactedBody), sensitiveBodyFields) : null;
        if (field != null) {
            return "the body matcher mentions the sensitive field '" + field + "' but is kept as written, because redaction cannot mask a value inside an"
                + " expression or fragment without breaking it; check it does not hold a real secret";
        }
        return null;
    }

    private static String bodyText(Body<?> body) {
        if (body == null) {
            return null;
        }
        return body instanceof BinaryBody ? new String(body.getRawBytes(), StandardCharsets.UTF_8) : String.valueOf(body.getValue());
    }

    private static String sensitiveFieldIn(String text, Set<String> sensitiveBodyFields) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String field : new java.util.TreeSet<>(sensitiveBodyFields)) {
            if (lower.contains(field.toLowerCase(java.util.Locale.ROOT))) {
                return field;
            }
        }
        return null;
    }

    private static Set<String> sensitiveBodyFields(Options options) {
        Set<String> sensitiveBodyFields = new LinkedHashSet<>(DEFAULT_SENSITIVE_BODY_FIELDS);
        sensitiveBodyFields.addAll(options.additionalSensitiveBodyFields());
        return sensitiveBodyFields;
    }

    private static FixtureRedactor redactor(Options options) {
        Set<String> sensitiveHeaders = new LinkedHashSet<>(FixtureRedactor.defaultSensitiveHeaders());
        sensitiveHeaders.addAll(options.additionalSensitiveHeaders());
        return new FixtureRedactor(sensitiveHeaders, sensitiveBodyFields(options));
    }

    private static Expectation redactPreservingAction(Expectation source, FixtureRedactor redactor) {
        RequestDefinition redactedRequest = preserveBodyMatcher(source.getHttpRequest(), redactor.redactRequestDefinition(source.getHttpRequest()));

        Expectation result = new Expectation(
            redactedRequest,
            source.getTimes() != null ? source.getTimes() : Times.unlimited(),
            source.getTimeToLive() != null ? source.getTimeToLive() : TimeToLive.unlimited(),
            source.getPriority()
        );
        if (source.getId() != null) {
            result.withId(source.getId());
        }
        result
            .withScenarioName(source.getScenarioName())
            .withScenarioState(source.getScenarioState())
            .withNewScenarioState(source.getNewScenarioState());

        List<HttpResponse> responses = source.getHttpResponses();
        if (responses != null && !responses.isEmpty()) {
            List<HttpResponse> redactedResponses = new ArrayList<>(responses.size());
            for (HttpResponse response : responses) {
                redactedResponses.add(preserveFileBody(response, redactor.redactResponseObject(response)));
            }
            result.thenRespond(redactedResponses);
            if (source.getResponseMode() != null) {
                result.withResponseMode(source.getResponseMode());
            }
        } else if (source.getHttpResponse() != null) {
            result.thenRespond(preserveFileBody(source.getHttpResponse(), redactor.redactResponseObject(source.getHttpResponse())));
        } else if (source.getHttpForward() != null) {
            result.thenForward(source.getHttpForward());
        } else if (source.getHttpError() != null) {
            result.thenError(source.getHttpError());
        } else if (source.getHttpResponseTemplate() != null) {
            result.thenRespond(source.getHttpResponseTemplate());
        }
        if (source.getAfterActions() != null) {
            List<AfterAction> afterActions = new ArrayList<>(source.getAfterActions().size());
            for (AfterAction afterAction : source.getAfterActions()) {
                afterActions.add(redactAfterAction(afterAction, redactor));
            }
            result.withAfterActions(afterActions);
        }
        return result;
    }

    /**
     * The redactor rewrites any JSON-looking body as a plain string body. That masks secrets but loses the
     * matcher type, so the matcher is rebuilt around the redacted value. XPath, JsonPath and regex bodies are
     * expressions whose text form is a JSON descriptor the redactor only mangles, so they are kept as is.
     */
    private static RequestDefinition preserveBodyMatcher(RequestDefinition source, RequestDefinition redacted) {
        if (!(source instanceof HttpRequest) || !(redacted instanceof HttpRequest)) {
            return redacted;
        }
        Body<?> sourceBody = ((HttpRequest) source).getBody();
        Body<?> redactedBody = ((HttpRequest) redacted).getBody();
        HttpRequest result = (HttpRequest) redacted;
        if (sourceBody instanceof XPathBody || sourceBody instanceof JsonPathBody || sourceBody instanceof RegexBody) {
            return result.withBody(sourceBody);
        }
        if (sourceBody == null || sourceBody == redactedBody || !(redactedBody instanceof StringBody)
            || (sourceBody instanceof StringBody && !((StringBody) sourceBody).isSubString())) {
            return redacted;
        }
        String value = ((StringBody) redactedBody).getValue();
        MediaType contentType = sourceBody.getContentType() != null ? MediaType.parse(sourceBody.getContentType()) : null;
        Body<?> rebuilt;
        if (sourceBody instanceof JsonBody) {
            JsonBody json = (JsonBody) sourceBody;
            rebuilt = new JsonBody(value, null, contentType, json.getMatchType(), json.isMatchNumbersAsStrings());
        } else if (sourceBody instanceof StringBody) {
            rebuilt = new StringBody(value, null, true, contentType);
        } else if (sourceBody instanceof XmlBody) {
            rebuilt = new XmlBody(value, null, contentType);
        } else if (sourceBody instanceof BinaryBody) {
            rebuilt = new BinaryBody(value.getBytes(StandardCharsets.UTF_8), contentType);
        } else {
            return redacted;
        }
        rebuilt.withOptional(sourceBody.getOptional());
        rebuilt.setNot(sourceBody.getNot());
        return result.withBody(rebuilt);
    }

    /**
     * Redacts a response that is about to be serialised into a response template, which redaction cannot
     * reach once it is template text. Returns the response unchanged when redaction is disabled.
     */
    static HttpResponse redactResponse(HttpResponse response, Options options) {
        Options effectiveOptions = options != null ? options : Options.enabled();
        if (response == null || !effectiveOptions.isEnabled()) {
            return response;
        }
        return redactor(effectiveOptions).redactResponseObject(response);
    }

    /**
     * A file body refers to a file on the MockServer host rather than content in the imported document,
     * so it is kept rather than inlined by the redactor.
     */
    private static HttpResponse preserveFileBody(HttpResponse source, HttpResponse redacted) {
        if (source != null && redacted != null && source.getBody() instanceof FileBody) {
            redacted.withBody(source.getBody());
        }
        return redacted;
    }

    private static AfterAction redactAfterAction(AfterAction source, FixtureRedactor redactor) {
        if (source.getHttpRequest() == null) {
            return source;
        }
        RequestDefinition redacted = redactor.redactRequestDefinition(source.getHttpRequest());
        AfterAction result = AfterAction.afterAction().withHttpRequest(redacted instanceof HttpRequest ? (HttpRequest) redacted : source.getHttpRequest());
        if (source.getDelay() != null) {
            result.withDelay(source.getDelay());
        }
        if (source.getBlocking() != null) {
            result.withBlocking(source.getBlocking());
        }
        if (source.getTimeout() != null) {
            result.withTimeout(source.getTimeout());
        }
        if (source.getFailurePolicy() != null) {
            result.withFailurePolicy(source.getFailurePolicy());
        }
        return result;
    }

    /**
     * Redact recorded request/response pairs (loaded from a persisted NDJSON archive by
     * {@link RecordedTrafficImporter}) according to {@code options}, using the same sensitive-header
     * and body-field masking as {@link #redact(List, Options)}. When redaction is disabled the input
     * list is returned unchanged; otherwise a new list of redacted pairs is returned. This is a
     * defence-in-depth re-mask on the import side — the persist side already redacts on write when
     * {@code mockserver.redactSecretsInLog} is on, and re-masking an already-masked value is a no-op.
     *
     * @param pairs   recorded request/response pairs (may be null or empty)
     * @param options redaction options; defaults to enabled if {@code null}
     * @return the redacted (or original, when disabled) pairs
     */
    public static List<HttpRequestAndHttpResponse> redactRecordedTraffic(List<HttpRequestAndHttpResponse> pairs, Options options) {
        if (pairs == null || pairs.isEmpty()) {
            return pairs;
        }
        Options effectiveOptions = options != null ? options : Options.enabled();
        if (!effectiveOptions.isEnabled()) {
            return pairs;
        }

        Set<String> sensitiveHeaders = new LinkedHashSet<>(FixtureRedactor.defaultSensitiveHeaders());
        sensitiveHeaders.addAll(effectiveOptions.additionalSensitiveHeaders());

        Set<String> sensitiveBodyFields = new LinkedHashSet<>(DEFAULT_SENSITIVE_BODY_FIELDS);
        sensitiveBodyFields.addAll(effectiveOptions.additionalSensitiveBodyFields());

        FixtureRedactor redactor = new FixtureRedactor(sensitiveHeaders, sensitiveBodyFields);

        List<HttpRequestAndHttpResponse> result = new ArrayList<>(pairs.size());
        for (HttpRequestAndHttpResponse pair : pairs) {
            if (pair == null) {
                continue;
            }
            HttpRequestAndHttpResponse masked = new HttpRequestAndHttpResponse();
            RequestDefinition redactedRequest = redactor.redactRequestDefinition(pair.getHttpRequest());
            if (redactedRequest instanceof HttpRequest) {
                masked.withHttpRequest((HttpRequest) redactedRequest);
            } else {
                masked.withHttpRequest(pair.getHttpRequest());
            }
            masked.withHttpResponse(redactor.redactResponseObject(pair.getHttpResponse()));
            result.add(masked);
        }
        return result;
    }
}
