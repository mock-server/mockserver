package org.mockserver.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Redaction for credentials that live <em>inside</em> a structured configuration value rather than
 * being the whole value.
 *
 * <p>Whole-value credentials are masked by property name — see
 * {@link ConfigurationProperties#isSensitivePropertyName(String)}. That name-keyed mask cannot reach a
 * secret nested in a structured value, of which there are two shapes:
 * <ul>
 *   <li>a {@code k=v,k2=v2} header list ({@code prometheusRemoteWriteHeaders}), which typically carries
 *       an {@code Authorization} or {@code Api-Key} header;</li>
 *   <li>a JSON document ({@code llmBackendsConfig}, whose documented shape is a <strong>path</strong> to
 *       a backends JSON file — see {@code LlmBackendResolver#namedBackends()} — so in its documented
 *       shape it holds no secret at all; a value that is itself a JSON document is nevertheless redacted
 *       per-field as defence-in-depth).</li>
 * </ul>
 *
 * <p>This is applied by <strong>every</strong> surface that discloses a configuration value —
 * {@code GET /mockserver/configuration} (via {@code ConfigurationDTO}), {@code GET /mockserver/config}
 * and {@code --print-config} (via {@link ConfigurationProperties#effectiveConfiguration()}), and the
 * startup property-file log dump — all routed through
 * {@link ConfigurationProperties#redactSensitiveValue(String, String)} so there is ONE notion of what a
 * credential is rather than one per endpoint.
 *
 * <p>Two properties are preserved throughout:
 * <ol>
 *   <li><strong>Byte-identical when there is nothing to redact.</strong> Every method returns the
 *       argument itself unless a secret was actually found, so a header list or a file path is
 *       unaffected — masking must not reformat ordinary configuration.</li>
 *   <li><strong>A masked value is never written back, and is never silently discarded either.</strong>
 *       {@code restore*} rebuilds the real value for each masked field from the value already held by
 *       the target, so a {@code GET}-then-{@code PUT} round trip cannot destroy a working credential —
 *       the same guarantee whole-value masking gives, applied per field. Any mask that CANNOT be
 *       resolved — buried inside a value, welded to extra text, in a field that is not
 *       credential-named, or naming a header/backend nothing is held for — makes the WHOLE value
 *       unmergeable: the held value is left untouched and the refusal is logged. Both halves matter.
 *       Writing the mask would make the literal {@code ***REDACTED***} the outbound credential; but
 *       quietly dropping the unresolvable part and writing the remainder deletes the credential just
 *       as effectively, while the enclosing {@code PUT} still answers {@code 200 OK}. Neither is a
 *       state this class will produce.</li>
 * </ol>
 *
 * <p>Unrecognised input fails closed: a sensitively-named JSON field is masked whatever its type
 * (string, number, array, object), and a value that is or merely EMBEDS an unparseable JSON
 * <em>document</em> is masked whole. Nothing here throws.
 *
 * <h2>Known limits of the name-keyed approach</h2>
 * <ul>
 *   <li><strong>A credential inline in a URL is not redacted.</strong> This masks by <em>name</em>
 *       (header name, JSON field name), so a secret carried inside an otherwise non-credential value —
 *       classically {@code "baseUrl":"https://user:p455w0rd@host/v1"} — is returned in clear. Any new
 *       property embedding a secret in a third shape needs its own per-shape treatment; the guard test
 *       is name-keyed and will not catch it automatically.</li>
 *   <li><strong>A header value containing a comma cannot be represented</strong> by the
 *       {@code k=v,k2=v2} format (there is no escape), and the consumer
 *       ({@code PrometheusRemoteWriteExporter#parseHeaders}) drops the tail after the comma. An entry
 *       with no {@code =} is therefore read here as the tail of the preceding value and masked with it,
 *       which is the fail-closed reading but means such an entry is not shown separately. On the way
 *       back, a COMMA-continuation after the mask is split off it and kept alongside the restored
 *       value. Anything else following the mask ({@code ***REDACTED***-my-new-key}) is refused rather
 *       than split: it reads as an operator typing a new credential over the mask, and welding it to
 *       the old value would persist neither the old credential nor the new one.</li>
 * </ul>
 *
 * <p>A plain {@link ObjectMapper} is used deliberately — this walks an anonymous JSON tree and must not
 * depend on the MockServer serializer graph, which itself binds {@code ConfigurationDTO} and would
 * invert the module layering of this package.
 */
final class EmbeddedCredentialRedaction {

    /**
     * {@link DeserializationFeature#FAIL_ON_TRAILING_TOKENS} is enabled deliberately, and this class is
     * not safe without it. It is OFF by default, so {@code readTree} parses the FIRST document in the
     * input and silently discards everything after it. A value such as
     * {@code {"a":1}{"name":"o","apiKey":"sk-…"}} would then be judged to hold no credential-named
     * field, and — because nothing was redacted — the ENTIRE original string, trailing document
     * included, was returned in clear on every surface that discloses configuration. Parsing must be
     * whole-value-or-nothing: a value that is not exactly one document is unparseable here, so it is
     * masked whole on read and refused on write.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final String MASK = ConfigurationProperties.REDACTED_VALUE;

    /** A JSON member — a quoted name followed by a colon. See {@link #looksLikeJsonDocument(String)}. */
    private static final Pattern JSON_MEMBER = Pattern.compile("\"[^\"]*\"\\s*:");

    /** U+FEFF, the zero-width byte-order mark. See {@link #stripLeading(String)}. */
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    /**
     * Credential-bearing names in HEADER and JSON-field vocabulary that are NOT credential names in
     * CONFIGURATION-PROPERTY vocabulary, so they live here rather than in
     * {@code ConfigurationProperties.SENSITIVE_SUBSTRINGS} — that set is applied to property names
     * too, where these words describe ordinary, readable settings that an operator debugging auth
     * needs to see: {@code authentication} appears in 16 property names such as
     * {@code tlsMutualAuthenticationRequired} (a boolean), {@code dataPlaneBasicAuthenticationRealm}
     * and {@code proxyAuthenticationUsername}, and {@code jwt} in the five
     * {@code controlPlaneJWTAuthentication*} settings. Masking those would hide non-secret
     * configuration from {@code --print-config} rather than protect anything.
     *
     * <p>As header/field names they are the opposite: {@code Authentication} is a real spelling of the
     * credential header, {@code X-JWT} carries a bearer assertion, and {@code Cookie} carries a session
     * credential. These also reach INSIDE {@code llmBackendsConfig}, whose backends carry a free-form
     * {@code headers} map.
     *
     * <p>{@code auth} rather than the two full spellings on purpose: it covers {@code Authorization},
     * {@code Proxy-Authorization}, {@code Authentication}, {@code WWW-Authenticate} and the bare
     * {@code X-Auth} in one rule, where enumerating spellings kept missing one. Over-masking a header
     * such as {@code X-Auth-Mode} is the accepted failure direction here, exactly as it is for the
     * {@code endsWith("key")} rule.
     */
    private static final List<String> HEADER_ONLY_SENSITIVE_SUBSTRINGS = List.of(
        "auth",
        "cookie",
        "jwt"
    );

    private EmbeddedCredentialRedaction() {
    }

    /**
     * Report a masked value that could not be resolved against the value held, and return the
     * "leave the held value untouched" signal. Failing closed is right, but failing SILENTLY is not:
     * the control plane answers {@code 200 OK} for the enclosing {@code PUT}, so without this the
     * operator believes a value was written that was in fact discarded.
     *
     * <p>The value itself is never logged — it carries the very credential this class exists to keep
     * out of readable output. The logger is resolved on demand rather than held in a static field:
     * this class is initialised during {@code ConfigurationProperties.<clinit>} (the startup
     * property-file dump redacts through it), and a static logger would drag logging initialisation
     * into that window — the #2338 failure shape.
     */
    static String dropUnmergeableValue(String propertyName, String reason) {
        LoggerFactory.getLogger(EmbeddedCredentialRedaction.class).warn(
            "ignoring the value supplied for {}: it carries the {} redaction mask but {}, so it cannot be "
                + "resolved against the value currently held — the existing value is left unchanged. To "
                + "change the credential, supply the new secret on its own in place of the entire mask",
            propertyName, MASK, reason);
        return null;
    }

    /**
     * True when a header name or JSON field name indicates its value is a credential.
     *
     * <p>Reuses {@link ConfigurationProperties#isSensitivePropertyName(String)} — the same
     * substring/{@code *key}-suffix shape rule the property store uses — after normalising away
     * {@code -} and {@code _} so {@code Api-Key} and {@code api_key} reach the {@code apikey} rule.
     *
     * <p>{@link #HEADER_ONLY_SENSITIVE_SUBSTRINGS} carries the names that are credential-bearing in
     * HEADER/field vocabulary but not in property-name vocabulary, so they belong here rather than in
     * the property-name rule.
     */
    static boolean isSensitiveName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String normalised = name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        for (String sensitive : HEADER_ONLY_SENSITIVE_SUBSTRINGS) {
            if (normalised.contains(sensitive)) {
                return true;
            }
        }
        return ConfigurationProperties.isSensitivePropertyName(normalised);
    }

    // ---------------------------------------------------------------------------------------------
    // k=v,k2=v2 header list
    // ---------------------------------------------------------------------------------------------

    /**
     * Redact the value of every credential-bearing header, leaving every other header — and the
     * ordering, spacing and any malformed entries — exactly as supplied.
     */
    static String redactHeaderList(String value) {
        if (value == null || value.isEmpty() || value.indexOf('=') < 0) {
            return value;
        }
        List<String> entries = headerEntries(value);
        boolean redacted = false;
        for (int i = 0; i < entries.size(); i++) {
            int equals = entries.get(i).indexOf('=');
            if (equals <= 0) {
                // no '=' or an empty name — malformed, and skipped by the exporter's parser too
                continue;
            }
            String name = entries.get(i).substring(0, equals);
            String headerValue = entries.get(i).substring(equals + 1);
            if (isSensitiveName(name.trim()) && !headerValue.trim().isEmpty()) {
                entries.set(i, name + "=" + MASK);
                redacted = true;
            }
        }
        return redacted ? String.join(",", entries) : value;
    }

    /**
     * Resolve an incoming header list against the one already held, replacing each masked header
     * value with the real value previously held under that header name.
     *
     * @return the header list to write, or {@code null} to leave the existing value untouched
     */
    static String restoreHeaderList(String propertyName, String incoming, String existing) {
        if (incoming == null || incoming.isEmpty() || !incoming.contains(MASK)) {
            return incoming;
        }
        if (MASK.equals(incoming.trim())) {
            // the whole value masked — treated exactly as a whole-value credential is
            return dropUnmergeableValue(propertyName, "the entire value is masked");
        }
        if (incoming.equals(redactHeaderList(existing))) {
            // an unedited GET-then-PUT: keep the held value verbatim, including its formatting
            return existing;
        }
        Map<String, String> held = parseHeaderList(existing);
        List<String> incomingNames = new ArrayList<>(parseHeaderList(incoming).keySet());
        List<String> restored = new ArrayList<>();
        for (String entry : headerEntries(incoming)) {
            int equals = entry.indexOf('=');
            if (equals <= 0) {
                restored.add(entry);
                continue;
            }
            String name = entry.substring(0, equals);
            String value = entry.substring(equals + 1).trim();
            if (!ConfigurationProperties.containsRedactionMask(value)) {
                restored.add(entry);
                continue;
            }
            String tail = value.startsWith(MASK) ? value.substring(MASK.length()) : null;
            if (tail == null || !(tail.isEmpty() || tail.startsWith(","))) {
                // the value is not a pure mask: either the mask is buried inside it ("Bearer
                // ***REDACTED***") or text is welded onto it ("***REDACTED***-my-new-key", which reads
                // as an operator typing a NEW credential over the mask). Neither is a form redaction
                // produces — a masked header value is replaced WHOLE — so neither can be resolved, and
                // guessing would persist a Frankenstein of the old value and the new one.
                return dropUnmergeableValue(propertyName,
                    "the value supplied for one of its headers is not exactly the mask");
            }
            // a tail that starts with a comma IS resolvable: the format cannot escape a comma, so
            // headerEntries re-attached a =-less segment to this value (see its javadoc) and the mask
            // stands for the held value that segment was appended to
            String trimmedName = name.trim();
            String heldValue = held.get(trimmedName);
            if (heldValue == null) {
                // no value is held under that exact name. Fall back to a case-insensitive match, since
                // HTTP header names are case-insensitive and re-casing one is a legitimate edit — but
                // ONLY when exactly one held name and one incoming name share that spelling. Where two
                // differ only in case they are two distinct headers to the consumer, and guessing which
                // one the mask stands for would cross-wire one credential onto both and destroy the
                // other.
                List<String> candidates = namesMatchingIgnoringCase(held.keySet(), trimmedName);
                if (candidates.size() > 1 || countMatchingIgnoringCase(incomingNames, trimmedName) > 1) {
                    return dropUnmergeableValue(propertyName,
                        "two headers in it differ only in case, so the mask cannot be resolved unambiguously");
                }
                if (candidates.isEmpty()) {
                    // Dropping the header (what this did before) writes a list whose credential is
                    // simply GONE — outbound auth breaks silently while the PUT answers 200 OK.
                    return dropUnmergeableValue(propertyName,
                        "no value is held under the name of one of its masked headers");
                }
                heldValue = held.get(candidates.get(0));
            }
            restored.add(name + "=" + heldValue + tail);
        }
        String merged = String.join(",", restored);
        if (merged.isEmpty()) {
            // nothing survived: writing "" would PIN an empty value on the Configuration instance,
            // which its getter then prefers over the static store, silently suppressing a
            // property-file or environment value. Leave the held value untouched instead — the same
            // contract restoreJsonDocument has.
            return dropUnmergeableValue(propertyName, "no header in it could be resolved");
        }
        if (ConfigurationProperties.containsRedactionMask(merged)) {
            // the invariant of this class, asserted rather than assumed: a mask still present after the
            // merge could not be resolved against anything held, and writing it would BOTH destroy the
            // live credential and make the literal mask the outbound one
            return dropUnmergeableValue(propertyName, "a header in it still carries the mask after merging");
        }
        return merged;
    }

    /**
     * The names in {@code names} that equal {@code name} ignoring case. More than one means the mask
     * cannot be resolved: they are distinct headers to the consumer, each with its own value.
     */
    private static List<String> namesMatchingIgnoringCase(Iterable<String> names, String name) {
        List<String> matching = new ArrayList<>();
        for (String candidate : names) {
            if (candidate.equalsIgnoreCase(name)) {
                matching.add(candidate);
            }
        }
        return matching;
    }

    private static int countMatchingIgnoringCase(Iterable<String> names, String name) {
        return namesMatchingIgnoringCase(names, name).size();
    }

    /**
     * Split a {@code k=v,k2=v2} list into entries, re-attaching any {@code =}-less entry to the one
     * before it. The format cannot escape a comma, so such an entry is the tail of the preceding
     * value; keeping the two together is what lets a comma-containing secret be masked whole instead
     * of leaking its tail.
     */
    private static List<String> headerEntries(String value) {
        List<String> entries = new ArrayList<>();
        for (String entry : value.split(",", -1)) {
            int last = entries.size() - 1;
            if (entry.indexOf('=') <= 0 && last >= 0 && entries.get(last).indexOf('=') > 0) {
                entries.set(last, entries.get(last) + "," + entry);
            } else {
                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * The header values currently held, indexed for restoration.
     *
     * <p>Keyed on the EXACT (trimmed) name, because the consumer keeps exact names too:
     * {@code PrometheusRemoteWriteExporter#parseHeaders} uses a case-sensitive map and applies the
     * result additively, so {@code X-Api-Key} and {@code x-api-key} are two headers and BOTH are sent.
     * Folding them together here would resolve one credential onto both names and destroy the other.
     * A repeated IDENTICAL name resolves LAST-wins, which is what that consumer does with it.
     *
     * <p>A secondary case-insensitive index exists only as a FALLBACK, because HTTP header names are
     * case-insensitive and re-casing one is a legitimate operator edit — but it is used solely when it
     * is unambiguous; see {@link #namesMatchingIgnoringCase(Iterable, String)}.
     */
    private static Map<String, String> parseHeaderList(String value) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (value == null || value.isEmpty()) {
            return parsed;
        }
        for (String entry : headerEntries(value)) {
            int equals = entry.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = entry.substring(0, equals).trim();
            if (!name.isEmpty()) {
                parsed.put(name, entry.substring(equals + 1));
            }
        }
        return parsed;
    }

    // ---------------------------------------------------------------------------------------------
    // JSON document (or, in its documented shape, a file path)
    // ---------------------------------------------------------------------------------------------

    /**
     * Redact every credential-bearing field of a JSON document, at any depth and whatever the field's
     * type. A value that is not a JSON document — the documented shape of {@code llmBackendsConfig} is
     * a file path — is returned unchanged; an unparseable document is redacted whole rather than
     * disclosed.
     */
    static String redactJsonDocument(String value) {
        if (!looksLikeJsonDocument(value)) {
            return value;
        }
        JsonNode root = parseOrNull(value);
        if (root == null) {
            return MASK;
        }
        if (!redactSecretFields(root)) {
            return value;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception writeFailure) {
            return MASK;
        }
    }

    /**
     * Resolve an incoming JSON document against the one already held, replacing each masked field
     * with the real value previously held at the same place in the document.
     *
     * <p>Array elements are matched by their {@code name} field when both sides carry one (so
     * reordering or inserting a backend cannot transplant one backend's key onto another), and by
     * index otherwise.
     *
     * @return the document to write, or {@code null} to leave the existing value untouched
     */
    static String restoreJsonDocument(String propertyName, String incoming, String existing) {
        if (incoming == null || incoming.isEmpty() || !incoming.contains(MASK)) {
            return incoming;
        }
        if (MASK.equals(incoming.trim())) {
            // the whole value masked (an unparseable document, or a masked GET of one)
            return dropUnmergeableValue(propertyName, "the entire value is masked");
        }
        if (!looksLikeJsonDocument(incoming)) {
            // not a document, so nothing was ever masked inside it — the token is part of the value
            return incoming;
        }
        if (incoming.equals(redactJsonDocument(existing))) {
            // an unedited GET-then-PUT: keep the held document verbatim, including its formatting
            return existing;
        }
        JsonNode incomingRoot = parseOrNull(incoming);
        if (incomingRoot == null) {
            // carries a mask but cannot be merged — writing it would make "***REDACTED***" the key
            return dropUnmergeableValue(propertyName, "it is not a parseable JSON document");
        }
        boolean[] destinationChanged = new boolean[1];
        if (!restoreSecretFields(incomingRoot, parseOrNull(existing), true, destinationChanged)) {
            return dropUnmergeableValue(propertyName, destinationChanged[0]
                ? "a backend's baseUrl or provider changed while its credential stayed masked, and a held "
                    + "credential is never sent to a new destination — supply that backend's credential again"
                : "no value is held for one of its masked fields — the backend it belongs to may have "
                    + "been renamed, reordered or newly added");
        }
        String merged;
        try {
            merged = OBJECT_MAPPER.writeValueAsString(incomingRoot);
        } catch (Exception writeFailure) {
            return dropUnmergeableValue(propertyName, "the merged document could not be written");
        }
        if (ConfigurationProperties.containsRedactionMask(merged)) {
            // the same invariant restoreHeaderList asserts. A mask surviving the merge was either
            // buried in a credential field's value (say "sk-***REDACTED***") or sat in a field that is
            // not credential-named at all — neither is a form redaction produces, so neither can be
            // resolved, and writing it would destroy the held credential and persist the mask
            return dropUnmergeableValue(propertyName, "a field in it still carries the mask after merging");
        }
        return merged;
    }

    /**
     * Whether a value is — or embeds — a JSON document, and so must be examined field by field rather
     * than disclosed.
     *
     * <p>A leading {@code [} or <code>{</code> is the normal case. The second rule is the fail-closed
     * one: a value carrying a quoted JSON member name embeds a document even when something precedes
     * it, and {@link #parseOrNull(String)} cannot parse such a value, so
     * {@link #redactJsonDocument(String)} masks it WHOLE. Without it a value such as
     * {@code backends=[{"name":"openai","apiKey":"sk-…"}]} — or one behind a byte-order mark — was
     * returned in clear.
     *
     * <p>The documented shape of {@code llmBackendsConfig} is a file PATH, and a path cannot match
     * either rule: it has no quotes. Paths therefore still pass through untouched.
     */
    private static boolean looksLikeJsonDocument(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String trimmed = stripLeading(value);
        return trimmed.startsWith("[")
            || trimmed.startsWith("{")
            || JSON_MEMBER.matcher(trimmed).find();
    }

    /**
     * Whitespace-trimmed and stripped of any leading byte-order mark. {@link String#trim()} removes
     * only characters at or below {@code U+0020}, so a BOM survives it and would push a document off
     * the leading-brace test — the shape that let a BOM-prefixed document be disclosed in clear. The
     * BOM must come off before parsing too: Jackson strips one only from a byte stream, not a String.
     */
    private static String stripLeading(String value) {
        String trimmed = value.trim();
        int start = 0;
        while (start < trimmed.length() && trimmed.charAt(start) == BYTE_ORDER_MARK) {
            start++;
        }
        return start == 0 ? trimmed : trimmed.substring(start).trim();
    }

    private static JsonNode parseOrNull(String value) {
        if (!looksLikeJsonDocument(value)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(stripLeading(value));
        } catch (Exception parseFailure) {
            return null;
        }
    }

    /**
     * Whether a sensitively-named field actually carries something worth masking. Anything other than
     * absent/null/empty counts — a secret is not always a string, and an array of tokens or a numeric
     * key must be masked whole rather than recursed past.
     */
    private static boolean isRedactableValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isTextual()) {
            return !node.asText().isEmpty();
        }
        if (node.isContainerNode()) {
            return node.size() > 0;
        }
        return true;
    }

    private static boolean redactSecretFields(JsonNode node) {
        boolean redacted = false;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            for (String name : fieldNames(object)) {
                JsonNode child = object.get(name);
                if (isSensitiveName(name)) {
                    // mask the whole subtree: never recurse past a credential-named field, or an
                    // array/object/number secret would pass through unmasked
                    if (isRedactableValue(child)) {
                        object.put(name, MASK);
                        redacted = true;
                    }
                } else {
                    redacted |= redactSecretFields(child);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                redacted |= redactSecretFields(child);
            }
        }
        return redacted;
    }

    /**
     * @return {@code false} when a masked credential field had no held counterpart to restore, so the
     * document as a whole cannot be resolved. Removing just that field (what this did before) writes a
     * document with the credential DELETED — which is the same silent-destruction failure this class
     * exists to prevent, reached by renaming a backend or reordering an unnamed one. A masked field is
     * not restored into an object whose destination ({@code baseUrl} or {@code provider}), or an enclosing
     * object's, changed either, so a held credential is never sent somewhere new; {@code destinationChanged}
     * records that case.
     */
    private static boolean restoreSecretFields(JsonNode incoming, JsonNode held, boolean sameDestinationSoFar, boolean[] destinationChanged) {
        boolean resolved = true;
        if (incoming.isObject()) {
            ObjectNode object = (ObjectNode) incoming;
            boolean sameDestination = sameDestinationSoFar && sameField(object, held, "baseUrl") && sameField(object, held, "provider");
            for (String name : fieldNames(object)) {
                JsonNode child = object.get(name);
                JsonNode heldChild = held != null && held.isObject() ? held.get(name) : null;
                if (isSensitiveName(name)) {
                    if (child.isTextual() && MASK.equals(child.asText())) {
                        if (!sameDestination) {
                            destinationChanged[0] = true;
                            resolved = false;
                        } else if (isRedactableValue(heldChild)) {
                            // whatever its type — the mask stands in for the whole subtree
                            object.set(name, heldChild.deepCopy());
                        } else {
                            resolved = false;
                        }
                    }
                    // else: a real value supplied by the client — written as given, not recursed into
                } else {
                    resolved &= restoreSecretFields(child, heldChild, sameDestination, destinationChanged);
                }
            }
        } else if (incoming.isArray()) {
            ArrayNode array = (ArrayNode) incoming;
            for (int index = 0; index < array.size(); index++) {
                resolved &= restoreSecretFields(array.get(index), heldElement(array.get(index), held, index), sameDestinationSoFar, destinationChanged);
            }
        }
        return resolved;
    }

    // an object with no held counterpart has nothing to restore from, which the caller reports separately
    private static boolean sameField(JsonNode incoming, JsonNode held, String name) {
        if (held == null || !held.isObject()) {
            return true;
        }
        JsonNode incomingValue = incoming.get(name);
        JsonNode heldValue = held.get(name);
        return incomingValue == null || heldValue == null ? incomingValue == heldValue : incomingValue.equals(heldValue);
    }

    private static JsonNode heldElement(JsonNode element, JsonNode heldArray, int index) {
        if (heldArray == null || !heldArray.isArray()) {
            return null;
        }
        JsonNode name = element.isObject() ? element.get("name") : null;
        if (name != null && name.isTextual()) {
            for (JsonNode candidate : heldArray) {
                JsonNode candidateName = candidate.isObject() ? candidate.get("name") : null;
                if (candidateName != null && candidateName.isTextual()
                    && candidateName.asText().equals(name.asText())) {
                    return candidate;
                }
            }
            // a named entry with no counterpart is new, so it has no prior secret to restore
            return null;
        }
        return index < heldArray.size() ? heldArray.get(index) : null;
    }

    private static List<String> fieldNames(ObjectNode object) {
        List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
