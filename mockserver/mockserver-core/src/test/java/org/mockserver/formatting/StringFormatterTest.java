package org.mockserver.formatting;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import org.mockserver.mock.Expectation;

import static org.mockserver.formatting.StringFormatter.escapeControlCharactersForLog;
import static org.mockserver.formatting.StringFormatter.formatBytes;
import static org.mockserver.formatting.StringFormatter.formatCompactLogMessage;
import static org.mockserver.formatting.StringFormatter.hexDumpForLog;
import static org.mockserver.formatting.StringFormatter.utf8ForLog;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class StringFormatterTest {

    @Test
    public void shouldFormatLogMessageWithMultipleParameters() {
        // when
        String logMessage = StringFormatter.formatLogMessage("returning response:{}for request:{}for action:{}", response("response_body"), request("request_path"), forward());

        // then
        assertThat(logMessage, is(
            "returning response:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"statusCode\" : 200," + NEW_LINE +
                "    \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                "    \"body\" : \"response_body\"" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " for request:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"path\" : \"request_path\"" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " for action:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"port\" : 80," + NEW_LINE +
                "    \"scheme\" : \"HTTP\"" + NEW_LINE +
                "  }" + NEW_LINE
        ));
    }

    @Test
    public void shouldFormatLogMessageWithMultipleParametersWithIndent() {
        // when
        String logMessage = StringFormatter.formatLogMessage(2, "returning response:{}for request:{}for action:{}", response("response_body"), request("request_path"), forward());

        // then
        assertThat(logMessage, is("    returning response:" + NEW_LINE +
            NEW_LINE +
            "      {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"response_body\"" + NEW_LINE +
            "      }" + NEW_LINE +
            NEW_LINE +
            "     for request:" + NEW_LINE +
            NEW_LINE +
            "      {" + NEW_LINE +
            "        \"path\" : \"request_path\"" + NEW_LINE +
            "      }" + NEW_LINE +
            NEW_LINE +
            "     for action:" + NEW_LINE +
            NEW_LINE +
            "      {" + NEW_LINE +
            "        \"port\" : 80," + NEW_LINE +
            "        \"scheme\" : \"HTTP\"" + NEW_LINE +
            "      }" + NEW_LINE));
    }

    @Test
    public void shouldFormatLogMessageWithASingleParameter() {
        // when
        String logMessage = StringFormatter.formatLogMessage("returning response:{}", response("response_body"));

        // then
        assertThat(logMessage, is(
            "returning response:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"statusCode\" : 200," + NEW_LINE +
                "    \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                "    \"body\" : \"response_body\"" + NEW_LINE +
                "  }" + NEW_LINE
        ));
    }

    @Test
    public void shouldFormatLogMessageWithASingleParameterWithIndent() {
        // when
        String logMessage = StringFormatter.formatLogMessage(1, "returning response:{}", response("response_body"));

        // then
        assertThat(logMessage, is(
            "  returning response:" + NEW_LINE +
                NEW_LINE +
                "    {" + NEW_LINE +
                "      \"statusCode\" : 200," + NEW_LINE +
                "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                "      \"body\" : \"response_body\"" + NEW_LINE +
                "    }" + NEW_LINE
        ));
    }

    @Test
    public void shouldIgnoreExtraParameters() {
        // when
        String logMessage = StringFormatter.formatLogMessage("returning response:{}", response("response_body"), request("request_path"), forward());

        // then
        assertThat(logMessage, is(
            "returning response:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"statusCode\" : 200," + NEW_LINE +
                "    \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                "    \"body\" : \"response_body\"" + NEW_LINE +
                "  }" + NEW_LINE
        ));
    }

    @Test
    public void shouldIgnoreTooFewParameters() {
        // when
        String logMessage = StringFormatter.formatLogMessage("returning response:{}for request:{}for action:{}", response("response_body"));

        // then
        assertThat(logMessage, is(
            "returning response:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"statusCode\" : 200," + NEW_LINE +
                "    \"reasonPhrase\" : \"OK\"," + NEW_LINE +
                "    \"body\" : \"response_body\"" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " for request:" + NEW_LINE +
                " for action:"
        ));
    }


    @Test
    public void shouldFormatCompactLogMessageWithMultipleParameters() {
        // when
        String logMessage = formatCompactLogMessage(
            "returning response:{}for request:{}for action:{}",
            response("response_body").withStatusCode(200),
            request("request_path").withMethod("GET"),
            response("response_body").withStatusCode(200)
        );

        // then
        assertThat(logMessage, is("returning response: 200 for request: GET request_path for action: 200"));
    }

    @Test
    public void shouldFormatCompactLogMessageWithStringArguments() {
        // when
        String logMessage = formatCompactLogMessage(
            "some message with{}and{}",
            "value1",
            "value2"
        );

        // then
        assertThat(logMessage, is("some message with value1 and value2"));
    }

    @Test
    public void shouldFormatCompactLogMessageWithExpectation() {
        // when
        Expectation expectation = new Expectation(request("test")).withId("test-id");
        String logMessage = formatCompactLogMessage(
            "matched expectation:{}",
            expectation
        );

        // then
        assertThat(logMessage, is("matched expectation: test-id"));
    }

    @Test
    public void shouldFormatCompactLogMessageWithNoArguments() {
        // when
        String logMessage = formatCompactLogMessage("started on port: 1080");

        // then
        assertThat(logMessage, is("started on port: 1080"));
    }

    @Test
    public void shouldFormatCompactLogMessageWithForward() {
        // when
        String logMessage = formatCompactLogMessage(
            "returning response:{}for request:{}for action:{}",
            response("response_body").withStatusCode(200),
            request("request_path").withMethod("POST"),
            forward()
        );

        // then
        assertThat(logMessage, is("returning response: 200 for request: POST request_path for action: forward"));
    }

    @Test
    public void shouldFormatBytes() {
        // given
        byte[] bytes = ("" +
            "The trick to getting kids to eat anything is to put catchup on it." + NEW_LINE +
            "I like to leave work after my eight-hour tea-break." + NEW_LINE +
            "He barked orders at his daughters but they just stared back with amusement." + NEW_LINE +
            "As the years pass by, we all know owners look more and more like their dogs." + NEW_LINE +
            "It must be five o'clock somewhere." + NEW_LINE +
            "The father died during childbirth." + NEW_LINE +
            "He had concluded that pigs must be able to fly in Hog Heaven." + NEW_LINE +
            "The pet shop stocks everything you need to keep your anaconda happy." + NEW_LINE +
            "We should play with legos at camp." + NEW_LINE +
            "It doesn't sound like that will ever be on my travel list."
        ).getBytes(StandardCharsets.UTF_8);

        // then
        assertThat(formatBytes(bytes), is("" +
            "54686520747269636b20746f2067657474696e67206b69647320746f20656174" + NEW_LINE +
            "20616e797468696e6720697320746f207075742063617463687570206f6e2069" + NEW_LINE +
            "742e0a49206c696b6520746f206c6561766520776f726b206166746572206d79" + NEW_LINE +
            "2065696768742d686f7572207465612d627265616b2e0a4865206261726b6564" + NEW_LINE +
            "206f726465727320617420686973206461756768746572732062757420746865" + NEW_LINE +
            "79206a75737420737461726564206261636b207769746820616d7573656d656e" + NEW_LINE +
            "742e0a41732074686520796561727320706173732062792c20776520616c6c20" + NEW_LINE +
            "6b6e6f77206f776e657273206c6f6f6b206d6f726520616e64206d6f7265206c" + NEW_LINE +
            "696b6520746865697220646f67732e0a4974206d757374206265206669766520" + NEW_LINE +
            "6f27636c6f636b20736f6d6577686572652e0a54686520666174686572206469" + NEW_LINE +
            "656420647572696e67206368696c6462697274682e0a48652068616420636f6e" + NEW_LINE +
            "636c7564656420746861742070696773206d7573742062652061626c6520746f" + NEW_LINE +
            "20666c7920696e20486f672048656176656e2e0a546865207065742073686f70" + NEW_LINE +
            "2073746f636b732065766572797468696e6720796f75206e65656420746f206b" + NEW_LINE +
            "65657020796f757220616e61636f6e64612068617070792e0a57652073686f75" + NEW_LINE +
            "6c6420706c61792077697468206c65676f732061742063616d702e0a49742064" + NEW_LINE +
            "6f65736e277420736f756e64206c696b6520746861742077696c6c2065766572" + NEW_LINE +
            "206265206f6e206d792074726176656c206c6973742e"));
    }

    @Test
    public void shouldLogOnlyTheFirstBytesOfALongerMessageAndItsLength() {
        byte[] bytes = "0123456789".getBytes(StandardCharsets.UTF_8);

        assertThat(hexDumpForLog(bytes, 4), is("30313233...(10 bytes, only the first 4 logged, maxLoggedBodyBytes)"));
        assertThat(formatBytes(bytes, 4), is("30313233...(10 bytes, only the first 4 logged, maxLoggedBodyBytes)"));
        assertThat(utf8ForLog(bytes, 4), is("0123...(10 bytes, only the first 4 logged, maxLoggedBodyBytes)"));
    }

    @Test
    public void shouldLogAllOfAMessageNoLongerThanTheLimitOrWithNoLimit() {
        byte[] bytes = "0123456789".getBytes(StandardCharsets.UTF_8);

        for (int maxBytes : new int[]{10, 11, 0}) {
            assertThat(hexDumpForLog(bytes, maxBytes), is("30313233343536373839"));
            assertThat(formatBytes(bytes, maxBytes), is(formatBytes(bytes)));
            assertThat(utf8ForLog(bytes, maxBytes), is("0123456789"));
        }
    }

    @Test
    public void shouldSplitTheLoggedFirstBytesIntoLinesAsTheWholeMessageIs() {
        byte[] bytes = new byte[100];

        assertThat(formatBytes(bytes, 40), is("0".repeat(64) + NEW_LINE + "0".repeat(16) + "...(100 bytes, only the first 40 logged, maxLoggedBodyBytes)"));
    }

    @Test
    public void shouldFormatABuffersReadableBytesAsTheirArrayIsFormattedWithoutConsumingThem() {
        byte[] bytes = new byte[150];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        // the readable bytes start part way into the buffer
        ByteBuf buffer = Unpooled.buffer().writeZero(7).writeBytes(bytes);
        buffer.skipBytes(7);
        try {
            for (int maxBytes : new int[]{0, 40, 149, 150, 151}) {
                assertThat(formatBytes(buffer, maxBytes), is(formatBytes(bytes, maxBytes)));
            }
            assertThat(buffer.readerIndex(), is(7));
            assertThat(buffer.readableBytes(), is(150));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void shouldEscapeLineBreaksSoAValueCannotStartANewLogLine() {
        String escaped = escapeControlCharactersForLog("host\r\n2026-01-01 INFO forged\nline\ttab");

        assertThat(escaped, is("host\\r\\n2026-01-01 INFO forged\\nline\\ttab"));
        assertThat(escaped.indexOf('\r'), is(-1));
        assertThat(escaped.indexOf('\n'), is(-1));
    }

    @Test
    public void shouldEscapeOtherControlAndLineSeparatorCharacters() {
        String value = "a" + (char) 0x00 + (char) 0x1B + (char) 0x7F + (char) 0x85 + (char) 0x2028 + (char) 0x2029
            + (char) 0x202E + (char) 0x2066 + (char) 0x200F + "b";

        assertThat(escapeControlCharactersForLog(value),
            is("a\\u0000\\u001b\\u007f\\u0085\\u2028\\u2029\\u202e\\u2066\\u200fb"));
    }

    @Test
    public void shouldReturnPrintableValueUnchanged() {
        String value = "upstream.example:8080 caf\u00e9 \u65e5\u672c";

        assertThat(escapeControlCharactersForLog(value) == value, is(true));
        assertThat(escapeControlCharactersForLog(null) == null, is(true));
    }
}
