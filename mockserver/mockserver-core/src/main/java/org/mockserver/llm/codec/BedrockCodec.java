package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ProviderCodec;
import org.mockserver.llm.StreamingFormat;
import org.mockserver.model.*;
import org.mockserver.uuid.UUIDService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Codec for AWS Bedrock Runtime. One {@link Provider#BEDROCK} covers two Bedrock wire APIs,
 * selected per request by {@link #isConverse(HttpRequest)}:
 * <ul>
 *   <li><strong>InvokeModel</strong> ({@code /model/{id}/invoke},
 *       {@code /model/{id}/invoke-with-response-stream}) for Anthropic Claude (version
 *       bedrock-2023-05-31): the response is the plain Anthropic Messages body, so this codec
 *       delegates to {@link AnthropicCodec}. Streaming wraps each Anthropic chunk as an AWS
 *       event-stream {@code chunk} frame whose payload is {@code {"bytes":"<base64(chunkJson)>"}}
 *       ({@link StreamingFormat#AWS_EVENT_STREAM}, encoded by {@link BedrockEventStreamEncoder}).</li>
 *   <li><strong>Converse</strong> ({@code /model/{id}/converse}, {@code /model/{id}/converse-stream}):
 *       the model-agnostic Converse envelope, delegated to {@link BedrockConverseCodec}
 *       ({@link StreamingFormat#AWS_CONVERSE_EVENT_STREAM} for streaming).</li>
 * </ul>
 * The request-less {@link #encode(Completion, String)} / {@link #encodeStreaming(Completion, String, StreamingPhysics)}
 * overloads keep the original InvokeModel behaviour.
 * <p>
 * A non-streaming InvokeModel response also carries its token counts in the
 * {@code X-Amzn-Bedrock-Input-Token-Count} header and, for a completion, the
 * {@code X-Amzn-Bedrock-Output-Token-Count} header, as real Bedrock sends them; boto3-based clients
 * such as langchain-aws read usage only from these headers. A completion whose usage has a cache read
 * or cache write count also carries {@code X-Amzn-Bedrock-Cache-Read-Input-Token-Count} and
 * {@code X-Amzn-Bedrock-Cache-Write-Input-Token-Count}, as Bedrock sends them for the models that support
 * prompt caching. Converse and streaming responses carry none.
 * <p>
 * <strong>SigV4 signing</strong> applies to the runtime client path ({@code BedrockLlmClient}),
 * not to this codec.
 */
public class BedrockCodec implements ProviderCodec {

    public static final String INPUT_TOKEN_COUNT_HEADER = "X-Amzn-Bedrock-Input-Token-Count";
    public static final String OUTPUT_TOKEN_COUNT_HEADER = "X-Amzn-Bedrock-Output-Token-Count";
    public static final String CACHE_READ_INPUT_TOKEN_COUNT_HEADER = "X-Amzn-Bedrock-Cache-Read-Input-Token-Count";
    public static final String CACHE_WRITE_INPUT_TOKEN_COUNT_HEADER = "X-Amzn-Bedrock-Cache-Write-Input-Token-Count";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern CONVERSE_PATH = Pattern.compile("/converse(-stream)?/*$");
    private static final Pattern INVOKE_PATH = Pattern.compile("/invoke(-with-response-stream)?/*$");

    private final AnthropicCodec delegate = new AnthropicCodec(false);
    private final BedrockConverseCodec converse = new BedrockConverseCodec();

    /**
     * Whether this request targets the Converse API rather than InvokeModel. The path decides
     * when it names either API; otherwise (a gateway or custom mock path) a Converse-shaped
     * request body selects Converse. Anything else, including a {@code null} request, is
     * InvokeModel, which preserves the behaviour that predates Converse support.
     */
    public static boolean isConverse(HttpRequest request) {
        if (request == null) {
            return false;
        }
        String path = request.getPath() != null ? request.getPath().getValue() : null;
        if (path != null) {
            String lowerPath = path.toLowerCase(Locale.ROOT);
            if (CONVERSE_PATH.matcher(lowerPath).find()) {
                return true;
            }
            if (INVOKE_PATH.matcher(lowerPath).find()) {
                return false;
            }
        }
        try {
            String body = request.getBodyAsText();
            return body != null && !body.isEmpty() && BedrockConverseCodec.isConverseShaped(OBJECT_MAPPER.readTree(body));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Provider provider() {
        return Provider.BEDROCK;
    }

    @Override
    public String apiVersion() {
        return "bedrock-2023-05-31";
    }

    @Override
    public StreamingFormat streamingFormat() {
        return StreamingFormat.AWS_EVENT_STREAM;
    }

    @Override
    public StreamingFormat streamingFormat(HttpRequest request) {
        return isConverse(request) ? converse.streamingFormat() : streamingFormat();
    }

    @Override
    public HttpResponse encode(Completion completion, String model, HttpRequest request) {
        return isConverse(request) ? converse.encode(completion, model) : encode(completion, model);
    }

    @Override
    public List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics, HttpRequest request) {
        return isConverse(request) ? converse.encodeStreaming(completion, model, physics) : encodeStreaming(completion, model, physics);
    }

    @Override
    public HttpResponse encode(Completion completion, String model) {
        Usage usage = completion != null ? completion.getUsage() : null;
        HttpResponse response = delegate.encode(completion, model)
            .withHeader(INPUT_TOKEN_COUNT_HEADER, tokenCount(usage != null ? usage.getInputTokens() : null))
            .withHeader(OUTPUT_TOKEN_COUNT_HEADER, tokenCount(usage != null ? usage.getOutputTokens() : null));
        if (usage != null && (usage.getCachedInputTokens() != null || usage.getCacheCreationTokens() != null)) {
            response
                .withHeader(CACHE_READ_INPUT_TOKEN_COUNT_HEADER, tokenCount(usage.getCachedInputTokens()))
                .withHeader(CACHE_WRITE_INPUT_TOKEN_COUNT_HEADER, tokenCount(usage.getCacheCreationTokens()));
        }
        return response;
    }

    private static String tokenCount(Integer count) {
        return String.valueOf(count != null ? count : 0);
    }

    /**
     * Encode a streaming completion as event-stream chunks.
     * <p>
     * Delegates to {@link AnthropicCodec#encodeStreaming} to produce the Anthropic
     * SSE events, then transforms each event's {@code data} payload into a form
     * suitable for event-stream binary wrapping. The downstream write handler
     * performs the actual binary encoding via {@link BedrockEventStreamEncoder}.
     * <p>
     * Each event's data becomes one event-stream chunk whose payload is
     * {@code {"bytes":"<base64(data)>"}}.
     */
    @Override
    public List<SseEvent> encodeStreaming(Completion completion, String model, StreamingPhysics physics) {
        // Get the Anthropic-format SSE events (with physics already applied)
        List<SseEvent> anthropicEvents = delegate.encodeStreaming(completion, model, physics);

        // Return events as-is — the data payloads are the raw Anthropic chunk JSON.
        // The HttpSseResponseActionHandler will wrap each chunk in event-stream
        // binary framing (including base64 encoding) when it detects
        // StreamingFormat.AWS_EVENT_STREAM.
        return anthropicEvents;
    }

    @Override
    public ParsedConversation decode(HttpRequest request) {
        return isConverse(request) ? converse.decode(request) : delegate.decode(request);
    }

    private enum EmbeddingFamily { TITAN_V1, TITAN_V2, TITAN_IMAGE, COHERE_V3, COHERE_V4 }

    /** Cohere Embed on Bedrock accepts at most 96 texts or inputs per call, and v4 96 images. */
    private static final int COHERE_MAX_INPUTS = 96;

    /** Cohere Embed v3 on Bedrock accepts one image per call. */
    private static final int COHERE_V3_MAX_IMAGES = 1;

    private static final Pattern MODEL_IN_PATH = Pattern.compile("/model/([^/]+)/");

    /** A request-less embedding encodes one input in the Titan Text Embeddings V2 shape. */
    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, String input) {
        return encodeEmbedding(embedding, input, (String) null);
    }

    /**
     * Encodes one input, in the Cohere Embed shape when {@code model} names a Cohere embed
     * model and otherwise in the Titan shape. See {@link #encodeEmbedding(EmbeddingResponse, HttpRequest, String)}.
     */
    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, String input, String model) {
        EmbeddingFamily family = familyFromModel(model);
        ObjectNode body = EmbeddingWire.object();
        if (family == EmbeddingFamily.COHERE_V3 || family == EmbeddingFamily.COHERE_V4) {
            body.putArray("texts").add(input);
            return encodeCohereEmbedding(embedding, body, family);
        }
        body.put("inputText", input);
        return encodeTitanEmbedding(embedding, body, family != null ? family : EmbeddingFamily.TITAN_V2);
    }

    /**
     * Encodes a Bedrock {@code InvokeModel} embedding response in the shape of the model
     * family. The model id comes from the {@code /model/{modelId}/invoke} path, then the
     * expectation's {@code model}; region prefixes ({@code us.cohere...}) and ARNs match. With no
     * embedding model named, a {@code texts}, {@code inputs} or {@code images} body selects Cohere
     * and anything else Titan.
     * <ul>
     *   <li><strong>Titan Text Embeddings G1</strong> ({@code amazon.titan-embed-text-v1}):
     *       {@code inputText} in; {@code {"embedding":[...],"inputTextTokenCount":N}} out,
     *       1536 dimensions.</li>
     *   <li><strong>Titan Text Embeddings V2</strong> ({@code amazon.titan-embed-text-v2:0}, the
     *       default): adds {@code embeddingsByType} keyed by the requested
     *       {@code embeddingTypes} ({@code float} by default, {@code binary} as 0/1 per
     *       dimension), omits {@code embedding} when only {@code binary} is requested, and
     *       honours {@code dimensions} (256, 512 or 1024); 1024 dimensions.</li>
     *   <li><strong>Titan Multimodal Embeddings G1</strong> ({@code amazon.titan-embed-image-v1}):
     *       {@code inputText} in; {@code {"embedding":[...],"inputTextTokenCount":N}} out, 1024
     *       dimensions or {@code embeddingConfig.outputEmbeddingLength} (256, 384 or 1024).</li>
     *   <li><strong>Cohere Embed v3 / v4</strong> ({@code cohere.embed-english-v3},
     *       {@code cohere.embed-v4:0}): one vector per {@code texts} entry, {@code images}
     *       entry (one on v3, with {@code texts} returned empty and {@code images} describing
     *       the image) or v4 {@code inputs} item (text and {@code image_url} parts), in
     *       {@code {"id":...,"embeddings":[[...]],
     *       "response_type":"embeddings_floats","texts":[...]}}; with {@code embedding_types}
     *       the embeddings are an object keyed by type and {@code response_type} is
     *       {@code embeddings_by_type}. v3 has 1024 dimensions; v4 1536 or
     *       {@code output_dimension} (256, 512, 1024 or 1536). At most 96 texts or inputs;
     *       each data URI image at most 5MB once decoded.</li>
     * </ul>
     * Every shape carries an {@code X-Amzn-Bedrock-Input-Token-Count} header and no output count:
     * Titan's {@code inputTextTokenCount}, or for Cohere the approximate tokens of its texts (images
     * add none).
     * A request the real API rejects on these limits throws
     * {@link EmbeddingWire.InvalidEmbeddingRequestException}.
     */
    @Override
    public HttpResponse encodeEmbedding(EmbeddingResponse embedding, HttpRequest request, String model) {
        JsonNode body = EmbeddingWire.body(request);
        EmbeddingFamily family = familyFromModel(modelFromPath(request));
        if (family == null) {
            family = familyFromModel(model);
        }
        if (family == null) {
            if (body.has("texts") || body.has("inputs") || body.has("images")) {
                family = body.has("inputs") || body.has("output_dimension") ? EmbeddingFamily.COHERE_V4 : EmbeddingFamily.COHERE_V3;
            } else {
                family = EmbeddingFamily.TITAN_V2;
            }
        }
        if (family == EmbeddingFamily.COHERE_V3 || family == EmbeddingFamily.COHERE_V4) {
            return encodeCohereEmbedding(embedding, body, family);
        }
        return encodeTitanEmbedding(embedding, body, family);
    }

    private static String modelFromPath(HttpRequest request) {
        if (request == null || request.getPath() == null || request.getPath().getValue() == null) {
            return null;
        }
        Matcher matcher = MODEL_IN_PATH.matcher(request.getPath().getValue());
        if (!matcher.find()) {
            return null;
        }
        try {
            return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return matcher.group(1);
        }
    }

    private static EmbeddingFamily familyFromModel(String model) {
        if (model == null) {
            return null;
        }
        String id = model.toLowerCase(Locale.ROOT);
        if (id.contains("cohere.embed")) {
            return id.contains("embed-v4") ? EmbeddingFamily.COHERE_V4 : EmbeddingFamily.COHERE_V3;
        }
        if (id.contains("titan-embed-text-v2")) {
            return EmbeddingFamily.TITAN_V2;
        }
        if (id.contains("titan-embed-text-v1") || id.contains("titan-embed-g1-text")) {
            return EmbeddingFamily.TITAN_V1;
        }
        if (id.contains("titan-embed-image")) {
            return EmbeddingFamily.TITAN_IMAGE;
        }
        return null;
    }

    private HttpResponse encodeTitanEmbedding(EmbeddingResponse embedding, JsonNode body, EmbeddingFamily family) {
        JsonNode inputText = body.get("inputText");
        String text = inputText != null && inputText.isTextual() ? inputText.asText() : "";
        int tokens = EmbeddingVectors.approximateTokens(text);
        ObjectNode root = EmbeddingWire.object();
        if (family == EmbeddingFamily.TITAN_V1) {
            EmbeddingWire.addFloats(root.putArray("embedding"), EmbeddingWire.vector(embedding, text, null, 1536));
            root.put("inputTextTokenCount", tokens);
            return withInputTokenCount(EmbeddingWire.json(root, "Bedrock Titan"), tokens);
        }
        if (family == EmbeddingFamily.TITAN_IMAGE) {
            JsonNode config = body.get("embeddingConfig");
            Integer length = config != null
                ? EmbeddingWire.requestedDimensions(config.get("outputEmbeddingLength"), "embeddingConfig.outputEmbeddingLength", 256, 384, 1024)
                : null;
            EmbeddingWire.addFloats(root.putArray("embedding"), EmbeddingWire.vector(embedding, text, length, 1024));
            root.put("inputTextTokenCount", tokens);
            return withInputTokenCount(EmbeddingWire.json(root, "Bedrock Titan"), tokens);
        }
        Integer dimensions = EmbeddingWire.requestedDimensions(body.get("dimensions"), "dimensions", 256, 512, 1024);
        List<String> types = EmbeddingWire.requestedTypes(body.get("embeddingTypes"), "embeddingTypes", "float", "binary");
        boolean floats = types.isEmpty() || types.contains("float");
        EmbeddingWire.checkTotalValues(embedding, dimensions, 1024, (floats ? 2 : 0) + (types.contains("binary") ? 1 : 0));
        double[] vector = EmbeddingWire.vector(embedding, text, dimensions, 1024);
        if (floats) {
            EmbeddingWire.addFloats(root.putArray("embedding"), vector);
        }
        root.put("inputTextTokenCount", tokens);
        ObjectNode byType = root.putObject("embeddingsByType");
        if (types.contains("binary")) {
            EmbeddingWire.titanBinary(byType.putArray("binary"), vector);
        }
        if (floats) {
            EmbeddingWire.addFloats(byType.putArray("float"), vector);
        }
        return withInputTokenCount(EmbeddingWire.json(root, "Bedrock Titan"), tokens);
    }

    private HttpResponse encodeCohereEmbedding(EmbeddingResponse embedding, JsonNode body, EmbeddingFamily family) {
        List<String> inputs = new ArrayList<>();
        long tokens = 0;
        JsonNode texts = body.get("texts");
        JsonNode items = body.get("inputs");
        JsonNode images = body.get("images");
        if (texts != null && texts.isArray() && images != null && images.isArray()) {
            throw new EmbeddingWire.InvalidEmbeddingRequestException("images", "send either texts or images, not both");
        }
        if (texts != null && texts.isArray()) {
            EmbeddingWire.checkInputCount(texts.size(), COHERE_MAX_INPUTS, "texts");
            for (JsonNode text : texts) {
                String input = text.isTextual() ? text.asText() : text.isNull() ? null : text.toString();
                inputs.add(input);
                tokens += EmbeddingVectors.approximateTokens(input);
            }
        } else if (items != null && items.isArray()) {
            EmbeddingWire.checkInputCount(items.size(), COHERE_MAX_INPUTS, "inputs");
            for (JsonNode item : items) {
                inputs.add(cohereInputText(item.get("content")));
                tokens += cohereTextPartTokens(item.get("content"));
            }
        } else if (images != null && images.isArray()) {
            EmbeddingWire.checkInputCount(images.size(), family == EmbeddingFamily.COHERE_V4 ? COHERE_MAX_INPUTS : COHERE_V3_MAX_IMAGES, "images");
            for (JsonNode image : images) {
                if (family == EmbeddingFamily.COHERE_V3) {
                    CohereImageDescription.checkSupported(image);
                }
                CohereImageDescription.checkSize(image, "images");
                inputs.add(EmbeddingWire.imageInput(image));
            }
        }
        Integer requested = family == EmbeddingFamily.COHERE_V4
            ? EmbeddingWire.requestedDimensions(body.get("output_dimension"), "output_dimension", 256, 512, 1024, 1536)
            : null;
        int defaultDimensions = family == EmbeddingFamily.COHERE_V4 ? 1536 : 1024;
        List<String> types = EmbeddingWire.requestedTypes(body.get("embedding_types"), "embedding_types", "float", "int8", "uint8", "binary", "ubinary");
        EmbeddingWire.checkTotalValues(embedding, requested, defaultDimensions, (long) inputs.size() * Math.max(1, types.size()));
        List<double[]> vectors = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            vectors.add(EmbeddingWire.vector(embedding, input, requested, defaultDimensions));
        }

        ObjectNode root = EmbeddingWire.object();
        root.put("id", UUIDService.getNonSecureUUID());
        if (types.isEmpty()) {
            ArrayNode embeddings = root.putArray("embeddings");
            for (double[] vector : vectors) {
                EmbeddingWire.addFloats(embeddings.addArray(), vector);
            }
            root.put("response_type", "embeddings_floats");
        } else {
            ObjectNode embeddings = root.putObject("embeddings");
            for (String type : types) {
                ArrayNode typed = embeddings.putArray(type);
                for (double[] vector : vectors) {
                    EmbeddingWire.cohereTyped(typed.addArray(), vector, type);
                }
            }
            root.put("response_type", "embeddings_by_type");
        }
        if (texts != null && texts.isArray()) {
            root.set("texts", texts.deepCopy());
        } else if (items != null && items.isArray()) {
            root.set("inputs", items.deepCopy());
        } else {
            root.putArray("texts");
            if (family == EmbeddingFamily.COHERE_V3 && images != null && images.isArray()) {
                ArrayNode descriptions = root.putArray("images");
                for (JsonNode image : images) {
                    CohereImageDescription.describe(image).writeTo(descriptions.addObject());
                }
            }
        }
        return withInputTokenCount(EmbeddingWire.json(root, "Bedrock Cohere"), tokens);
    }

    private static HttpResponse withInputTokenCount(HttpResponse response, long tokens) {
        return response.withHeader(INPUT_TOKEN_COUNT_HEADER, String.valueOf(tokens));
    }

    /** The approximate tokens of a v4 {@code inputs} item's text parts; its images add none. */
    private static long cohereTextPartTokens(JsonNode content) {
        long tokens = 0;
        if (content != null && content.isArray()) {
            for (JsonNode part : content) {
                JsonNode partText = part.get("text");
                if ("text".equals(part.path("type").asText("")) && partText != null && partText.isTextual()) {
                    tokens += EmbeddingVectors.approximateTokens(partText.asText());
                }
            }
        }
        return tokens;
    }

    /**
     * The text of a v4 {@code inputs} item: its parts in order, each text part as its text and
     * each {@code image_url} part as the digest of its URL, so its images change its vector.
     */
    private static String cohereInputText(JsonNode content) {
        if (content == null || !content.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content) {
            String type = part.path("type").asText("");
            String value = null;
            if ("text".equals(type)) {
                JsonNode partText = part.get("text");
                value = partText != null && partText.isTextual() ? partText.asText() : null;
            } else if ("image_url".equals(type)) {
                JsonNode imageUrl = part.get("image_url");
                JsonNode url = imageUrl != null && imageUrl.isObject() ? imageUrl.get("url") : imageUrl;
                CohereImageDescription.checkSize(url, "inputs");
                value = EmbeddingWire.imageInput(url);
            }
            if (value != null) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(value);
            }
        }
        return text.toString();
    }
}
