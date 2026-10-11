package org.mockserver.dashboard.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Body;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.LogEntryBody;
import org.mockserver.model.RequestDefinition;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Shortens the bodies the dashboard sends in its live updates. A row carries its exchange's bodies about eight
 * times over, so uncapped bodies make an update hundreds of megabytes; the dashboard loads a full body on demand
 * (GET /mockserver/logEntryBody). Sizes are in characters of the body as the dashboard shows it.
 */
public final class DashboardBodyCap {

    public static final int MAX_BODY_CHARACTERS = 64 * 1024;
    // a message's headers, path and the like, which are not capped
    public static final int MESSAGE_OVERHEAD_CHARACTERS = 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    // Cut text of retained bodies, so a large body is decoded once rather than at every quote in every update.
    // Weak keys compare by identity and go with the body's log entry; soft values give way under heap pressure.
    // A cut is shown only under the rendering variant (redaction settings) it was cut under.
    private static final long CUT_BODIES_MAX_CHARACTERS = 16L * 1024 * 1024;
    private static final Cache<Body<?>, Cut> CUT_BODIES = CacheBuilder.newBuilder()
        .weakKeys()
        .softValues()
        .maximumWeight(CUT_BODIES_MAX_CHARACTERS)
        .weigher((Body<?> body, Cut cut) -> cut.text.text.length())
        .build();

    private DashboardBodyCap() {
    }

    /**
     * Renders one log entry's bodies for the dashboard: a body already cut for an earlier row under the same
     * variant (with redaction on, its redacted rendering) comes back as that cut text without being decoded again.
     */
    public static final class Rendering implements LogEntry.BodyRendering {
        // each rendered body this produced, to the retained body it came from and the variant it was rendered under
        private final Map<Body<?>, Map.Entry<Body<?>, Object>> renderedFrom = new IdentityHashMap<>(4);

        @Override
        public Body<?> render(Body<?> body, Object variant, UnaryOperator<Body<?>> standard) {
            Cut cut = CUT_BODIES.getIfPresent(body);
            if (cut != null && Objects.equals(cut.variant, variant)) {
                return new CutBody(cut.text);
            }
            Body<?> rendered = standard.apply(body);
            if (rendered != body) {
                renderedFrom.put(rendered, new java.util.AbstractMap.SimpleImmutableEntry<>(body, variant));
            }
            return rendered;
        }

        private void cut(Body<?> rendered, BodyText text) {
            Map.Entry<Body<?>, Object> source = renderedFrom.get(rendered);
            if (source != null) {
                CUT_BODIES.put(source.getKey(), new Cut(source.getValue(), text));
            }
        }
    }

    private static final class Cut {
        private final Object variant;
        private final BodyText text;

        private Cut(Object variant, BodyText text) {
            this.variant = variant;
            this.text = text;
        }
    }

    // Stands in for a rendered body whose cut text is already known; replaced by its cut copy when capped.
    private static final class CutBody extends LogEntryBody {
        private final BodyText text;

        private CutBody(BodyText text) {
            super(text.text);
            this.text = text;
        }
    }

    /**
     * What capping one log entry's messages found: each cut copy's full body length, and roughly how many
     * characters the entry adds to an update.
     */
    public static final class Sizes {
        private final Map<Object, Long> originalLengths = new IdentityHashMap<>();
        private final Map<Object, Long> shownLengths = new IdentityHashMap<>();
        private long displayedCharacters;

        void displayed(long characters) {
            displayedCharacters += characters;
        }

        void shown(Object message, long shownCharacters) {
            shownLengths.put(message, shownCharacters);
            displayedCharacters += shownCharacters;
        }

        void truncated(Object capped, long originalLength, long shownCharacters) {
            originalLengths.put(capped, originalLength);
            shown(capped, shownCharacters);
        }

        /**
         * How many characters of a message's body this sends, as measured when it was capped, or {@code null} when
         * the message was not capped here.
         */
        public Long shownLength(Object message) {
            return message == null ? null : shownLengths.get(message);
        }

        /**
         * The full body length of a message this cut short, or {@code null} when it was sent whole.
         */
        public Long originalLength(Object message) {
            return message == null ? null : originalLengths.get(message);
        }

        public long displayedCharacters() {
            return displayedCharacters;
        }

        private long longestOriginalLength() {
            return originalLengths.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        }
    }

    /**
     * A body's text, cut to at most {@code limit} characters, and its full length.
     */
    static final class BodyText {
        final String text;
        final long length;

        private BodyText(String text, long length) {
            this.text = text;
            this.length = length;
        }

        boolean truncated() {
            return length > text.length();
        }
    }

    static BodyText bodyText(Body<?> body, int limit) {
        if (body instanceof CutBody && limit == MAX_BODY_CHARACTERS) {
            return ((CutBody) body).text;
        }
        Object value = body instanceof LogEntryBody ? body.getValue() : null;
        if (value instanceof JsonNode) {
            PrefixWriter writer = new PrefixWriter(limit);
            try {
                JSON.writeValue(writer, value);
            } catch (IOException e) {
                return of(body.toStringWithoutCaching(), limit);
            }
            String shown = writer.length > limit ? prefix(writer.prefix, writer.prefix.length()) : writer.prefix.toString();
            return new BodyText(shown, writer.length);
        }
        return of(value instanceof String ? (String) value : body.toStringWithoutCaching(), limit);
    }

    private static BodyText of(String text, int limit) {
        if (text == null) {
            return new BodyText("", 0);
        }
        return new BodyText(text.length() > limit ? prefix(text, limit) : text, text.length());
    }

    // The first characters of text, never ending on the first half of a surrogate pair.
    private static String prefix(CharSequence text, int limit) {
        int end = limit > 0 && Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit;
        return text.subSequence(0, end).toString();
    }

    /**
     * What the dashboard needs to say a body was cut and to load it whole: the log entry it belongs to,
     * which of the entry's messages it is, and the full and shown lengths.
     */
    public static Map<String, Object> truncationMarker(String logEntryId, Object message, long originalLength, boolean loadable) {
        Map<String, Object> marker = new java.util.LinkedHashMap<>();
        marker.put("logEntryId", logEntryId);
        marker.put("part", message instanceof HttpResponse ? "response" : message instanceof Expectation ? "expectation" : "request");
        marker.put("originalLength", originalLength);
        marker.put("shownLength", (long) MAX_BODY_CHARACTERS);
        marker.put("loadable", loadable && !(message instanceof Expectation));
        return marker;
    }

    static String capText(String text) {
        if (text == null || text.length() <= MAX_BODY_CHARACTERS) {
            return text;
        }
        String shown = prefix(text, MAX_BODY_CHARACTERS);
        return shown + "... (" + (text.length() - shown.length()) + " more characters not shown)";
    }

    /**
     * The request with its body cut to the cap, or the same request when it fits. A cut copy is recorded in
     * {@code sizes} with the body's full length.
     */
    static RequestDefinition cap(RequestDefinition requestDefinition, Sizes sizes, Rendering rendering) {
        if (!(requestDefinition instanceof HttpRequest) || ((HttpRequest) requestDefinition).getBody() == null) {
            return requestDefinition;
        }
        HttpRequest request = (HttpRequest) requestDefinition;
        BodyText text = bodyText(request.getBody(), MAX_BODY_CHARACTERS);
        if (!text.truncated()) {
            sizes.shown(request, text.length);
            return request;
        }
        rendering.cut(request.getBody(), text);
        HttpRequest capped = request.shallowClone().withBody(new LogEntryBody(text.text));
        sizes.truncated(capped, text.length, text.text.length());
        return capped;
    }

    static HttpResponse cap(HttpResponse response, Sizes sizes, Rendering rendering) {
        if (response == null || response.getBody() == null) {
            return response;
        }
        BodyText text = bodyText(response.getBody(), MAX_BODY_CHARACTERS);
        if (!text.truncated()) {
            sizes.shown(response, text.length);
            return response;
        }
        rendering.cut(response.getBody(), text);
        HttpResponse capped = response.shallowClone().withBody(new LogEntryBody(text.text));
        sizes.truncated(capped, text.length, text.text.length());
        return capped;
    }

    /**
     * A log message argument with any body or long text in it cut to the cap.
     */
    static Object capArgument(Object argument, Sizes sizes, Rendering rendering) {
        if (argument instanceof RequestDefinition) {
            sizes.displayed(MESSAGE_OVERHEAD_CHARACTERS);
            return cap((RequestDefinition) argument, sizes, rendering);
        } else if (argument instanceof HttpResponse) {
            sizes.displayed(MESSAGE_OVERHEAD_CHARACTERS);
            return cap((HttpResponse) argument, sizes, rendering);
        } else if (argument instanceof Expectation) {
            Expectation expectation = (Expectation) argument;
            sizes.displayed(MESSAGE_OVERHEAD_CHARACTERS);
            Sizes inner = new Sizes();
            RequestDefinition request = cap(expectation.getHttpRequest(), inner, rendering);
            HttpResponse response = cap(expectation.getHttpResponse(), inner, rendering);
            sizes.displayed(inner.displayedCharacters());
            if (request == expectation.getHttpRequest() && response == expectation.getHttpResponse()) {
                return expectation;
            }
            Expectation capped = expectation.cloneWith(request, response);
            sizes.truncated(capped, inner.longestOriginalLength(), 0);
            return capped;
        } else if (argument instanceof String) {
            String text = (String) argument;
            sizes.displayed(Math.min(text.length(), MAX_BODY_CHARACTERS));
            return capText(text);
        } else {
            sizes.displayed(256);
            return argument;
        }
    }

    /**
     * Cuts, in place, an expectation's serialised tree for the dashboard's expectations section: every {@code body}
     * longer than the cap (a JSON or binary body is measured and cut as its serialised text) and every other string
     * longer than the cap. Returns the longest original length cut, or 0 when nothing was cut. The dashboard loads
     * the whole expectation by id before editing it, so a cut tree is only ever shown.
     */
    public static long capExpectationTree(JsonNode node) {
        long longest = 0;
        if (node instanceof ObjectNode) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                if ("body".equals(name) && child != null && child.isContainerNode()) {
                    PrefixWriter writer = new PrefixWriter(MAX_BODY_CHARACTERS);
                    try {
                        JSON.writeValue(writer, child);
                    } catch (IOException e) {
                        writer.length = 0;
                    }
                    if (writer.length > MAX_BODY_CHARACTERS) {
                        object.put(name, prefix(writer.prefix, writer.prefix.length()));
                        longest = Math.max(longest, writer.length);
                        continue;
                    }
                }
                longest = Math.max(longest, capChild(child, value -> object.put(name, value)));
            }
        } else if (node instanceof ArrayNode) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                int index = i;
                longest = Math.max(longest, capChild(array.get(i), value -> array.set(index, array.textNode(value))));
            }
        }
        return longest;
    }

    private static long capChild(JsonNode child, Consumer<String> replace) {
        if (child != null && child.isTextual() && child.textValue().length() > MAX_BODY_CHARACTERS) {
            replace.accept(prefix(child.textValue(), MAX_BODY_CHARACTERS));
            return child.textValue().length();
        }
        return capExpectationTree(child);
    }

    /**
     * What the dashboard needs to say an expectation in its expectations section was cut, and to load it whole by id.
     */
    public static Map<String, Object> expectationTruncationMarker(String expectationId, long originalLength) {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("expectationId", expectationId);
        marker.put("part", "expectation");
        marker.put("originalLength", originalLength);
        marker.put("shownLength", (long) MAX_BODY_CHARACTERS);
        return marker;
    }

    // Keeps the first characters written and counts the rest without holding them.
    private static final class PrefixWriter extends Writer {
        private final int limit;
        private final StringBuilder prefix = new StringBuilder();
        private long length;

        private PrefixWriter(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(char[] buffer, int offset, int count) {
            int room = (int) Math.max(0, Math.min(count, limit - length));
            if (room > 0) {
                prefix.append(buffer, offset, room);
            }
            length += count;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
