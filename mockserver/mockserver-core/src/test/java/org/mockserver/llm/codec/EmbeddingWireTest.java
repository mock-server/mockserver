package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Test;
import org.mockserver.llm.codec.EmbeddingWire.InvalidEmbeddingRequestException;
import org.mockserver.model.EmbeddingResponse;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;

public class EmbeddingWireTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static JsonNode json(String value) throws Exception {
        return OBJECT_MAPPER.readTree(value);
    }

    @Test
    public void shouldSplitOpenAiStyleInputs() throws Exception {
        assertThat(EmbeddingWire.stringOrArrayInputs(json("\"one\"")), is(Collections.singletonList("one")));
        assertThat(EmbeddingWire.stringOrArrayInputs(json("[\"a\",\"b\",\"c\"]")), is(Arrays.asList("a", "b", "c")));
        assertThat(EmbeddingWire.stringOrArrayInputs(json("[1,2,3]")), is(Collections.singletonList("[1,2,3]")));
        assertThat(EmbeddingWire.stringOrArrayInputs(json("[[1,2],[3]]")), is(Arrays.asList("[1,2]", "[3]")));
        assertThat(EmbeddingWire.stringOrArrayInputs(null), is(Collections.singletonList("")));
    }

    @Test
    public void shouldJoinTextParts() throws Exception {
        assertThat(EmbeddingWire.geminiContentText(json("{\"parts\":[{\"text\":\"a\"},{\"inlineData\":{}},{\"text\":\"b\"}]}")), is("a\nb"));
        assertThat(EmbeddingWire.joinTextParts(json("[{\"type\":\"image_url\",\"text\":\"x\"},{\"type\":\"text\",\"text\":\"y\"}]"), "text"), is("y"));
    }

    @Test
    public void shouldReadLegacyTopLevelInput() {
        assertThat(EmbeddingWire.legacyInputText(request().withBody("{\"input\":\"hi\"}")), is("hi"));
        assertThat(EmbeddingWire.legacyInputText(request().withBody("{\"input\":[\"a\",\"b\"]}")), is("[\"a\",\"b\"]"));
        assertThat(EmbeddingWire.legacyInputText(request().withBody("not json")), is(""));
        assertThat(EmbeddingWire.legacyInputText(null), is(""));
    }

    @Test
    public void shouldPackCohereBinaryTypesMostSignificantBitFirst() {
        double[] vector = {0.5, -0.5, 0.5, -0.5, -0.5, -0.5, -0.5, 0.5, 0.1};
        ArrayNode ubinary = EmbeddingWire.cohereTyped(OBJECT_MAPPER.createArrayNode(), vector, "ubinary");
        assertThat(ubinary.size(), is(2));
        assertThat(ubinary.get(0).asInt(), is(0b10100001));
        assertThat(ubinary.get(1).asInt(), is(0b10000000));
        ArrayNode binary = EmbeddingWire.cohereTyped(OBJECT_MAPPER.createArrayNode(), vector, "binary");
        assertThat(binary.get(0).asInt(), is(0b10100001 - 128));
    }

    @Test
    public void shouldQuantiseCohereIntegerTypes() {
        double[] vector = {1.0, -1.0, 0.0};
        ArrayNode int8 = EmbeddingWire.cohereTyped(OBJECT_MAPPER.createArrayNode(), vector, "int8");
        assertThat(int8.toString(), is("[127,-127,0]"));
        ArrayNode uint8 = EmbeddingWire.cohereTyped(OBJECT_MAPPER.createArrayNode(), vector, "uint8");
        assertThat(uint8.toString(), is("[255,0,128]"));
    }

    @Test
    public void shouldAcceptRequestedDimensionsFromOneToTheLimit() throws Exception {
        assertThat(EmbeddingWire.requestedDimensions(null, "dimensions"), is(nullValue()));
        assertThat(EmbeddingWire.requestedDimensions(json("null"), "dimensions"), is(nullValue()));
        assertThat(EmbeddingWire.requestedDimensions(json("1"), "dimensions"), is(1));
        assertThat(EmbeddingWire.requestedDimensions(json("8192"), "dimensions"), is(EmbeddingWire.MAX_REQUESTED_DIMENSIONS));
        for (String invalid : new String[]{"0", "-1", "8193", "2147483648", "99999999999999999999", "256.0", "1e3", "\"256\"", "true", "[256]"}) {
            InvalidEmbeddingRequestException e = assertThrows(invalid, InvalidEmbeddingRequestException.class,
                () -> EmbeddingWire.requestedDimensions(json(invalid), "dimensions"));
            assertThat(e.getParam(), is("dimensions"));
        }
    }

    @Test
    public void shouldOnlyAcceptAnAllowedDimensionWhenTheProviderListsThem() throws Exception {
        assertThat(EmbeddingWire.requestedDimensions(json("512"), "dimensions", 256, 512, 1024), is(512));
        assertThrows(InvalidEmbeddingRequestException.class, () -> EmbeddingWire.requestedDimensions(json("513"), "dimensions", 256, 512, 1024));
    }

    @Test
    public void shouldRejectOneInputOverTheMaximum() {
        EmbeddingWire.checkInputCount(EmbeddingWire.MAX_INPUTS, EmbeddingWire.MAX_INPUTS, "input");
        InvalidEmbeddingRequestException e = assertThrows(InvalidEmbeddingRequestException.class,
            () -> EmbeddingWire.checkInputCount(EmbeddingWire.MAX_INPUTS + 1, EmbeddingWire.MAX_INPUTS, "input"));
        assertThat(e.getParam(), is("input"));
        assertThat(e.getMessage(), is("input must have at most 2048 entries, got 2049"));
    }

    @Test
    public void shouldRejectOneValueOverTheTotal() {
        assertThat(EmbeddingWire.maxTotalValues(), is(262_144L));
        assertThat(EmbeddingWire.maxTotalBase64Values(), is(1_048_576L));
        EmbeddingWire.checkTotalValues(EmbeddingWire.maxTotalValues(), EmbeddingWire.maxTotalValues());
        assertThrows(InvalidEmbeddingRequestException.class,
            () -> EmbeddingWire.checkTotalValues(EmbeddingWire.maxTotalValues() + 1, EmbeddingWire.maxTotalValues()));

        EmbeddingResponse expectationSets512 = EmbeddingResponse.embedding().withDimensions(512);
        EmbeddingWire.checkTotalValues(expectationSets512, 8192, 1536, 512);
        assertThrows(InvalidEmbeddingRequestException.class, () -> EmbeddingWire.checkTotalValues(expectationSets512, 4, 1536, 513));
        EmbeddingWire.checkTotalValues(EmbeddingResponse.embedding(), 128, 1536, 2048);
        assertThrows(InvalidEmbeddingRequestException.class, () -> EmbeddingWire.checkTotalValues(EmbeddingResponse.embedding(), 129, 1536, 2048));
        EmbeddingWire.checkTotalValues(EmbeddingResponse.embedding(), null, 1536, 170);
        assertThrows(InvalidEmbeddingRequestException.class, () -> EmbeddingWire.checkTotalValues(EmbeddingResponse.embedding(), null, 1536, 171));
    }

    @Test
    public void shouldApplyLimitsOnlyWhileInForceAndRestoreTheOuterOnes() {
        EmbeddingWire.Limits outer = new EmbeddingWire.Limits(1_000, 50);
        EmbeddingWire.Limits inner = new EmbeddingWire.Limits(10, 5);
        long[] seen = EmbeddingWire.withLimits(outer, () -> {
            long innerValues = EmbeddingWire.withLimits(inner, () -> EmbeddingWire.maxTotalValues() * 100 + EmbeddingWire.maxInputCharacters());
            return new long[]{innerValues, EmbeddingWire.maxTotalValues(), EmbeddingWire.maxTotalBase64Values(), EmbeddingWire.maxInputCharacters()};
        });
        assertThat(seen[0], is(1_005L));
        assertThat(seen[1], is(1_000L));
        assertThat(seen[2], is(4_000L));
        assertThat(seen[3], is(50L));
        assertThat(EmbeddingWire.maxTotalValues(), is((long) EmbeddingWire.DEFAULT_MAX_TOTAL_VALUES));
        assertThat(EmbeddingWire.maxInputCharacters(), is(EmbeddingWire.DEFAULT_MAX_INPUT_CHARACTERS));

        assertThrows(IllegalStateException.class, () -> EmbeddingWire.withLimits(inner, () -> {
            throw new IllegalStateException("encode failed");
        }));
        assertThat(EmbeddingWire.maxTotalValues(), is((long) EmbeddingWire.DEFAULT_MAX_TOTAL_VALUES));
    }

    @Test
    public void shouldOnlyLimitTheInputLengthOfVectorsHashedFromTheInput() {
        String atLimit = repeat('a', 5);
        String overLimit = repeat('a', 6);
        EmbeddingResponse deterministic = EmbeddingResponse.embedding().withDeterministicFromInput(true);
        EmbeddingWire.withLimits(new EmbeddingWire.Limits(1_000, 5), () -> {
            assertThat(EmbeddingWire.build(deterministic, atLimit, null, 8).length, is(8));
            InvalidEmbeddingRequestException e = assertThrows(InvalidEmbeddingRequestException.class,
                () -> EmbeddingWire.build(deterministic, overLimit, null, 8));
            assertThat(e.getMessage(), is("an input has 6 characters; the limit is 5. Send shorter inputs"));
            assertThat(EmbeddingWire.build(EmbeddingResponse.embedding(), overLimit, null, 8).length, is(8));
            return null;
        });
    }

    @Test
    public void shouldRejectAnExpectationDimensionBelowOne() {
        for (int dimensions : new int[]{0, -1, Integer.MIN_VALUE}) {
            EmbeddingResponse embedding = EmbeddingResponse.embedding().withDimensions(dimensions);
            assertThrows(IllegalArgumentException.class, () -> EmbeddingWire.dimensions(embedding, null, 1536));
            assertThrows(IllegalArgumentException.class, () -> EmbeddingWire.build(embedding, "x", null, 1536));
            assertThrows(IllegalArgumentException.class, () -> EmbeddingWire.build(embedding.withDeterministicFromInput(true), "x", null, 1536));
        }
    }

    @Test
    public void shouldDigestImageInputsSoTheSameImageGivesTheSameInput() throws Exception {
        String image = EmbeddingWire.imageInput(json("\"data:image/png;base64,iVBORw0KGgo=\""));
        assertThat(image.length(), is(64));
        assertThat(EmbeddingWire.imageInput(json("\"data:image/png;base64,iVBORw0KGgo=\"")), is(image));
        assertThat(EmbeddingWire.imageInput(json("\"data:image/png;base64,iVBORw0KGgp=\"")).equals(image), is(false));
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    @Test
    public void shouldNotOverflowWhenTheExpectationSetsHugeDimensions() {
        EmbeddingResponse huge = EmbeddingResponse.embedding().withDimensions(Integer.MAX_VALUE);
        assertThrows(InvalidEmbeddingRequestException.class, () -> EmbeddingWire.checkTotalValues(huge, null, 1536, 2048L * 5));
        assertThrows(InvalidEmbeddingRequestException.class, () -> EmbeddingWire.vector(huge, "x", null, 1536));
    }

    @Test
    public void shouldKeepDistinctKnownEmbeddingTypesInRequestOrder() throws Exception {
        assertThat(EmbeddingWire.requestedTypes(null, "embedding_types", "float", "int8"), is(Collections.emptyList()));
        assertThat(EmbeddingWire.requestedTypes(json("\"float\""), "embedding_types", "float", "int8"), is(Collections.emptyList()));
        assertThat(EmbeddingWire.requestedTypes(json("[\"int8\",\"float\",\"int8\"]"), "embedding_types", "float", "int8"), is(Arrays.asList("int8", "float")));
        for (String invalid : new String[]{"[\"float\",\"uint8\"]", "[7]", "[null]", "[\"FLOAT\"]"}) {
            InvalidEmbeddingRequestException e = assertThrows(invalid, InvalidEmbeddingRequestException.class,
                () -> EmbeddingWire.requestedTypes(json(invalid), "embedding_types", "float", "int8"));
            assertThat(e.getMessage(), is("embedding_types must be one or more of [float, int8]"));
        }
    }
}
