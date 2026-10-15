package org.mockserver.serialization.code;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.CaptureRule;
import org.mockserver.model.CrossProtocolScenario;
import org.mockserver.model.ExpectationStep;
import org.mockserver.model.HttpChaosProfile;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.model.RateLimit;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.FuzzyBody.fuzzy;
import static org.mockserver.model.GraphQLBody.graphQL;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.XmlBody.xml;

/**
 * Pins the code each generator writes, through its String and its Writer variant, to digests recorded from
 * the generators that built each expectation's code, and each body in it, as a String of its own. The
 * fixture holds what each generator escapes or scans for: quotes, backslashes, a backtick (Go's escaped
 * literal), a quote followed by hashes (Rust's raw literal), multi-byte, supplementary and unpaired
 * surrogate characters, every byte value in a binary body and bodies long enough to cross buffers.
 * The Go module path's major suffix is replaced by a placeholder before hashing: each major release
 * rewrites it, and the tests that assert on the import line pin it instead.
 * <p>
 * To re-record (only when an output change is intended), run with
 * {@code -Dmockserver.recordCodeGolden=<path of generated-code-golden.txt in src/test/resources>}.
 */
public class GeneratedCodeGoldenTest {

    private static final String GOLDEN = "/org/mockserver/serialization/code/generated-code-golden.txt";
    private static final String MIXED = "ascii é ñ 中文 😀 quote \" backslash \\ tab \t newline \n crlf \r\n backtick ` hashes \"## end";
    private static final String UNPAIRED = "high \uD83D low \uDE00 end \uD83D";
    private static final Pattern GO_MODULE_MAJOR = Pattern.compile("mockserver-client-go/v[0-9]+");

    private static String longText(int characters) {
        StringBuilder text = new StringBuilder(characters + MIXED.length());
        while (text.length() < characters) {
            text.append(MIXED);
        }
        return text.toString();
    }

    private static byte[] allByteValues(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    static List<Expectation> expectations() {
        HttpResponse textResponse = response(longText(40_000)).withHeader(header("x-unpaired", UNPAIRED)).withReasonPhrase("OK " + MIXED);
        return Arrays.asList(
            new Expectation(request("/plain").withMethod("GET")).withId("plain").thenRespond(response("no quote, backtick or hash")),
            new Expectation(request("/text/中文")
                .withMethod("PUT")
                .withHeader(header("x-mixed", MIXED))
                .withQueryStringParameter("q", UNPAIRED)
                .withBody(longText(50_000)))
                .withId("text")
                .withNamespace("name\"space")
                .withScenarioName("scenario `" + MIXED)
                .thenRespond(textResponse),
            new Expectation(request("/json").withBody(json("{\"text\":\"" + MIXED.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"}", MediaType.JSON_UTF_8)))
                .withId("json")
                .thenRespond(response().withBody(binary(allByteValues(3_000), MediaType.APPLICATION_OCTET_STREAM))),
            new Expectation(request("/binary").withBody(binary(allByteValues(5_000))))
                .withId("binary")
                .thenRespond(response().withStatusCode(201).withBody(json("{\"emoji\":\"😀\",\"tick\":\"`\"}", MediaType.JSON_UTF_8))),
            new Expectation(request("/xml").withBody(xml("<a b=\"`\">" + MIXED + "</a>")))
                .withId("xml")
                .thenRespond(Arrays.asList(response("one"), response(UNPAIRED))),
            new Expectation(request("/params").withBody(params(param("p", MIXED))))
                .withId("params")
                .thenRespond(response("params")),
            new Expectation(request("/regex").withBody(regex(".*\"#" + MIXED)))
                .withId("regex")
                .thenRespond(response("regex")),
            new Expectation(request("/fuzzy").withBody(fuzzy(MIXED, 0.75, true)))
                .withId("fuzzy")
                .thenRespond(response("fuzzy")),
            new Expectation(request("/graphql").withBody(graphQL("query { a(\"`\") }", "op\"#", "{\"type\":\"object\"}")))
                .withId("graphql")
                .thenRespond(response("graphql")),
            new Expectation(request("/extras"))
                .withId("extras")
                .withChaos(HttpChaosProfile.httpChaosProfile().withErrorStatus(503))
                .withRateLimit(RateLimit.rateLimit().withLimit(5))
                .withCapture(CaptureRule.captureRule().withExpression("$.id").withInto("id"))
                .withCrossProtocolScenarios(List.of(CrossProtocolScenario.crossProtocolScenario().withScenarioName("s\"`").withTargetState("t")))
                .withSteps(ExpectationStep.step().withHttpResponse(response(MIXED)))
                .thenRespond(response("extras"))
        );
    }

    private static Map<String, BiConsumer<List<Expectation>, Writer>> writerVariants() {
        ExpectationSerializer json = new ExpectationSerializer(new MockServerLogger());
        Map<String, BiConsumer<List<Expectation>, Writer>> generators = new LinkedHashMap<>();
        generators.put("JAVA", new ExpectationToJavaSerializer()::serialize);
        generators.put("JAVA_RECORDED", new ExpectationToJavaSerializer(false)::serialize);
        generators.put("JAVASCRIPT", new ExpectationToJavaScriptSerializer(json)::serialize);
        generators.put("PYTHON", new ExpectationToPythonSerializer(json)::serialize);
        generators.put("GO", new ExpectationToGoSerializer(json)::serialize);
        generators.put("CSHARP", new ExpectationToCSharpSerializer(json)::serialize);
        generators.put("RUBY", new ExpectationToRubySerializer(json)::serialize);
        generators.put("RUST", new ExpectationToRustSerializer(json)::serialize);
        generators.put("PHP", new ExpectationToPhpSerializer(json)::serialize);
        return generators;
    }

    private static Map<String, Function<List<Expectation>, String>> stringVariants() {
        ExpectationSerializer json = new ExpectationSerializer(new MockServerLogger());
        Map<String, Function<List<Expectation>, String>> generators = new LinkedHashMap<>();
        generators.put("JAVA", new ExpectationToJavaSerializer()::serialize);
        generators.put("JAVA_RECORDED", new ExpectationToJavaSerializer(false)::serialize);
        generators.put("JAVASCRIPT", new ExpectationToJavaScriptSerializer(json)::serialize);
        generators.put("PYTHON", new ExpectationToPythonSerializer(json)::serialize);
        generators.put("GO", new ExpectationToGoSerializer(json)::serialize);
        generators.put("CSHARP", new ExpectationToCSharpSerializer(json)::serialize);
        generators.put("RUBY", new ExpectationToRubySerializer(json)::serialize);
        generators.put("RUST", new ExpectationToRustSerializer(json)::serialize);
        generators.put("PHP", new ExpectationToPhpSerializer(json)::serialize);
        return generators;
    }

    @Test
    public void shouldWriteTheRecordedCodeThroughBothVariants() throws Exception {
        // given
        List<Expectation> expectations = expectations();

        // when
        Map<String, String> actual = new LinkedHashMap<>();
        writerVariants().forEach((format, generator) -> {
            StringWriter writer = new StringWriter();
            generator.accept(expectations, writer);
            actual.put(format + " writer", describe(writer.toString()));
        });
        stringVariants().forEach((format, generator) -> actual.put(format + " string", describe(generator.apply(expectations))));
        // each expectation on its own, so one escaped literal cannot hide another's
        for (Expectation expectation : expectations) {
            writerVariants().forEach((format, generator) -> {
                StringWriter writer = new StringWriter();
                generator.accept(List.of(expectation), writer);
                actual.put(format + " " + expectation.getId(), describe(writer.toString()));
            });
        }

        // then
        String record = System.getProperty("mockserver.recordCodeGolden");
        if (record != null && !record.isEmpty()) {
            StringBuilder golden = new StringBuilder();
            actual.forEach((key, value) -> golden.append(key).append(" ").append(value).append("\n"));
            Files.write(Paths.get(record), golden.toString().getBytes(StandardCharsets.UTF_8));
            fail("recorded " + actual.size() + " digests to " + record + "; run again without mockserver.recordCodeGolden");
        }
        Map<String, String> expected = golden();
        assertThat(actual.keySet().toString(), is(expected.keySet().toString()));
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertThat(entry.getKey(), actual.get(entry.getKey()), is(entry.getValue()));
        }
    }

    private static String describe(String code) {
        try {
            byte[] bytes = GO_MODULE_MAJOR.matcher(code).replaceAll("mockserver-client-go/vN").getBytes(StandardCharsets.UTF_8);
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(String.format("%02x", b));
            }
            return bytes.length + " " + hex;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> golden() throws IOException {
        Map<String, String> golden = new LinkedHashMap<>();
        try (InputStream in = GeneratedCodeGoldenTest.class.getResourceAsStream(GOLDEN)) {
            assertThat(GOLDEN, in, notNullValue());
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isEmpty()) {
                    int value = line.indexOf(' ', line.indexOf(' ') + 1);
                    golden.put(line.substring(0, value), line.substring(value + 1));
                }
            }
        }
        return golden;
    }
}
