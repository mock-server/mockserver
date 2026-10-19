package org.mockserver.benchmark;

import org.mockserver.formatting.StringFormatter;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

/**
 * Cost of rendering one event-log message as the console does ({@link StringFormatter#formatLogMessage}),
 * against the same rendering with the {@code (?m)^} regex indent it replaced, in one JMH run. The {@code indent*}
 * pair isolates the indent of an already-serialised argument; the {@code message*} pair includes each
 * argument's Jackson {@code toString}.
 *
 * <pre>./run.sh -prof gc LogMessageRenderBenchmark</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class LogMessageRenderBenchmark {

    private static final Pattern START_OF_EACH_LINE = Pattern.compile("(?m)^");
    private static final Pattern ARGUMENT_PLACEHOLDER = Pattern.compile("\\{}");

    /**
     * RECEIVED: "received request:{}"; NOT_MATCHED: a did-not-match entry quoting the request, the expectation and
     * the because text; RESPONSE: "returning response:{}for request:{}for action:{}from expectation:{}".
     */
    @Param({"RECEIVED", "NOT_MATCHED", "RESPONSE"})
    private String shape;

    /**
     * SIMPLE: the bodiless keep-alive GET the perf sweep sends; JSON: a POST with a 1 KB pretty-printed JSON body.
     */
    @Param({"SIMPLE", "JSON"})
    private String request;

    private String format;
    private Object[] arguments;
    private String serialisedRequest;

    @Setup
    public void setup() {
        try {
            StringFormatter.class.getDeclaredMethod("appendIndented", StringBuilder.class, String.class, String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("mockserver-core on the classpath still indents with the regex, so messageLoop would measure it; install mockserver-core from this tree first", e);
        }
        HttpRequest httpRequest = "SIMPLE".equals(request)
            ? request("/simple").withMethod("GET").withHeader(header("Host", "localhost:1080")).withHeader(header("User-Agent", "k6/0.54.0")).withKeepAlive(true).withSecure(false)
            : request("/api/orders").withMethod("POST").withHeader(header("Content-Type", "application/json")).withBody(json(jsonBody()));
        HttpResponse httpResponse = response().withStatusCode(200).withBody("{\"id\":42}");
        Expectation expectation = new Expectation(request("/other").withMethod("GET")).withId("expectation-1").thenRespond(httpResponse);
        switch (shape) {
            case "RECEIVED":
                format = "received request:{}";
                arguments = new Object[]{httpRequest};
                break;
            case "NOT_MATCHED":
                format = "request:{}didn't match expectation \"expectation-1\":{}because:{}";
                arguments = new Object[]{httpRequest, expectation, "method matched" + NEW_LINE + "  path didn't match"};
                break;
            default:
                format = "returning response:{}for request:{}for action:{}from expectation:{}";
                arguments = new Object[]{httpResponse, httpRequest, httpResponse, "expectation-1"};
        }
        serialisedRequest = String.valueOf(httpRequest);
        if (!messageLoop().equals(messageRegex())) {
            throw new IllegalStateException("the two renderings differ");
        }
    }

    @Benchmark
    public StringBuilder[] indentLoop() {
        return StringFormatter.indentAndToString(1, new Object[]{serialisedRequest});
    }

    @Benchmark
    public StringBuilder indentRegex() {
        return regexIndentAndToString(1, serialisedRequest);
    }

    @Benchmark
    public String messageLoop() {
        return StringFormatter.formatLogMessage(format, arguments);
    }

    @Benchmark
    public String messageRegex() {
        final StringBuilder logMessage = new StringBuilder();
        final String[] messageParts = ARGUMENT_PLACEHOLDER.split(format);
        final StringBuilder[] formattedArguments = new StringBuilder[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            formattedArguments[i] = regexIndentAndToString(1, arguments[i]);
        }
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            logMessage.append(messageParts[messagePartIndex]);
            if (formattedArguments.length > messagePartIndex) {
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

    private static StringBuilder regexIndentAndToString(int indent, Object argument) {
        return new StringBuilder(NEW_LINE)
            .append(NEW_LINE)
            .append(START_OF_EACH_LINE.matcher(String.valueOf(argument)).replaceAll(indent == 1 ? "  " : "    "))
            .append(NEW_LINE);
    }

    private static String jsonBody() {
        StringBuilder body = new StringBuilder("{\n  \"items\" : [");
        for (int i = 0; i < 16; i++) {
            body.append(i == 0 ? "" : ",").append("\n    { \"id\" : ").append(i).append(", \"name\" : \"item-").append(i).append("\", \"price\" : ").append(i * 3).append(" }");
        }
        return body.append("\n  ]\n}").toString();
    }
}
