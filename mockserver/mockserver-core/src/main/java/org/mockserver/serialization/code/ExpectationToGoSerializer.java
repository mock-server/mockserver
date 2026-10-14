package org.mockserver.serialization.code;

import org.mockserver.mock.Expectation;
import org.mockserver.serialization.ExpectationSerializer;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Generates copy-paste-ready Go expectation code for the MockServer Go client
 * ({@code github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9}).
 * <p>
 * Following the same JSON-wrap pattern as the JavaScript/Python generators, the
 * generated code embeds each expectation's existing JSON serialization (the
 * same bytes produced by {@code format=JSON}) and parses it at runtime with
 * {@code json.Unmarshal} into a {@code mockserver.Expectation}, then passes it
 * to {@code client.Upsert(...)}.
 * <p>
 * Verified against the Go client source:
 * <ul>
 *   <li>{@code func New(host string, port int, opts ...Option) *Client}</li>
 *   <li>{@code func (c *Client) Upsert(expectations ...Expectation) ([]Expectation, error)}</li>
 *   <li>{@code type Expectation struct} with {@code json:"..."} tags (so {@code json.Unmarshal} works)</li>
 * </ul>
 * <p>
 * The JSON is embedded in a Go raw string literal (backticks) which needs no
 * escaping. If the JSON happens to contain a backtick (impossible for Jackson
 * structural output but possible inside a string value), the generator falls
 * back to a double-quoted interpreted string with the minimal Go escapes.
 * Example output for a single expectation:
 * <pre>
 * package main
 *
 * import (
 *     "encoding/json"
 *
 *     mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
 * )
 *
 * func main() {
 *     client := mockserver.New("localhost", 1080)
 *
 *     var e mockserver.Expectation
 *     _ = json.Unmarshal([]byte(`{ ... }`), &e)
 *     client.Upsert(e)
 * }
 * </pre>
 *
 * @author jamesdbloom
 */
public class ExpectationToGoSerializer {

    private static final String NEW_LINE = "\n";
    private static final String TAB = "\t";

    private final ExpectationSerializer expectationSerializer;

    public ExpectationToGoSerializer(ExpectationSerializer expectationSerializer) {
        this.expectationSerializer = expectationSerializer;
    }

    public String serialize(List<Expectation> expectations) {
        return GeneratedCode.toString(writer -> serialize(expectations, writer));
    }

    /**
     * As {@link #serialize(List)}, writing the code for one expectation at a time to {@code writer}.
     */
    public void serialize(List<Expectation> expectations, Writer writer) {
        StringBuilder output = new StringBuilder();
        output.append("package main").append(NEW_LINE).append(NEW_LINE);
        output.append("import (").append(NEW_LINE);
        output.append(TAB).append("\"encoding/json\"").append(NEW_LINE).append(NEW_LINE);
        output.append(TAB).append("mockserver \"github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9\"").append(NEW_LINE);
        output.append(")").append(NEW_LINE).append(NEW_LINE);
        output.append("func main() {").append(NEW_LINE);
        output.append(TAB).append("client := mockserver.New(\"localhost\", 1080)").append(NEW_LINE);
        if (expectations != null) {
            for (Expectation expectation : expectations) {
                GeneratedCode.flush(output, writer);
                if (expectation == null) {
                    continue;
                }
                output.append(NEW_LINE);
                output.append(TAB).append("var e mockserver.Expectation").append(NEW_LINE);
                output.append(TAB).append("_ = json.Unmarshal([]byte(");
                writeGoStringLiteral(expectation, output, writer);
                output.append("), &e)").append(NEW_LINE);
                output.append(TAB).append("client.Upsert(e)").append(NEW_LINE);
            }
        }
        output.append("}").append(NEW_LINE);
        GeneratedCode.flush(output, writer);
    }

    /**
     * Emit the expectation's JSON as a Go string literal. Prefer a raw string literal
     * (backticks) which preserves the JSON verbatim with no escaping; fall back
     * to a double-quoted interpreted string (with Go escapes) only if the JSON
     * contains a backtick, which cannot be represented inside a raw literal. The JSON
     * is written once to look for a backtick and once to {@code writer}, never held whole.
     */
    private void writeGoStringLiteral(Expectation expectation, StringBuilder output, Writer writer) {
        boolean[] backtick = new boolean[1];
        expectationSerializer.serialize(expectation, new GeneratedCode.CharWriter() {
            @Override
            public void write(int c) {
                backtick[0] |= c == '`';
            }
        });
        String quote = backtick[0] ? "\"" : "`";
        output.append(quote);
        GeneratedCode.flush(output, writer);
        expectationSerializer.serialize(expectation, backtick[0] ? goEscaped(writer) : writer);
        output.append(quote);
    }

    private static Writer goEscaped(Writer writer) {
        return new GeneratedCode.CharWriter() {
            @Override
            public void write(int c) throws IOException {
                switch (c) {
                    case '\\':
                        writer.write("\\\\");
                        break;
                    case '"':
                        writer.write("\\\"");
                        break;
                    case '\n':
                        writer.write("\\n");
                        break;
                    case '\r':
                        writer.write("\\r");
                        break;
                    case '\t':
                        writer.write("\\t");
                        break;
                    default:
                        writer.write(c);
                }
            }
        };
    }
}
