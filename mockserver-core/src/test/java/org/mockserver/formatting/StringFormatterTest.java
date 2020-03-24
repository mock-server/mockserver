package org.mockserver.formatting;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
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
        String logMessage = StringFormatter.formatLogMessage(2,"returning response:{}for request:{}for action:{}", response("response_body"), request("request_path"), forward());

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

}
