package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Structural comparison of a real provider response with MockServer's response, for
 * {@link LlmProviderContractTest}.
 * <p>
 * A JSON document is reduced to its shape: every field path (array elements merged under
 * {@code []}, so {@code choices[].message.content}) mapped to the JSON types seen there. Values are
 * ignored, which normalises ids, timestamps, token counts and generated text, except for the
 * discriminator fields in {@link #DISCRIMINATORS}, whose values are compared because clients
 * branch on them. {@code null} is compatible with every type, because a provider schema that
 * allows null allows the value too; the field must still be present on both sides.
 */
final class ProviderContractShape {

    static final Set<String> DISCRIMINATORS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "type", "object", "role", "status", "finish_reason", "stop_reason", "finishReason", "stopReason", "done", "done_reason")));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    enum Kind {
        /** in the real response, not in MockServer's */
        MISSING,
        /** in MockServer's response, not in the real one */
        EXTRA,
        /** present on both sides with different JSON types */
        TYPE,
        /** a discriminator field with different values */
        VALUE,
        /** the stream's event order differs */
        SEQUENCE,
        /** MockServer streams in a different wire format */
        FORMAT
    }

    static final class Difference {
        final Kind kind;
        final String scope;
        final String path;
        final String detail;

        Difference(Kind kind, String scope, String path, String detail) {
            this.kind = kind;
            this.scope = scope;
            this.path = path;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return kind + " " + scope + " " + path + (detail.isEmpty() ? "" : " (" + detail + ")");
        }
    }

    /** One event of a stream: its SSE event name (or null) and its payload. */
    static final class Event {
        final String name;
        final String data;
        final JsonNode json;

        Event(String name, String data) {
            this.name = name == null || name.isEmpty() ? null : name;
            this.data = data;
            this.json = parseOrNull(data);
        }

        Event(String name, JsonNode json) {
            this.name = name == null || name.isEmpty() ? null : name;
            this.data = json.toString();
            this.json = json;
        }

        /** Event name, else {@code <data>} for a JSON payload, else the literal payload (e.g. {@code [DONE]}). */
        String signature() {
            if (name != null) {
                return name;
            }
            return json != null ? "<data>" : data;
        }
    }

    private ProviderContractShape() {
    }

    static Map<String, Set<String>> shape(JsonNode root) {
        Map<String, Set<String>> shape = new TreeMap<>();
        collect(root, "", shape, null);
        return shape;
    }

    static Map<String, Set<String>> discriminatorValues(JsonNode root) {
        Map<String, Set<String>> values = new TreeMap<>();
        collect(root, "", new TreeMap<>(), values);
        return values;
    }

    private static void collect(JsonNode node, String path, Map<String, Set<String>> shape, Map<String, Set<String>> values) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                String child = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                shape.computeIfAbsent(child, k -> new TreeSet<>()).add(typeOf(field.getValue()));
                if (values != null && DISCRIMINATORS.contains(field.getKey()) && field.getValue().isValueNode() && !field.getValue().isNull()) {
                    values.computeIfAbsent(child, k -> new TreeSet<>()).add(field.getValue().asText());
                }
                collect(field.getValue(), child, shape, values);
            });
        } else if (node.isArray()) {
            String child = path + "[]";
            for (JsonNode element : node) {
                shape.computeIfAbsent(child, k -> new TreeSet<>()).add(typeOf(element));
                collect(element, child, shape, values);
            }
        }
    }

    private static String typeOf(JsonNode node) {
        if (node.isNull()) {
            return "null";
        } else if (node.isNumber()) {
            return "number";
        } else if (node.isTextual()) {
            return "string";
        } else if (node.isBoolean()) {
            return "boolean";
        } else if (node.isArray()) {
            return "array";
        }
        return "object";
    }

    /**
     * Compares one real document (or the union of one stream event's payloads) with MockServer's.
     * Only the topmost missing or extra path is reported, so a missing object is one difference,
     * not one per field. With {@code partial} (an example that elides some of the response) the
     * real discriminator values need only be a subset of MockServer's.
     */
    static List<Difference> compare(String scope, List<JsonNode> real, List<JsonNode> mock, boolean partial) {
        Map<String, Set<String>> realShape = new TreeMap<>();
        Map<String, Set<String>> mockShape = new TreeMap<>();
        Map<String, Set<String>> realValues = new TreeMap<>();
        Map<String, Set<String>> mockValues = new TreeMap<>();
        real.forEach(node -> merge(realShape, realValues, node));
        mock.forEach(node -> merge(mockShape, mockValues, node));

        List<Difference> differences = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : realShape.entrySet()) {
            String path = entry.getKey();
            Set<String> mockTypes = mockShape.get(path);
            if (mockTypes == null) {
                if (!hasAncestorIn(path, realShape, mockShape)) {
                    differences.add(new Difference(Kind.MISSING, scope, path, ""));
                }
            } else if (!typesCompatible(entry.getValue(), mockTypes)) {
                differences.add(new Difference(Kind.TYPE, scope, path, "real " + entry.getValue() + ", mock " + mockTypes));
            }
        }
        for (String path : mockShape.keySet()) {
            if (!realShape.containsKey(path) && !hasAncestorIn(path, mockShape, realShape)) {
                differences.add(new Difference(Kind.EXTRA, scope, path, ""));
            }
        }
        for (Map.Entry<String, Set<String>> entry : realValues.entrySet()) {
            Set<String> mockSet = mockValues.getOrDefault(entry.getKey(), Collections.emptySet());
            boolean matches = partial ? mockSet.containsAll(entry.getValue()) : mockSet.equals(entry.getValue());
            if (!matches && mockShape.containsKey(entry.getKey())) {
                differences.add(new Difference(Kind.VALUE, scope, entry.getKey(), "real " + entry.getValue() + ", mock " + mockSet));
            }
        }
        return differences;
    }

    private static void merge(Map<String, Set<String>> shape, Map<String, Set<String>> values, JsonNode node) {
        shape(node).forEach((path, types) -> shape.computeIfAbsent(path, k -> new TreeSet<>()).addAll(types));
        discriminatorValues(node).forEach((path, set) -> values.computeIfAbsent(path, k -> new TreeSet<>()).addAll(set));
    }

    /** True when an ancestor of {@code path} is also absent from {@code other}, so only the ancestor is reported. */
    private static boolean hasAncestorIn(String path, Map<String, Set<String>> own, Map<String, Set<String>> other) {
        for (String ancestor : ancestors(path)) {
            if (own.containsKey(ancestor) && !other.containsKey(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> ancestors(String path) {
        List<String> ancestors = new ArrayList<>();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '.' || (c == '[' && i > 0)) {
                ancestors.add(path.substring(0, i));
            }
        }
        return ancestors;
    }

    private static boolean typesCompatible(Set<String> realTypes, Set<String> mockTypes) {
        Set<String> real = new TreeSet<>(realTypes);
        Set<String> mock = new TreeSet<>(mockTypes);
        real.remove("null");
        mock.remove("null");
        return real.isEmpty() || mock.isEmpty() || real.equals(mock);
    }

    /**
     * Compares two streams: the order of event signatures with consecutive repeats collapsed
     * (equal, or for a partial example a subsequence of MockServer's), then each signature's
     * merged payload shape.
     */
    static List<Difference> compareStreams(List<Event> real, List<Event> mock, boolean partial) {
        List<Difference> differences = new ArrayList<>();
        List<String> realOrder = collapse(real);
        List<String> mockOrder = collapse(mock);
        boolean orderMatches = partial ? isSubsequence(realOrder, mockOrder) : realOrder.equals(mockOrder);
        if (!orderMatches) {
            differences.add(new Difference(Kind.SEQUENCE, "stream", "events", "real " + realOrder + ", mock " + mockOrder));
        }
        Set<String> signatures = new LinkedHashSet<>();
        real.forEach(e -> signatures.add(e.signature()));
        mock.forEach(e -> signatures.add(e.signature()));
        for (String signature : signatures) {
            List<JsonNode> realPayloads = payloads(real, signature);
            List<JsonNode> mockPayloads = payloads(mock, signature);
            if (!realPayloads.isEmpty() && !mockPayloads.isEmpty()) {
                differences.addAll(compare(signature, realPayloads, mockPayloads, partial));
            }
        }
        return differences;
    }

    private static List<JsonNode> payloads(List<Event> events, String signature) {
        List<JsonNode> payloads = new ArrayList<>();
        for (Event event : events) {
            if (event.signature().equals(signature) && event.json != null) {
                payloads.add(event.json);
            }
        }
        return payloads;
    }

    static List<String> collapse(List<Event> events) {
        List<String> order = new ArrayList<>();
        for (Event event : events) {
            String signature = event.signature();
            if (order.isEmpty() || !order.get(order.size() - 1).equals(signature)) {
                order.add(signature);
            }
        }
        return order;
    }

    private static boolean isSubsequence(List<String> sub, List<String> full) {
        int i = 0;
        for (String item : full) {
            if (i < sub.size() && sub.get(i).equals(item)) {
                i++;
            }
        }
        return i == sub.size();
    }

    /**
     * Parses a {@code text/event-stream} body as the WHATWG rules do: lines end at CRLF, CR or LF,
     * a blank line dispatches the event, a line starting with {@code :} is a comment, and one
     * space after the field colon is dropped.
     */
    static List<Event> parseSse(String body) {
        List<Event> events = new ArrayList<>();
        String name = null;
        StringBuilder data = null;
        for (String line : body.split("\r\n|\r|\n", -1)) {
            if (line.isEmpty()) {
                if (data != null) {
                    events.add(new Event(name, data.toString()));
                }
                name = null;
                data = null;
                continue;
            }
            if (line.startsWith(":")) {
                continue;
            }
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            if ("event".equals(field)) {
                name = value;
            } else if ("data".equals(field)) {
                data = data == null ? new StringBuilder(value) : data.append('\n').append(value);
            }
        }
        if (data != null) {
            events.add(new Event(name, data.toString()));
        }
        return events;
    }

    private static JsonNode parseOrNull(String data) {
        if (data == null) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(data);
            return node != null && node.isContainerNode() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }
}
