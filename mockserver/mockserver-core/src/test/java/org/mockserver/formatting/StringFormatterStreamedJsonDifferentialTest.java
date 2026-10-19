package org.mockserver.formatting;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonValue;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.ObjectWithJsonToString;
import org.mockserver.model.SocketAddress;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_NOT_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.XmlBody.xml;
import static org.mockserver.verify.Verification.verification;

/**
 * Differential corpus for the streamed rendering of an argument whose text is its JSON: every entry point that
 * renders a log message must produce, byte for byte, what it produced when each argument was first turned into a
 * String by its {@code toString} and then indented. The reference below is that implementation, kept verbatim
 * apart from inlining the indents.
 *
 * <p>Random raw and string values long enough to span several of Jackson's buffer flushes put every line
 * terminator, and the {@code \r\n} pair, on each side of a chunk boundary.
 */
public class StringFormatterStreamedJsonDifferentialTest {

    private static final String[] INDENTS = {"", "  ", "    ", "      ", "        "};
    private static final String TERMINATORS = "a\nb\rc\r\nd\u0085e\u2028f\u2029g\n";
    private static final String[] MESSAGES = {
        "", "no placeholders", "{}", "{}{}", "a{}", "{}a", "a{}b{}c", "a{}{}b", "x{}{}{}",
        "received request:{}", "request:{}didn't match expectation:{}because:{}",
        "returning response:{}for request:{}for action:{}from expectation:{}", "a {} b", "multi\nline{}msg"
    };
    private static final char[] ALPHABET = {'a', ' ', '"', '\\', '\n', '\r', '\u000b', '\u0084', '\u0085', '\u0086', '\u2028', '\u2029', 'ü', '\ud83d', '\ude00', '{', '}'};

    @Test
    public void formatLogMessageMatchesTheToStringImplementationForEveryArgumentShape() {
        int cases = 0;
        List<Object> arguments = arguments();
        for (int indent = 0; indent <= 3; indent++) {
            for (String message : MESSAGES) {
                for (int i = 0; i < arguments.size(); i++) {
                    Object[] single = {arguments.get(i)};
                    Object[] triple = {arguments.get(i), arguments.get((i + 1) % arguments.size()), arguments.get((i + 7) % arguments.size())};
                    for (Object[] set : new Object[][]{single, triple}) {
                        assertThat("indent=" + indent + " message=" + message + " argument=" + i,
                            StringFormatter.formatLogMessage(indent, message, set),
                            is(referenceFormatLogMessage(indent, message, set)));
                        cases++;
                    }
                }
            }
        }
        assertThat(cases, greaterThanOrEqualTo(4_000));
    }

    @Test
    public void indentAndToStringMatchesTheToStringImplementationForEveryArgumentShape() {
        for (int indent = 0; indent <= 4; indent++) {
            for (Object argument : arguments()) {
                assertThat("indent=" + indent + " argument=" + argument.getClass().getSimpleName(),
                    StringFormatter.indentAndToString(indent, new Object[]{argument})[0].toString(),
                    is(referenceIndentAndToString(indent, new Object[]{argument})[0].toString()));
            }
        }
    }

    @Test
    public void writeLogMessageMatchesTheToStringImplementationForEveryArgumentShape() throws IOException {
        for (String message : MESSAGES) {
            for (Object argument : arguments()) {
                if (argument instanceof Unserialisable) {
                    continue;
                }
                StringWriter writer = new StringWriter();
                StringFormatter.writeLogMessage(writer, message, argument);
                assertThat("message=" + message, writer.toString(), is(referenceFormatLogMessage(0, message, argument)));
            }
        }
    }

    @Test
    public void streamedJsonMatchesToStringAcrossBufferBoundaries() throws IOException {
        Random random = new Random(1262);
        for (int iteration = 0; iteration < 1_500; iteration++) {
            String text = randomText(random, random.nextInt(iteration % 10 == 0 ? 40_000 : 9_000));
            for (Object argument : new Object[]{new RawJson(text), new JsonString(text)}) {
                int indent = random.nextInt(4);
                assertThat("iteration=" + iteration, StringFormatter.formatLogMessage(indent, "value:{}end", argument),
                    is(referenceFormatLogMessage(indent, "value:{}end", argument)));
                StringWriter writer = new StringWriter();
                StringFormatter.writeLogMessage(writer, "value:{}end", argument);
                assertThat("iteration=" + iteration, writer.toString(), is(referenceFormatLogMessage(0, "value:{}end", argument)));
            }
        }
    }

    @Test
    public void logEntryMessagesMatchTheToStringImplementation() throws IOException {
        Configuration[] configurations = {
            configuration().logLevel(Level.INFO).redactSecretsInLog(false),
            configuration().logLevel(Level.INFO).redactSecretsInLog(true).fixtureBodyRedactFields("password")
        };
        int cases = 0;
        for (Configuration configuration : configurations) {
            for (LogEntry entry : entries()) {
                LogEntry.RedactedView view = entry.redactedView(configuration);
                String expected = view.getArguments() == null ? view.getMessageFormat() : referenceFormatLogMessage(0, view.getMessageFormat(), view.getArguments());
                assertThat(entry.getMessageFormat(), entry.getMessage(configuration), is(expected));
                assertThat(entry.getMessageFormat(), view.getMessage(), is(expected));
                StringWriter writer = new StringWriter();
                entry.writeMessage(configuration, writer);
                assertThat(entry.getMessageFormat(), writer.toString(), is(expected));
                cases += 3;
            }
        }
        assertThat(cases, greaterThanOrEqualTo(100));
    }

    @Test
    public void anArgumentThatCannotBeSerialisedIsShownByItsFieldsAsToStringShowsIt() {
        Unserialisable argument = new Unserialisable();
        assertThat(StringFormatter.formatLogMessage("before{}after", "text", argument),
            is(referenceFormatLogMessage(0, "before{}after", "text", argument)));
        assertThat(StringFormatter.formatLogMessage("first{}second{}", argument, request("/path")),
            is(referenceFormatLogMessage(0, "first{}second{}", argument, request("/path"))));
    }

    private static List<Object> arguments() {
        List<Object> arguments = new ArrayList<>();
        HttpRequest simple = request("/simple").withMethod("GET").withHeader(header("Host", "localhost:1080")).withKeepAlive(true).withSecure(false);
        arguments.add(simple);
        arguments.add(request());
        arguments.add(request("/path").withHeader(header("x-line", "one\u2028two\u0085three\u2029four")).withBody("body\nwith\r\nlines\rand\u2028more"));
        arguments.add(request("/json").withMethod("POST").withBody(json("{\"items\":[{\"id\":1,\"name\":\"a\\nb\"},{\"id\":2}],\"password\":\"secret\"}")));
        arguments.add(request("/xml").withBody(xml("<a>\n  <b>text\r\n</b>\n</a>")));
        arguments.add(request("/binary").withBody(binary(new byte[]{0, 1, 2, (byte) 0xff, '\n', '\r'})));
        arguments.add(request("/form").withBody(params(param("a", "1"), param("b", "two\nlines"))));
        arguments.add(request("/query").withQueryStringParameter("q", "x y").withPathParameter("id", "42").withCookie(cookie("session", "abc\u2028def")));
        arguments.add(request().withPath(not("/negated")).withMethod(not("DELETE")).withHeader(not("x-absent"), not(".*")));
        arguments.add(request("/socket").withSocketAddress("example.com", 443, SocketAddress.Scheme.HTTPS).withSecure(true));
        arguments.add(request("/large").withBody(largeText()));
        arguments.add(request("/unicode").withBody("ünïcödé 😀 \"quoted\" back\\slash", StandardCharsets.UTF_8));
        arguments.add(response());
        arguments.add(response("text\nbody").withStatusCode(201).withReasonPhrase("Created"));
        arguments.add(response().withBody(json("{\"id\":42}")).withHeader(header("Set-Cookie", "a=b")).withDelay(Delay.milliseconds(10)));
        arguments.add(response().withBody(binary(new byte[]{1, 2, 3})).withHeader("x-u", "\u0085"));
        arguments.add(response().withBody(largeText()));
        arguments.add(forward().withHost("localhost").withPort(1090));
        arguments.add(error().withDropConnection(true));
        arguments.add(HttpTemplate.template(HttpTemplate.TemplateType.VELOCITY, "{\n  'statusCode': 200\n}"));
        arguments.add(new Expectation(request("/other").withMethod("GET")).withId("expectation-1").thenRespond(response().withBody("{\"id\":42}")));
        arguments.add(new Expectation(request("/forward")).withId("expectation-2").thenForward(forward().withHost("h")));
        arguments.add(new Expectation(request("/error")).withId("expectation-3").thenError(error().withDropConnection(true)));
        arguments.add(verification().withRequest(request("/verify")).withTimes(VerificationTimes.atLeast(2)));
        arguments.add(new HttpRequestAndHttpResponse().withHttpRequest(simple).withHttpResponse(response("ok")));
        arguments.add(Delay.delay(TimeUnit.SECONDS, 3));
        arguments.add(new JsonString(""));
        arguments.add(new JsonString("quoted \" and\nline\u2028and\u0085more"));
        arguments.add(new JsonString("\""));
        arguments.add(new RawJson(TERMINATORS));
        arguments.add(new RawJson(""));
        arguments.add(new Unserialisable());
        arguments.add(new FormatsWhileSerialised());
        arguments.add(new FormatsInToString());
        arguments.add(Arrays.asList(simple, response("in a list")));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("request", simple);
        map.put("text", "a\nb");
        arguments.add(map);
        arguments.add(TERMINATORS);
        arguments.add("");
        arguments.add(42);
        arguments.add(new Object[]{simple, "array"});
        arguments.add(new StringBuilder("builder\r\nline"));
        return arguments;
    }

    private static List<LogEntry> entries() {
        List<LogEntry> entries = new ArrayList<>();
        HttpRequest secretRequest = request("/login").withMethod("POST")
            .withHeader(header("Authorization", "Bearer abc.def.ghi"))
            .withBody(json("{\"user\":\"u\",\"password\":\"hunter2\"}"));
        for (Object argument : arguments()) {
            // an array is shown by its identity, and redaction copies it
            if (argument instanceof Unserialisable || argument instanceof Object[]) {
                continue;
            }
            entries.add(new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(Level.INFO).setMessageFormat("received:{}").setArguments(argument));
        }
        entries.add(new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(Level.INFO).setHttpRequest(secretRequest).setMessageFormat("received request:{}").setArguments(secretRequest));
        Expectation expectation = new Expectation(request("/login")).withId("login").thenRespond(response().withHeader("Set-Cookie", "token=xyz").withBody("{\"token\":\"xyz\"}"));
        entries.add(new LogEntry().setType(EXPECTATION_NOT_MATCHED).setLogLevel(Level.INFO).setHttpRequest(secretRequest).setExpectation(expectation)
            .setMessageFormat("request:{}didn't match expectation:{}because:{}").setArguments(secretRequest, expectation, "method matched" + NEW_LINE + "  path didn't match"));
        entries.add(new LogEntry().setType(EXPECTATION_RESPONSE).setLogLevel(Level.INFO).setHttpRequest(secretRequest).setHttpResponse(expectation.getHttpResponse())
            .setMessageFormat("returning response:{}for request:{}for action:{}from expectation:{}")
            .setArguments(expectation.getHttpResponse(), secretRequest, expectation.getHttpResponse(), expectation.getId()));
        entries.add(new LogEntry().setLogLevel(Level.INFO).setMessageFormat("no arguments"));
        entries.add(new LogEntry().setLogLevel(Level.INFO).setMessageFormat("text arguments{}and{}").setArguments("one\ntwo", 2));
        return entries;
    }

    private static String randomText(Random random, int length) {
        char[] text = new char[length];
        for (int i = 0; i < length; i++) {
            text[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(text);
    }

    private static String largeText() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 1_200; i++) {
            text.append("line ").append(i).append(i % 3 == 0 ? "\r\n" : i % 3 == 1 ? "\u2028" : "\n");
        }
        return text.toString();
    }

    // ---- the pre-change implementation: each argument's toString, then the indent ----

    private static StringBuilder[] referenceIndentAndToString(final int indent, final Object... objects) {
        final StringBuilder[] indentedObjects = new StringBuilder[objects.length];
        for (int i = 0; i < objects.length; i++) {
            indentedObjects[i] =
                new StringBuilder(NEW_LINE)
                    .append(NEW_LINE)
                    .append(String.valueOf(objects[i]).replaceAll("(?m)^", INDENTS[indent]))
                    .append(NEW_LINE);
        }
        return indentedObjects;
    }

    private static String referenceFormatLogMessage(final int indent, final String message, final Object... arguments) {
        final StringBuilder logMessage = new StringBuilder();
        final StringBuilder[] formattedArguments = referenceIndentAndToString(indent + 1, arguments);
        final String[] messageParts = message.split("\\{}");
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            logMessage.append(INDENTS[indent]).append(messageParts[messagePartIndex]);
            if (formattedArguments.length > 0 &&
                formattedArguments.length > messagePartIndex) {
                logMessage.append(formattedArguments[messagePartIndex]);
            }
            if (messagePartIndex < messageParts.length - 1) {
                logMessage.append(NEW_LINE);
                if (!messageParts[messagePartIndex + 1].startsWith(" ")) {
                    logMessage.append(" ");
                }
            }
        }
        return logMessage.toString();
    }

    public static class JsonString extends ObjectWithJsonToString {
        private final String value;

        JsonString(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    public static class RawJson extends ObjectWithJsonToString {
        private final String raw;

        RawJson(String raw) {
            this.raw = raw;
        }

        @JsonRawValue
        public String getRaw() {
            return raw;
        }

        public String getAfter() {
            return "after";
        }
    }

    // formats a message of its own while its JSON is being written into the message that quotes it
    public static class FormatsWhileSerialised extends ObjectWithJsonToString {
        public String getNested() {
            return StringFormatter.formatLogMessage("nested:{}and{}", request("/nested").withBody("a\nb"), TERMINATORS);
        }
    }

    public static class FormatsInToString {
        @Override
        public String toString() {
            return StringFormatter.formatLogMessage(1, "inner{}", new FormatsWhileSerialised());
        }
    }

    public static class Unserialisable extends ObjectWithJsonToString {
        public String getValue() {
            throw new IllegalStateException("no value");
        }
    }
}
