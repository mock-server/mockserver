package org.mockserver.serialization.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Behavioural tests for the JavaScript and Python expectation code generators.
 * Each generator embeds the expectation's existing JSON serialization (the same
 * produced by {@code format=JSON}) in a copy-paste-ready client call.
 *
 * @author jamesdbloom
 */
public class ExpectationToCodeSerializerTest {

    private final ExpectationSerializer expectationSerializer =
        new ExpectationSerializer(new MockServerLogger(), true);
    private final ExpectationToJavaScriptSerializer javaScriptSerializer =
        new ExpectationToJavaScriptSerializer(expectationSerializer);
    private final ExpectationToPythonSerializer pythonSerializer =
        new ExpectationToPythonSerializer(expectationSerializer);
    private final ExpectationToGoSerializer goSerializer =
        new ExpectationToGoSerializer(expectationSerializer);
    private final ExpectationToCSharpSerializer cSharpSerializer =
        new ExpectationToCSharpSerializer(expectationSerializer);
    private final ExpectationToRubySerializer rubySerializer =
        new ExpectationToRubySerializer(expectationSerializer);
    private final ExpectationToRustSerializer rustSerializer =
        new ExpectationToRustSerializer(expectationSerializer);
    private final ExpectationToPhpSerializer phpSerializer =
        new ExpectationToPhpSerializer(expectationSerializer);
    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    private Expectation sampleExpectation(String path, String body) {
        return new Expectation(request().withMethod("GET").withPath(path))
            .thenRespond(response().withStatusCode(200).withBody(body));
    }

    @Test
    public void shouldGenerateJavaScriptWithRequirePreambleAndMockAnyResponseCall() {
        String code = javaScriptSerializer.serialize(Collections.singletonList(sampleExpectation("/somePath", "someBody")));

        assertThat(code, containsString("const { mockServerClient } = require('mockserver-client');"));
        assertThat(code, containsString("mockServerClient(\"localhost\", 1080).mockAnyResponse("));
        assertThat(code, containsString("\"/somePath\""));
        assertThat(code, containsString("\"someBody\""));
        assertThat(code, containsString(");"));
    }

    @Test
    public void shouldGenerateOneMockAnyResponseCallPerExpectationInJavaScript() {
        String code = javaScriptSerializer.serialize(Arrays.asList(
            sampleExpectation("/first", "one"),
            sampleExpectation("/second", "two")
        ));

        int calls = code.split("mockAnyResponse\\(", -1).length - 1;
        assertThat(calls, is(2));
        // require preamble emitted exactly once
        assertThat(code.split("require\\('mockserver-client'\\)", -1).length - 1, is(1));
        assertThat(code, containsString("\"/first\""));
        assertThat(code, containsString("\"/second\""));
    }

    @Test
    public void shouldGeneratePythonWithImportPreambleAndUpsertCall() {
        String code = pythonSerializer.serialize(Collections.singletonList(sampleExpectation("/somePath", "someBody")));

        assertThat(code, containsString("import json"));
        assertThat(code, containsString("from mockserver import MockServerClient, Expectation"));
        assertThat(code, containsString("client = MockServerClient(\"localhost\", 1080)"));
        assertThat(code, containsString("client.upsert(Expectation.from_dict(json.loads(\"\"\""));
        assertThat(code, containsString("\"/somePath\""));
    }

    @Test
    public void shouldGenerateOneUpsertCallPerExpectationInPython() {
        String code = pythonSerializer.serialize(Arrays.asList(
            sampleExpectation("/first", "one"),
            sampleExpectation("/second", "two")
        ));

        int calls = code.split("client\\.upsert\\(", -1).length - 1;
        assertThat(calls, is(2));
        // import preamble emitted exactly once
        assertThat(code.split("import json", -1).length - 1, is(1));
    }

    @Test
    public void shouldEmbedValidParseableExpectationJsonInJavaScript() throws Exception {
        Expectation expectation = sampleExpectation("/roundtrip", "body");
        String code = javaScriptSerializer.serialize(Collections.singletonList(expectation));

        String embedded = code.substring(
            code.indexOf("mockAnyResponse(") + "mockAnyResponse(".length(),
            code.lastIndexOf(");")
        ).trim();

        // round-trip sanity: the embedded JSON parses and is a valid expectation
        assertThat(objectMapper.readTree(embedded).get("httpRequest").get("path").asText(), is("/roundtrip"));
        Expectation[] parsed = expectationSerializer.deserializeArray("[" + embedded + "]", true);
        assertThat(parsed.length, is(1));
        assertThat(((org.mockserver.model.HttpRequest) parsed[0].getHttpRequest()).getPath().getValue(), is("/roundtrip"));
    }

    @Test
    public void shouldEmbedValidParseableExpectationJsonInPython() throws Exception {
        Expectation expectation = sampleExpectation("/roundtrip", "body");
        String code = pythonSerializer.serialize(Collections.singletonList(expectation));

        String start = "json.loads(\"\"\"";
        String embedded = code.substring(
            code.indexOf(start) + start.length(),
            code.indexOf("\"\"\")))")
        ).trim();

        assertThat(objectMapper.readTree(embedded).get("httpRequest").get("path").asText(), is("/roundtrip"));
    }

    @Test
    public void shouldRoundTripExpectationWithHostileSpecialCharactersInJavaScriptAndPython() throws Exception {
        // an expectation whose path and body carry every embedding hazard: a backslash, an embedded
        // double-quote, a newline, and a non-ASCII/unicode character
        String hostilePath = "/a\\b\"c\ndé中";
        String hostileBody = "body\\with\"quote\nnewlineé中\"\"\"triple";
        Expectation expectation = new Expectation(request().withMethod("GET").withPath(hostilePath))
            .thenRespond(response().withStatusCode(200).withBody(hostileBody));

        // the canonical JSON serialization is what both generators embed verbatim
        String expectedJson = expectationSerializer.serialize(expectation);

        // JavaScript: the embedded text between mockAnyResponse( and the trailing ); must equal the JSON
        String js = javaScriptSerializer.serialize(Collections.singletonList(expectation));
        String jsEmbedded = js.substring(
            js.indexOf("mockAnyResponse(") + "mockAnyResponse(".length(),
            js.lastIndexOf(");")
        );
        assertThat(jsEmbedded, is(expectedJson));
        assertThat(objectMapper.readTree(jsEmbedded), is(objectMapper.readTree(expectedJson)));

        // Python: the embedded text between the """ open and the """))) close must equal the JSON
        String py = pythonSerializer.serialize(Collections.singletonList(expectation));
        String pyStart = "json.loads(\"\"\"";
        String pyEmbedded = py.substring(
            py.indexOf(pyStart) + pyStart.length(),
            py.indexOf("\"\"\")))")
        );
        assertThat(pyEmbedded, is(expectedJson));
        assertThat(objectMapper.readTree(pyEmbedded), is(objectMapper.readTree(expectedJson)));
    }

    @Test
    public void shouldHandleEmptyAndNullExpectationLists() {
        String js = javaScriptSerializer.serialize(Collections.emptyList());
        assertThat(js, containsString("require('mockserver-client')"));
        assertThat(js.contains("mockAnyResponse("), is(false));

        String py = pythonSerializer.serialize(null);
        assertThat(py, containsString("from mockserver import MockServerClient, Expectation"));
        assertThat(py.contains("client.upsert("), is(false));
    }

    // an expectation whose path and body carry every embedding hazard, including each
    // language's own raw/literal string terminators: a backslash, an embedded double-quote,
    // a newline, a non-ASCII unicode character, a backtick (Go raw literal), the heredoc
    // delimiter token JSON (Ruby/PHP), and the sequence quote-hash (Rust raw literal r#"..."#)
    private Expectation hostileExpectation() {
        String hostilePath = "/a\\b\"c\ndé中`JSON\"#x";
        String hostileBody = "body\\with\"quote\nnewlineé中`\"\"\"triple\nJSON\n\"##end";
        return new Expectation(request().withMethod("GET").withPath(hostilePath))
            .thenRespond(response().withStatusCode(200).withBody(hostileBody));
    }

    @Test
    public void shouldGenerateGoWithImportPreambleAndUpsertCall() {
        String code = goSerializer.serialize(Collections.singletonList(sampleExpectation("/somePath", "someBody")));

        assertThat(code, containsString("package main"));
        assertThat(code, containsString("mockserver \"github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9\""));
        assertThat(code, containsString("client := mockserver.New(\"localhost\", 1080)"));
        assertThat(code, containsString("json.Unmarshal([]byte("));
        assertThat(code, containsString("client.Upsert(e)"));
        assertThat(code, containsString("/somePath"));
    }

    @Test
    public void shouldGenerateGoOneUpsertPerExpectationAndSinglePreamble() {
        String code = goSerializer.serialize(Arrays.asList(sampleExpectation("/first", "one"), sampleExpectation("/second", "two")));
        assertThat(code.split("client\\.Upsert\\(e\\)", -1).length - 1, is(2));
        assertThat(code.split("package main", -1).length - 1, is(1));
    }

    @Test
    public void shouldEmbedHostileExpectationCorrectlyInGo() throws Exception {
        Expectation expectation = hostileExpectation();
        String expectedJson = expectationSerializer.serialize(expectation);
        String code = goSerializer.serialize(Collections.singletonList(expectation));

        // because the JSON contains a backtick, the generator must fall back to a
        // double-quoted interpreted Go string, not a raw literal
        String start = "json.Unmarshal([]byte(";
        String end = "), &e)";
        String literal = code.substring(code.indexOf(start) + start.length(), code.indexOf(end)).trim();
        assertThat(literal.charAt(0), is('"'));
        // un-escape the Go interpreted string and confirm it equals the JSON bytes
        String decoded = decodeGoInterpretedString(literal);
        assertThat(objectMapper.readTree(decoded), is(objectMapper.readTree(expectedJson)));
    }

    private static String decodeGoInterpretedString(String literal) {
        String inner = literal.substring(1, literal.length() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\\') {
                char n = inner.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Test
    public void shouldGenerateCSharpWithUsingsAndUpsertCall() {
        String code = cSharpSerializer.serialize(Collections.singletonList(sampleExpectation("/somePath", "someBody")));

        assertThat(code, containsString("using System.Text.Json;"));
        assertThat(code, containsString("using MockServer.Client;"));
        assertThat(code, containsString("using MockServer.Client.Models;"));
        assertThat(code, containsString("new MockServerClient(\"localhost\", 1080)"));
        assertThat(code, containsString("client.Upsert(JsonSerializer.Deserialize<Expectation>(@\""));
        assertThat(code, containsString("/somePath"));
    }

    @Test
    public void shouldEmbedHostileExpectationCorrectlyInCSharp() throws Exception {
        Expectation expectation = hostileExpectation();
        String expectedJson = expectationSerializer.serialize(expectation);
        String code = cSharpSerializer.serialize(Collections.singletonList(expectation));

        String start = "Deserialize<Expectation>(@\"";
        String end = "\", jsonOptions));";
        String verbatim = code.substring(code.indexOf(start) + start.length(), code.indexOf(end));
        // un-double the doubled quotes of a C# verbatim string
        String decoded = verbatim.replace("\"\"", "\"");
        assertThat(objectMapper.readTree(decoded), is(objectMapper.readTree(expectedJson)));
    }

    @Test
    public void shouldGenerateRubyWithRequireAndUpsertCall() {
        String code = rubySerializer.serialize(Collections.singletonList(sampleExpectation("/somePath", "someBody")));

        assertThat(code, containsString("require 'mockserver-client'"));
        assertThat(code, containsString("require 'json'"));
        assertThat(code, containsString("client = MockServer::Client.new('localhost', 1080)"));
        assertThat(code, containsString("client.upsert(MockServer::Expectation.from_hash(JSON.parse(<<JSON)))"));
        assertThat(code, containsString("/somePath"));
    }

    @Test
    public void shouldEmbedHostileExpectationCorrectlyInRuby() throws Exception {
        Expectation expectation = hostileExpectation();
        String expectedJson = expectationSerializer.serialize(expectation);
        String code = rubySerializer.serialize(Collections.singletonList(expectation));

        // heredoc body is between the line opening <<JSON)) and the closing delimiter line
        String openMarker = "<<JSON)))\n";
        int bodyStart = code.indexOf(openMarker) + openMarker.length();
        // closing delimiter is a line containing only JSON
        int bodyEnd = code.indexOf("\nJSON\n", bodyStart);
        String embedded = code.substring(bodyStart, bodyEnd);
        assertThat(embedded, is(expectedJson));
        assertThat(objectMapper.readTree(embedded), is(objectMapper.readTree(expectedJson)));
    }

    @Test
    public void shouldGenerateRustWithUseAndUpsertCall() {
        String code = rustSerializer.serialize(Collections.singletonList(sampleExpectation("/somePath", "someBody")));

        assertThat(code, containsString("use mockserver_client::{ClientBuilder, Expectation};"));
        assertThat(code, containsString("ClientBuilder::new(\"localhost\", 1080).build()?"));
        assertThat(code, containsString("client.upsert(&[serde_json::from_str::<Expectation>(r#\""));
        assertThat(code, containsString("/somePath"));
    }

    @Test
    public void shouldEmbedHostileExpectationCorrectlyInRustWithBumpedHashes() throws Exception {
        Expectation expectation = hostileExpectation();
        String expectedJson = expectationSerializer.serialize(expectation);
        String code = rustSerializer.serialize(Collections.singletonList(expectation));

        // the JSON contains the sequence quote-hash-hash, so a single-hash raw literal would
        // be terminated early; the generator must bump to at least r##"..."##
        assertThat(code, containsString("serde_json::from_str::<Expectation>(r##"));
        String start = "from_str::<Expectation>(r";
        int afterR = code.indexOf(start) + start.length();
        int firstQuote = code.indexOf('"', afterR);
        String hashes = code.substring(afterR, firstQuote);
        String openMarker = "r" + hashes + "\"";
        String closeMarker = "\"" + hashes;
        int bodyStart = code.indexOf(openMarker) + openMarker.length();
        int bodyEnd = code.indexOf(closeMarker, bodyStart);
        String embedded = code.substring(bodyStart, bodyEnd);
        assertThat(embedded, is(expectedJson));
        assertThat(objectMapper.readTree(embedded), is(objectMapper.readTree(expectedJson)));
    }

    @Test
    public void shouldGeneratePhpWithUseAndUpsertCall() {
        String code = phpSerializer.serialize(Collections.singletonList(sampleExpectation("/somePath", "someBody")));

        assertThat(code, containsString("<?php"));
        assertThat(code, containsString("use MockServer\\MockServerClient;"));
        assertThat(code, containsString("use MockServer\\Expectation;"));
        assertThat(code, containsString("$client = new MockServerClient('localhost', 1080);"));
        assertThat(code, containsString("$client->upsertExpectation(Expectation::fromArray(json_decode(<<<'JSON'"));
        assertThat(code, containsString("/somePath"));
    }

    @Test
    public void shouldEmbedHostileExpectationCorrectlyInPhp() throws Exception {
        Expectation expectation = hostileExpectation();
        String expectedJson = expectationSerializer.serialize(expectation);
        String code = phpSerializer.serialize(Collections.singletonList(expectation));

        // nowdoc body between the opening <<<'JSON' line and the closing JSON line
        String openMarker = "json_decode(<<<'JSON'\n";
        int bodyStart = code.indexOf(openMarker) + openMarker.length();
        int bodyEnd = code.indexOf("\nJSON, true)", bodyStart);
        String embedded = code.substring(bodyStart, bodyEnd);
        assertThat(embedded, is(expectedJson));
        assertThat(objectMapper.readTree(embedded), is(objectMapper.readTree(expectedJson)));
    }

    @Test
    public void shouldHandleEmptyListsForAllNewLanguages() {
        assertThat(goSerializer.serialize(Collections.emptyList()), containsString("package main"));
        assertThat(goSerializer.serialize(Collections.emptyList()).contains("client.Upsert(e)"), is(false));
        assertThat(cSharpSerializer.serialize(null), containsString("using MockServer.Client;"));
        assertThat(rubySerializer.serialize(Collections.emptyList()), containsString("require 'mockserver-client'"));
        assertThat(rustSerializer.serialize(null), containsString("use mockserver_client::{ClientBuilder, Expectation};"));
        assertThat(phpSerializer.serialize(Collections.emptyList()), containsString("<?php"));
    }
}
