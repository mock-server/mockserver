package org.mockserver.mock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.jayway.jsonpath.JsonPath;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.ObjectWithJsonToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Control-plane filter for retrieving and clearing expectations by metadata, by whether they have
 * ever matched a request, and for paging the retrieved list. It is sent as query string parameters
 * on {@code PUT /mockserver/retrieve} and {@code PUT /mockserver/clear}, alongside the existing
 * {@code namespace} filter and the request-matcher or expectation-id body.
 * <p>
 * Every selection is evaluated by a linear scan when the control-plane call is made. Nothing here
 * is read while matching requests, and nothing is indexed or maintained when expectations change.
 * <ul>
 * <li>{@code metadataKey} (with optional {@code metadataValue}): the expectation's metadata has that
 * key, and, when a value is given, the value equals it. A string value is compared as text. Any
 * other value is compared as JSON, so {@code 42}, {@code true} or {@code ["a","b"]} match the
 * number, boolean or array.</li>
 * <li>{@code metadataJsonPath}: the JSONPath, evaluated against the metadata object, selects at
 * least one value, for example {@code $[?(@.owner == 'team-a')]} or {@code $.tags[?(@ == 'smoke')]}.</li>
 * <li>{@code unmatched=true}: the expectation has not matched any request since it was last
 * created or changed.</li>
 * <li>{@code offset} and {@code limit}: page the result, after every other filter, in the order it is
 * returned (active expectations in match order, recorded expectations in the order received).</li>
 * </ul>
 * All selections that are present must hold. An expectation with no metadata matches no metadata
 * selection.
 */
public class ExpectationFilter extends ObjectWithJsonToString {

    public static final String METADATA_KEY_PARAMETER = "metadataKey";
    public static final String METADATA_VALUE_PARAMETER = "metadataValue";
    public static final String METADATA_JSON_PATH_PARAMETER = "metadataJsonPath";
    public static final String UNMATCHED_PARAMETER = "unmatched";
    public static final String LIMIT_PARAMETER = "limit";
    public static final String OFFSET_PARAMETER = "offset";

    private static final ObjectMapper OBJECT_MAPPER = MetadataJson.WRITER;
    private static final Comparator<JsonNode> NUMERIC_EQUALITY = (one, two) -> {
        if (one.isNumber() && two.isNumber()) {
            return one.decimalValue().compareTo(two.decimalValue());
        }
        return one.equals(two) ? 0 : 1;
    };

    private String metadataKey;
    private String metadataValue;
    private String metadataJsonPath;
    private boolean unmatched;
    private boolean filterParameterPresent;
    private boolean blankNamespaceParameter;
    private Integer limit;
    private Integer offset;
    private JsonPath compiledJsonPath;

    public static ExpectationFilter expectationFilter() {
        return new ExpectationFilter();
    }

    /**
     * Select expectations whose metadata has {@code key}, with any value.
     */
    public ExpectationFilter withMetadataKey(String key) {
        this.metadataKey = requireNotBlank(key, "metadata key");
        this.metadataValue = null;
        return this;
    }

    /**
     * Select expectations whose metadata has {@code key} with a value equal to {@code value}. A {@code String}
     * is sent as text; any other value is sent as its JSON. MockServer cannot tell which was sent, so it
     * matches a stored value that equals the text, or whose JSON equals the text parsed as JSON: {@code 42}
     * matches both the number 42 and the string "42", and {@code "\"team-a\""} also matches the string team-a.
     *
     * @throws IllegalArgumentException if the key is blank or the value is null (use {@link #withMetadataKey(String)}
     *                                  for any value, or {@code NullNode.getInstance()} for a JSON null)
     */
    public ExpectationFilter withMetadata(String key, Object value) {
        if (value == null) {
            // null must not read as "any value": that would widen a clear to every expectation with this key
            throw new IllegalArgumentException("the metadata value must not be null; use withMetadataKey(key) to select any value, or NullNode.getInstance() to select a JSON null");
        }
        this.metadataKey = requireNotBlank(key, "metadata key");
        this.metadataValue = value instanceof String ? (String) value : toJson(value);
        return this;
    }

    /**
     * Select expectations whose metadata object the JSONPath selects at least one value from.
     */
    public ExpectationFilter withMetadataJsonPath(String jsonPath) {
        this.compiledJsonPath = compileJsonPath(requireNotBlank(jsonPath, "metadata JSONPath"));
        this.metadataJsonPath = jsonPath;
        return this;
    }

    // A blank selection must fail rather than read as "no filter": on clear, "no filter" means clear everything.
    private static String requireNotBlank(String value, String description) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("the " + description + " must not be blank");
        }
        return value;
    }

    private static JsonPath compileJsonPath(String jsonPath) {
        try {
            return JsonPath.compile(jsonPath);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("\"" + jsonPath + "\" is not a valid JSONPath for \"" + METADATA_JSON_PATH_PARAMETER + "\": " + e.getMessage(), e);
        }
    }

    /**
     * Select only expectations that have not matched any request.
     */
    public ExpectationFilter withUnmatched(boolean unmatched) {
        this.unmatched = unmatched;
        return this;
    }

    public ExpectationFilter withLimit(Integer limit) {
        this.limit = limit;
        return this;
    }

    public ExpectationFilter withOffset(Integer offset) {
        this.offset = offset;
        return this;
    }

    public String getMetadataKey() {
        return metadataKey;
    }

    public String getMetadataValue() {
        return metadataValue;
    }

    public String getMetadataJsonPath() {
        return metadataJsonPath;
    }

    public boolean isUnmatched() {
        return unmatched;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }

    /**
     * @return true when this filter selects a subset of expectations (metadata or unmatched)
     */
    @JsonIgnore
    public boolean hasSelection() {
        return isNotBlank(metadataKey) || isNotBlank(metadataJsonPath) || unmatched;
    }

    /**
     * @return true when this filter pages the result
     */
    @JsonIgnore
    public boolean hasPaging() {
        return limit != null || offset != null;
    }

    /**
     * Add this filter's query string parameters to a control-plane request. Every name and value is added
     * literally: the {@code String} forms parse a leading {@code !} or {@code ?} as a not or optional marker,
     * which the encoder then drops, so {@code "!team-a"} would be sent as {@code team-a} and select other
     * expectations.
     */
    public HttpRequest applyTo(HttpRequest request) {
        if (isNotBlank(metadataKey)) {
            addLiteral(request, METADATA_KEY_PARAMETER, metadataKey);
            if (metadataValue != null) {
                addLiteral(request, METADATA_VALUE_PARAMETER, metadataValue);
            }
        }
        if (isNotBlank(metadataJsonPath)) {
            addLiteral(request, METADATA_JSON_PATH_PARAMETER, metadataJsonPath);
        }
        if (unmatched) {
            addLiteral(request, UNMATCHED_PARAMETER, "true");
        }
        if (limit != null) {
            addLiteral(request, LIMIT_PARAMETER, String.valueOf(limit));
        }
        if (offset != null) {
            addLiteral(request, OFFSET_PARAMETER, String.valueOf(offset));
        }
        return request;
    }

    private static void addLiteral(HttpRequest request, String name, String value) {
        request.withQueryStringParameter(new Parameter(NottableString.string(name, false), NottableString.string(value, false)));
    }

    /**
     * The single entry point for {@code PUT /mockserver/clear} and {@code PUT /mockserver/retrieve}. Whether a
     * request is filtered is decided from the RAW query string the server received, decoded here strictly, not
     * from the already-decoded parameters: the server's decoder drops every parameter when any part of the query
     * cannot be decoded (a bare {@code %}, {@code %ZZ}), which would otherwise read as "no filter" and, on clear,
     * clear everything. So any decoding failure is refused, and a request naming any filter must parse into a
     * complete filter (see {@link #requireComplete(boolean)}).
     *
     * @throws IllegalArgumentException for an undecodable query string or any invalid filter parameter
     */
    public static ExpectationFilter fromControlPlaneRequest(HttpRequest request) {
        if (request.getUndecodableQueryString() != null) {
            // the transport could not decode it, so the request carries no parameters at all: never "no filter"
            strictlyDecode(request.getUndecodableQueryString());
            throw new IllegalArgumentException("the query string \"" + request.getUndecodableQueryString() + "\" cannot be decoded");
        }
        String raw = request.getQueryStringParameters() != null ? request.getQueryStringParameters().getRawParameterString() : null;
        if (raw != null && !raw.isEmpty()) {
            return fromPairs(strictlyDecode(raw));
        }
        return fromQueryStringParameters(request);
    }

    /**
     * Read and validate the filter from a request built in process (no raw query string). Names are matched
     * ignoring case, as for every other control-plane parameter.
     *
     * @throws IllegalArgumentException for an invalid limit, offset, unmatched value or JSONPath, a blank
     *                                  selection, a metadataValue without a metadataKey, a parameter given more
     *                                  than once, an unrecognised metadata or unmatched parameter, or a not,
     *                                  optional or schema matcher in place of a literal
     */
    public static ExpectationFilter fromQueryStringParameters(HttpRequest request) {
        List<String[]> pairs = new ArrayList<>();
        for (Parameter parameter : request.getQueryStringParameterList()) {
            String name = parameter.getName().getValue();
            List<NottableString> values = parameter.getValues();
            if (isFilterLike(name)) {
                // the wire decoder builds literal strings; an in-process not, optional or schema form cannot be read
                // literally, so it is refused rather than read without its marker
                if (!isLiteral(parameter.getName()) || (values != null && values.stream().anyMatch(value -> !isLiteral(value)))) {
                    throw new IllegalArgumentException("\"" + name + "\" must be a literal value, not a not, optional or schema matcher");
                }
            }
            if (values == null || values.isEmpty()) {
                pairs.add(new String[]{name, ""});
            } else {
                for (NottableString value : values) {
                    pairs.add(new String[]{name, value == null ? "" : java.util.Objects.toString(value.getValue(), "")});
                }
            }
        }
        return fromPairs(pairs);
    }

    private static ExpectationFilter fromPairs(List<String[]> pairs) {
        ExpectationFilter filter = new ExpectationFilter();
        Map<String, String> parameters = filterParameters(pairs, filter);
        if (parameters.containsKey(METADATA_KEY_PARAMETER)) {
            filter.metadataKey = notBlank(parameters, METADATA_KEY_PARAMETER);
            if (parameters.containsKey(METADATA_VALUE_PARAMETER)) {
                filter.metadataValue = parameters.get(METADATA_VALUE_PARAMETER);
            }
        } else if (parameters.containsKey(METADATA_VALUE_PARAMETER)) {
            throw new IllegalArgumentException("\"" + METADATA_VALUE_PARAMETER + "\" requires \"" + METADATA_KEY_PARAMETER + "\"");
        }
        if (parameters.containsKey(METADATA_JSON_PATH_PARAMETER)) {
            filter.metadataJsonPath = notBlank(parameters, METADATA_JSON_PATH_PARAMETER);
            filter.compiledJsonPath = compileJsonPath(filter.metadataJsonPath);
        }
        if (parameters.containsKey(UNMATCHED_PARAMETER)) {
            String unmatchedValue = parameters.get(UNMATCHED_PARAMETER);
            if ("true".equalsIgnoreCase(unmatchedValue)) {
                filter.unmatched = true;
            } else if (!"false".equalsIgnoreCase(unmatchedValue)) {
                throw new IllegalArgumentException("\"" + unmatchedValue + "\" is not a valid value for \"" + UNMATCHED_PARAMETER + "\", only true or false are supported");
            }
        }
        filter.limit = nonNegativeInteger(parameters, LIMIT_PARAMETER);
        filter.offset = nonNegativeInteger(parameters, OFFSET_PARAMETER);
        return filter;
    }

    private static final List<String> PARAMETERS = java.util.Arrays.asList(
        METADATA_KEY_PARAMETER, METADATA_VALUE_PARAMETER, METADATA_JSON_PATH_PARAMETER, UNMATCHED_PARAMETER, LIMIT_PARAMETER, OFFSET_PARAMETER
    );

    private static String canonical(String name) {
        return PARAMETERS.stream().filter(known -> known.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private static boolean isFilterLike(String name) {
        if (canonical(name) != null) {
            return true;
        }
        String normalised = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalised.startsWith("metadata") || normalised.startsWith("unmatched");
    }

    /**
     * The filter parameters present, by canonical name, each with its single value ("" when given without one).
     * Fails closed: a name that looks like a filter but is not one (such as {@code metadata_key}) or a filter
     * given twice is refused, because on clear a filter that is not read means "clear everything". Also records
     * on {@code filter} that the request named a filter, and whether it carried a blank namespace.
     */
    private static Map<String, String> filterParameters(List<String[]> pairs, ExpectationFilter filter) {
        Map<String, String> parameters = new java.util.HashMap<>();
        for (String[] pair : pairs) {
            String name = pair[0];
            if ("namespace".equalsIgnoreCase(name) && isBlank(pair[1])) {
                filter.blankNamespaceParameter = true;
            }
            if (!isFilterLike(name)) {
                continue;
            }
            filter.filterParameterPresent = true;
            String canonical = canonical(name);
            if (canonical == null) {
                throw new IllegalArgumentException("\"" + name + "\" is not a recognised expectation filter parameter, the supported parameters are " + PARAMETERS);
            }
            if (parameters.containsKey(canonical)) {
                throw new IllegalArgumentException("\"" + canonical + "\" must be given at most once");
            }
            parameters.put(canonical, pair[1]);
        }
        return parameters;
    }

    /**
     * Decodes a raw query string as the server's decoder does ({@code &} separates, {@code +} is a space,
     * {@code ;} is an ordinary character, a {@code #} ends it), but refuses what that decoder would drop or
     * replace: a {@code %} not followed by two hex digits, and percent-encoded bytes that are not valid UTF-8.
     */
    static List<String[]> strictlyDecode(String raw) {
        int fragment = raw.indexOf('#');
        String query = fragment >= 0 ? raw.substring(0, fragment) : raw;
        List<String[]> pairs = new ArrayList<>();
        for (String part : query.split("&", -1)) {
            if (part.isEmpty()) {
                continue;
            }
            int equals = part.indexOf('=');
            pairs.add(new String[]{
                decodeComponent(equals < 0 ? part : part.substring(0, equals), raw),
                equals < 0 ? "" : decodeComponent(part.substring(equals + 1), raw)
            });
        }
        return pairs;
    }

    private static String decodeComponent(String component, String raw) {
        StringBuilder decoded = new StringBuilder(component.length());
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < component.length(); i++) {
            char c = component.charAt(i);
            if (c == '%') {
                if (i + 2 >= component.length() || Character.digit(component.charAt(i + 1), 16) < 0 || Character.digit(component.charAt(i + 2), 16) < 0) {
                    throw new IllegalArgumentException("the query string \"" + raw + "\" cannot be decoded: a % must be followed by two hex digits (send a literal % as %25)");
                }
                bytes.write(Character.digit(component.charAt(i + 1), 16) * 16 + Character.digit(component.charAt(i + 2), 16));
                i += 2;
                continue;
            }
            flushUtf8(bytes, decoded, raw);
            decoded.append(c == '+' ? ' ' : c);
        }
        flushUtf8(bytes, decoded, raw);
        return decoded.toString();
    }

    private static void flushUtf8(java.io.ByteArrayOutputStream bytes, StringBuilder decoded, String raw) {
        if (bytes.size() == 0) {
            return;
        }
        try {
            decoded.append(java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())));
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IllegalArgumentException("the query string \"" + raw + "\" cannot be decoded: percent-encoded bytes are not valid UTF-8");
        }
        bytes.reset();
    }

    /**
     * A request that names any filter parameter must be complete: on clear, a selection with no paging; on
     * retrieve, a selection or paging. It must never be read as an unfiltered request.
     */
    public ExpectationFilter requireComplete(boolean clear) {
        if (clear && blankNamespaceParameter) {
            throw new IllegalArgumentException("\"namespace\" must not be blank; omit it to clear every namespace");
        }
        if (!filterParameterPresent) {
            return this;
        }
        if (clear) {
            if (hasPaging()) {
                throw new IllegalArgumentException("limit and offset are not supported for clear");
            }
            if (!hasSelection()) {
                throw new IllegalArgumentException("the filter parameters select no expectations to clear (for example unmatched=false on its own); use a metadata filter or unmatched=true, or send no filter parameters to clear without one");
            }
        } else if (!hasSelection() && !hasPaging()) {
            throw new IllegalArgumentException("the filter parameters select nothing (for example unmatched=false on its own); omit them to retrieve without a filter");
        }
        return this;
    }

    /**
     * @return true when the request named any filter parameter, so it must be handled as a filtered request
     */
    @JsonIgnore
    public boolean isFiltered() {
        return filterParameterPresent;
    }

    private static boolean isLiteral(NottableString value) {
        return value == null || (!value.isNot() && !(value instanceof org.mockserver.model.NottableOptionalString) && !(value instanceof org.mockserver.model.NottableSchemaString));
    }

    private static String notBlank(Map<String, String> parameters, String name) {
        String value = parameters.get(name);
        if (isBlank(value)) {
            throw new IllegalArgumentException("\"" + name + "\" must not be blank; omit it to select every expectation");
        }
        return value;
    }


    private static Integer nonNegativeInteger(Map<String, String> parameters, String name) {
        if (!parameters.containsKey(name)) {
            return null;
        }
        String value = parameters.get(name);
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= 0) {
                return parsed;
            }
        } catch (NumberFormatException ignore) {
            // reported below
        }
        throw new IllegalArgumentException("\"" + value + "\" is not a valid value for \"" + name + "\", it must be an integer of zero or more");
    }

    /**
     * @return true when the expectation satisfies every selection in this filter (paging is not a selection)
     */
    public boolean matches(Expectation expectation) {
        if (expectation == null) {
            return false;
        }
        if (unmatched && expectation.getMatchCount() != 0) {
            return false;
        }
        if (isNotBlank(metadataKey) || isNotBlank(metadataJsonPath)) {
            Map<String, Object> metadata = expectation.getMetadata();
            if (metadata == null) {
                return false;
            }
            if (isNotBlank(metadataKey)) {
                if (!metadata.containsKey(metadataKey)) {
                    return false;
                }
                if (metadataValue != null && !valueMatches(metadata.get(metadataKey), metadataValue)) {
                    return false;
                }
            }
            return isBlank(metadataJsonPath) || jsonPathMatches(metadata);
        }
        return true;
    }

    /**
     * @return the page of {@code items} this filter selects, or {@code items} itself when it does not page
     */
    public <T> List<T> page(List<T> items) {
        if (!hasPaging() || items == null) {
            return items;
        }
        int from = Math.min(offset != null ? offset : 0, items.size());
        int to = limit != null ? (int) Math.min((long) from + limit, items.size()) : items.size();
        return new ArrayList<>(items.subList(from, to));
    }

    private static boolean valueMatches(Object stored, String expected) {
        JsonNode storedNode = stored == null ? NullNode.getInstance() : OBJECT_MAPPER.valueToTree(stored);
        JsonNode expectedNode;
        try {
            expectedNode = OBJECT_MAPPER.readTree(expected);
        } catch (Exception notJson) {
            expectedNode = null;
        }
        if (storedNode.isTextual()) {
            return storedNode.asText().equals(expected)
                || (expectedNode != null && expectedNode.isTextual() && storedNode.asText().equals(expectedNode.asText()));
        }
        return expectedNode != null && !expectedNode.isMissingNode() && storedNode.equals(NUMERIC_EQUALITY, expectedNode);
    }

    private boolean jsonPathMatches(Map<String, Object> metadata) {
        if (compiledJsonPath == null) {
            compiledJsonPath = JsonPath.compile(metadataJsonPath);
        }
        try {
            Object result = compiledJsonPath.read(toJson(metadata));
            if (result instanceof Collection) {
                return !((Collection<?>) result).isEmpty();
            }
            return result != null;
        } catch (RuntimeException noMatch) {
            return false;
        }
    }

    private static String toJson(Object value) {
        return MetadataJson.toJson(value);
    }

    @Override
    @JsonIgnore
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"compiledJsonPath", "filterParameterPresent", "blankNamespaceParameter"};
    }
}
