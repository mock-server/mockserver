package org.mockserver.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.ByteBuf;
import org.mockserver.serialization.ObjectMapperFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

/**
 * Reads the token usage out of an LLM response as its bytes pass, without holding the response.
 * It follows the stream's framing (Server-Sent Events, newline-delimited or concatenated JSON, AWS
 * event stream) and skims the JSON inside, keeping only the raw value of a few named fields near
 * the top of each document: the usage object, the model and the stop reason.
 * <p>
 * Bounded whatever the stream sends: one captured value of at most {@link #MAX_VALUE_BYTES} bytes
 * (a larger one is dropped), and a merged usage of at most {@value #MAX_USAGE_FIELDS} fields, each a
 * count or an object of at most {@value #MAX_USAGE_DETAIL_FIELDS} counts, under
 * names of at most {@value #MAX_USAGE_NAME_CHARS} characters (under 64 KiB as JSON text), plus
 * Bedrock's invocation metrics, one more object of at most {@value #MAX_USAGE_DETAIL_FIELDS} counts.
 * Not thread-safe; {@link #accept} never throws.
 */
public final class LlmStreamUsageScanner {

    /** The most bytes kept of one captured value; a larger value is dropped, not truncated. */
    public static final int MAX_VALUE_BYTES = 8 * 1024;
    static final int MAX_NAME_BYTES = 32;
    /** The most fields kept in the merged usage; a known field is replaced, a further new one is refused. */
    public static final int MAX_USAGE_FIELDS = 32;
    /** The most whole numbers kept of one object inside the usage, such as a token details breakdown. */
    public static final int MAX_USAGE_DETAIL_FIELDS = 16;
    /** The longest field name kept in the merged usage. */
    public static final int MAX_USAGE_NAME_CHARS = 64;
    /** The longest model name or stop reason kept; a longer one is ignored. */
    public static final int MAX_TEXT_CHARS = 128;
    // a count sent as text is parsed only up to this length
    private static final int MAX_COUNT_TEXT_CHARS = 32;
    // Long.MAX_VALUE has 19 digits
    private static final int MAX_COUNT_WHOLE_DIGITS = 19;
    // an AWS event stream message is at most 16 MiB of payload and 128 KiB of headers
    private static final int MAX_EVENT_STREAM_MESSAGE_BYTES = 16 * 1024 * 1024 + 128 * 1024 + 16;
    private static final byte[] SSE_DATA_FIELD = {'d', 'a', 't', 'a'};

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    private enum Framing {
        UNKNOWN, JSON, SSE, EVENT_STREAM, UNREADABLE
    }

    private enum Name {
        USAGE("usage"),
        USAGE_METADATA("usageMetadata"),
        PROMPT_EVAL_COUNT("prompt_eval_count"),
        EVAL_COUNT("eval_count"),
        // exactly MAX_NAME_BYTES long
        INVOCATION_METRICS("amazon-bedrock-invocationMetrics"),
        BYTES("bytes"),
        MODEL("model"),
        ID("id"),
        MODEL_VERSION("modelVersion"),
        FINISH_REASON("finish_reason"),
        STOP_REASON("stop_reason"),
        STATUS("status"),
        FINISH_REASON_CAMEL("finishReason"),
        DONE_REASON("done_reason"),
        STOP_REASON_CAMEL("stopReason"),
        RESPONSE("response"),
        MESSAGE("message"),
        X_GROQ("x_groq"),
        CHOICES("choices"),
        DELTA("delta"),
        CANDIDATES("candidates");

        private static final Name[] ALL = values();

        private final String text;
        private final byte[] bytes;

        Name(String text) {
            this.text = text;
            this.bytes = text.getBytes(StandardCharsets.US_ASCII);
        }

        static Name of(byte[] buffer, int length) {
            for (Name name : ALL) {
                if (name.bytes.length == length && Arrays.equals(name.bytes, 0, length, buffer, 0, length)) {
                    return name;
                }
            }
            return null;
        }
    }

    private final JsonSkimmer json = new JsonSkimmer(false);
    private JsonSkimmer nestedJson;

    private Framing framing = Framing.UNKNOWN;
    private boolean failed;

    // Server-Sent Events
    private boolean sseLineEmpty = true;
    private boolean sseAfterCarriageReturn;
    private int sseFieldMatched;
    private boolean sseInData;
    private boolean sseSkipLine;

    // AWS event stream
    private final byte[] prelude = new byte[12];
    private int preludeLength;
    private int headerBytesLeft;
    private int payloadBytesLeft;
    private int checksumBytesLeft;

    private ObjectNode usage;
    private ObjectNode invocationMetrics;
    private boolean finalUsageSeen;
    private boolean usageOnEveryChunk;
    private boolean finishReasonSeen;
    private String model;
    private String responseId;
    private String stopReason;
    private int jsonDocuments;

    /**
     * Feed the next bytes of the response. The buffer's reader index is not moved.
     */
    public void accept(ByteBuf chunk) {
        if (failed || chunk == null) {
            return;
        }
        try {
            for (int index = chunk.readerIndex(), end = chunk.writerIndex(); index < end; index++) {
                acceptByte(chunk.getByte(index));
            }
        } catch (RuntimeException e) {
            failed = true;
        }
    }

    /**
     * Feed the next bytes of the response.
     */
    public void accept(byte[] bytes) {
        if (failed || bytes == null) {
            return;
        }
        try {
            for (byte b : bytes) {
                acceptByte(b);
            }
        } catch (RuntimeException e) {
            failed = true;
        }
    }

    /**
     * @return the usage the stream reported, in the provider's own field names and merged across
     * events (a later event's fields replace an earlier one's), or null when it reported none.
     * A Bedrock InvokeModelWithResponseStream stream with no usage object gives the counts of its
     * last chunk's {@code amazon-bedrock-invocationMetrics} instead.
     */
    public JsonNode usage() {
        return usage != null ? usage : invocationMetrics;
    }

    /**
     * @return false when the only usage seen came from an event that opens a response (Anthropic's
     * {@code message_start}), or from chunks that each repeat the counts so far (Gemini) with no
     * finish reason yet, so the stream may have ended before its final counts
     */
    public boolean finalUsageSeen() {
        return finalUsageSeen || (usageOnEveryChunk && finishReasonSeen);
    }

    /**
     * @return true when every chunk carries the counts so far (Gemini), so a stream that completes
     * without a finish reason is still whole and only one that was cut off is missing counts
     */
    public boolean usageOnEveryChunk() {
        return usageOnEveryChunk;
    }

    public String model() {
        return model;
    }

    /**
     * @return the id of the OpenAI Responses API response the stream's events describe
     * ({@code response.id}), or null when none was seen
     */
    public String responseId() {
        return responseId;
    }

    public String stopReason() {
        return stopReason;
    }

    /**
     * @return true when the bytes were a stream of events rather than a single JSON document
     */
    public boolean streamed() {
        return framing == Framing.SSE || framing == Framing.EVENT_STREAM || jsonDocuments > 1;
    }

    private void acceptByte(byte b) {
        switch (framing) {
            case UNKNOWN:
                detectFraming(b);
                break;
            case JSON:
                json.accept(b);
                break;
            case SSE:
                acceptServerSentEventByte(b);
                break;
            case EVENT_STREAM:
                acceptEventStreamByte(b);
                break;
            default:
                break;
        }
    }

    private void detectFraming(byte b) {
        int unsigned = b & 0xFF;
        // leading whitespace and a UTF-8 byte order mark say nothing about the framing
        if (isWhitespace(b) || unsigned == 0xEF || unsigned == 0xBB || unsigned == 0xBF) {
            return;
        }
        if (b == 0) {
            // an AWS event stream message opens with its big-endian length, well under 2^24
            framing = Framing.EVENT_STREAM;
        } else if (b == '{' || b == '[') {
            framing = Framing.JSON;
        } else {
            framing = Framing.SSE;
        }
        acceptByte(b);
    }

    private void acceptServerSentEventByte(byte b) {
        if (b == '\n' && sseAfterCarriageReturn) {
            sseAfterCarriageReturn = false;
            return;
        }
        sseAfterCarriageReturn = b == '\r';
        if (b == '\n' || b == '\r') {
            if (sseLineEmpty) {
                // a blank line ends the event, and with it any JSON left unfinished
                json.reset();
            }
            sseLineEmpty = true;
            sseFieldMatched = 0;
            sseInData = false;
            sseSkipLine = false;
            return;
        }
        sseLineEmpty = false;
        if (sseInData) {
            json.accept(b);
        } else if (!sseSkipLine) {
            if (b == ':' && sseFieldMatched == SSE_DATA_FIELD.length) {
                sseInData = true;
            } else if (sseFieldMatched < SSE_DATA_FIELD.length && b == SSE_DATA_FIELD[sseFieldMatched]) {
                sseFieldMatched++;
            } else {
                sseSkipLine = true;
            }
        }
    }

    private void acceptEventStreamByte(byte b) {
        if (preludeLength < prelude.length) {
            prelude[preludeLength++] = b;
            if (preludeLength == prelude.length) {
                int totalLength = readInt(prelude, 0);
                int headersLength = readInt(prelude, 4);
                if (totalLength < 16 || totalLength > MAX_EVENT_STREAM_MESSAGE_BYTES
                    || headersLength < 0 || headersLength > totalLength - 16) {
                    framing = Framing.UNREADABLE;
                    return;
                }
                headerBytesLeft = headersLength;
                payloadBytesLeft = totalLength - headersLength - 16;
                checksumBytesLeft = 4;
                json.reset();
            }
            return;
        }
        if (headerBytesLeft > 0) {
            headerBytesLeft--;
        } else if (payloadBytesLeft > 0) {
            payloadBytesLeft--;
            json.accept(b);
        } else if (--checksumBytesLeft == 0) {
            preludeLength = 0;
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24
            | (bytes[offset + 1] & 0xFF) << 16
            | (bytes[offset + 2] & 0xFF) << 8
            | (bytes[offset + 3] & 0xFF);
    }

    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }

    private void onValue(Name name, Name parent, byte[] buffer, int length, boolean nested) {
        JsonNode value;
        try {
            value = OBJECT_MAPPER.readTree(buffer, 0, length);
        } catch (Exception e) {
            return;
        }
        if (value == null || value.isNull() || value.isMissingNode()) {
            return;
        }
        switch (name) {
            case USAGE:
                if (value.isObject() && mergeUsage((ObjectNode) value) && parent != Name.MESSAGE) {
                    finalUsageSeen = true;
                }
                break;
            case USAGE_METADATA:
                if (value.isObject() && mergeUsage((ObjectNode) value)) {
                    usageOnEveryChunk = true;
                }
                break;
            case INVOCATION_METRICS:
                // sent only on the last chunk of the stream
                ObjectNode metrics = counts(value);
                if (metrics != null) {
                    invocationMetrics = metrics;
                    finalUsageSeen = true;
                }
                break;
            case PROMPT_EVAL_COUNT:
            case EVAL_COUNT:
                JsonNode count = count(value);
                if (count != null && keep(name.text, count)) {
                    finalUsageSeen = true;
                }
                break;
            case BYTES:
                if (value.isTextual() && !nested) {
                    acceptNested(value.asText());
                }
                break;
            case MODEL:
            case MODEL_VERSION:
                if (isShortText(value)) {
                    model = value.asText();
                }
                break;
            case ID:
                if (isShortText(value)) {
                    responseId = value.asText();
                }
                break;
            default:
                if (isShortText(value)) {
                    stopReason = value.asText();
                    finishReasonSeen |= name == Name.FINISH_REASON_CAMEL;
                }
                break;
        }
    }

    private static boolean isShortText(JsonNode value) {
        return value.isTextual() && !value.asText().isEmpty() && value.asText().length() <= MAX_TEXT_CHARS;
    }

    /**
     * A token count as a whole number, read as the whole-response path reads it ({@code JsonNode.asInt()}):
     * a decimal is truncated and a numeric string is parsed. Anything else is not a count. The size of the
     * whole part is checked from precision and scale before converting, because a short value such as
     * {@code 1e30000000} would otherwise expand to millions of digits on the event loop.
     *
     * @return the count, or null when the value is not one
     */
    private static JsonNode count(JsonNode value) {
        if (value.isIntegralNumber()) {
            return value.canConvertToLong() ? value : null;
        }
        java.math.BigDecimal number;
        if (value.isNumber()) {
            if (!Double.isFinite(value.doubleValue())) {
                return null;
            }
            number = value.decimalValue();
        } else if (value.isTextual() && value.asText().trim().length() <= MAX_COUNT_TEXT_CHARS) {
            try {
                number = new java.math.BigDecimal(value.asText().trim());
            } catch (NumberFormatException notANumber) {
                return null;
            }
        } else {
            return null;
        }
        long wholeDigits = (long) number.precision() - number.scale();
        if (wholeDigits <= 0) {
            return LongNode.valueOf(0);
        }
        if (wholeDigits > MAX_COUNT_WHOLE_DIGITS) {
            return null;
        }
        java.math.BigInteger whole = number.toBigInteger();
        return whole.bitLength() < Long.SIZE ? LongNode.valueOf(whole.longValue()) : null;
    }

    // a field already kept is replaced; a new one is refused once the usage is full
    private boolean keep(String field, JsonNode value) {
        if (field.length() > MAX_USAGE_NAME_CHARS) {
            return false;
        }
        if (usage == null) {
            usage = OBJECT_MAPPER.createObjectNode();
        }
        if (!usage.has(field) && usage.size() >= MAX_USAGE_FIELDS) {
            return false;
        }
        usage.set(field, value);
        return true;
    }

    /**
     * Merge one usage event into the usage kept for the stream. Only counts are kept, as whole numbers,
     * directly or one object down, so the usage kept cannot grow with the stream: a null, text or
     * array field is ignored and leaves the value an earlier event gave.
     *
     * @return whether the event gave any count
     */
    private boolean mergeUsage(ObjectNode update) {
        boolean merged = false;
        Iterator<Map.Entry<String, JsonNode>> fields = update.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode counts = count(field.getValue());
            if (counts == null) {
                counts = counts(field.getValue());
            }
            if (counts != null && keep(field.getKey(), counts)) {
                merged = true;
            }
        }
        return merged;
    }

    // the counts of an object inside the usage, such as prompt_tokens_details, or null when it has none
    private static ObjectNode counts(JsonNode details) {
        if (!details.isObject()) {
            return null;
        }
        ObjectNode counts = OBJECT_MAPPER.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = details.fields();
        while (fields.hasNext() && counts.size() < MAX_USAGE_DETAIL_FIELDS) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode count = count(field.getValue());
            if (count != null && field.getKey().length() <= MAX_USAGE_NAME_CHARS) {
                counts.set(field.getKey(), count);
            }
        }
        return counts.isEmpty() ? null : counts;
    }

    // Bedrock InvokeModelWithResponseStream wraps each model event as base64 in {"bytes": "..."}
    private void acceptNested(String base64) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (nestedJson == null) {
            nestedJson = new JsonSkimmer(true);
        }
        nestedJson.reset();
        for (byte b : decoded) {
            nestedJson.accept(b);
        }
    }

    /**
     * Skims JSON one byte at a time, tracking only its depth and the current field name, and
     * capturing the raw value of a wanted field. Malformed JSON cannot make it fail.
     */
    private final class JsonSkimmer {

        private final boolean nested;
        private final byte[] nameBuffer = new byte[MAX_NAME_BYTES];
        private int nameLength;
        private int objectDepth;
        private int arrayDepth;
        private boolean inString;
        private boolean escaped;
        private boolean afterString;
        private Name topLevelName;

        private Name armedName;
        private Name armedParent;
        private boolean capturing;
        private boolean capturingString;
        private boolean capturingBareValue;
        private boolean captureDropped;
        private int captureBaseDepth;
        private byte[] captureBuffer = new byte[256];
        private int captureLength;

        JsonSkimmer(boolean nested) {
            this.nested = nested;
        }

        void reset() {
            nameLength = 0;
            objectDepth = 0;
            arrayDepth = 0;
            inString = false;
            escaped = false;
            afterString = false;
            topLevelName = null;
            armedName = null;
            capturing = false;
            capturingString = false;
            capturingBareValue = false;
        }

        void accept(byte b) {
            if (capturingBareValue) {
                if (!endsBareValue(b)) {
                    capture(b);
                    return;
                }
                capturingBareValue = false;
                deliver();
            }
            if (capturing) {
                capture(b);
            }
            if (inString) {
                acceptStringByte(b);
                return;
            }
            if (afterString) {
                if (isWhitespace(b)) {
                    return;
                }
                afterString = false;
                if (b == ':') {
                    if (!capturing) {
                        fieldNameRead();
                    }
                    return;
                }
            }
            if (armedName != null && !capturing) {
                if (isWhitespace(b)) {
                    return;
                }
                if (endsBareValue(b)) {
                    armedName = null;
                } else {
                    startCapture(b);
                    if (b != '{' && b != '[') {
                        return;
                    }
                }
            }
            switch (b) {
                case '"':
                    inString = true;
                    escaped = false;
                    nameLength = 0;
                    break;
                case '{':
                    objectDepth++;
                    break;
                case '[':
                    arrayDepth++;
                    break;
                case '}':
                    if (objectDepth > 0) {
                        objectDepth--;
                    }
                    valueClosed(true);
                    break;
                case ']':
                    if (arrayDepth > 0) {
                        arrayDepth--;
                    }
                    valueClosed(false);
                    break;
                default:
                    break;
            }
        }

        private void acceptStringByte(byte b) {
            if (escaped) {
                escaped = false;
            } else if (b == '\\') {
                escaped = true;
                // no wanted field name needs an escape
                nameLength = -1;
            } else if (b == '"') {
                inString = false;
                if (capturingString) {
                    capturingString = false;
                    deliver();
                } else {
                    afterString = true;
                }
            } else if (nameLength >= 0) {
                if (nameLength < nameBuffer.length) {
                    nameBuffer[nameLength++] = b;
                } else {
                    nameLength = -1;
                }
            }
        }

        private void fieldNameRead() {
            if (objectDepth < 1 || objectDepth > 2) {
                return;
            }
            Name name = nameLength > 0 ? Name.of(nameBuffer, nameLength) : null;
            Name parent = null;
            if (objectDepth == 1) {
                topLevelName = name;
            } else {
                parent = topLevelName;
            }
            if (name != null && wanted(name, parent)) {
                armedName = name;
                armedParent = parent;
            }
        }

        // a wanted field is at the top of a document, or one object down inside a known field;
        // arrays do not count as depth, so choices[0].finish_reason is one object down
        private boolean wanted(Name name, Name parent) {
            boolean top = objectDepth == 1;
            switch (name) {
                case USAGE:
                    // under delta: Cohere v2's message-end event
                    return top || parent == Name.RESPONSE || parent == Name.MESSAGE || parent == Name.X_GROQ || parent == Name.DELTA;
                case MODEL:
                    return top || parent == Name.RESPONSE || parent == Name.MESSAGE;
                case ID:
                    return parent == Name.RESPONSE;
                case USAGE_METADATA:
                case PROMPT_EVAL_COUNT:
                case EVAL_COUNT:
                case MODEL_VERSION:
                case DONE_REASON:
                case STOP_REASON_CAMEL:
                    return top;
                case BYTES:
                    return top && !nested && framing == Framing.EVENT_STREAM;
                case INVOCATION_METRICS:
                    return top && nested;
                case FINISH_REASON:
                    return parent == Name.CHOICES || parent == Name.DELTA;
                case STOP_REASON:
                    return parent == Name.DELTA;
                case STATUS:
                    return parent == Name.RESPONSE;
                case FINISH_REASON_CAMEL:
                    return parent == Name.CANDIDATES;
                default:
                    return false;
            }
        }

        private void startCapture(byte first) {
            capturing = true;
            captureDropped = false;
            captureLength = 0;
            capture(first);
            if (first == '"') {
                capturingString = true;
                inString = true;
                escaped = false;
                nameLength = -1;
            } else if (first == '{' || first == '[') {
                captureBaseDepth = objectDepth + arrayDepth;
            } else {
                capturingBareValue = true;
            }
        }

        private void capture(byte b) {
            if (captureDropped) {
                return;
            }
            if (captureLength == captureBuffer.length) {
                if (captureLength >= MAX_VALUE_BYTES) {
                    captureDropped = true;
                    return;
                }
                captureBuffer = Arrays.copyOf(captureBuffer, Math.min(MAX_VALUE_BYTES, captureLength * 2));
            }
            captureBuffer[captureLength++] = b;
        }

        private void valueClosed(boolean object) {
            if (capturing && !capturingString && !capturingBareValue && objectDepth + arrayDepth == captureBaseDepth) {
                deliver();
            }
            // an object at the top, or directly inside a top-level array, is one document of the stream
            if (object && objectDepth == 0 && arrayDepth <= 1 && !nested && jsonDocuments < 2) {
                jsonDocuments++;
            }
        }

        private void deliver() {
            Name name = armedName;
            capturing = false;
            armedName = null;
            if (!captureDropped && name != null) {
                onValue(name, armedParent, captureBuffer, captureLength, nested);
            }
        }

        private boolean endsBareValue(byte b) {
            return b == ',' || b == '}' || b == ']' || isWhitespace(b);
        }
    }
}
