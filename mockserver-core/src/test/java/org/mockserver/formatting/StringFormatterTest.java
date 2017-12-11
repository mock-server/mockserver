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
        String logMessage = StringFormatter.formatLogMessage("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response("response_body"), request("request_path"), forward());

        // then
        assertThat(logMessage, is(
                "returning response:" + NEW_LINE +
                        "" + NEW_LINE +
                        "\t{" + NEW_LINE +
                        "\t  \"statusCode\" : 200," + NEW_LINE +
                        "\t  \"body\" : \"response_body\"" + NEW_LINE +
                        "\t}" + NEW_LINE +
                        "" + NEW_LINE +
                        " for request:" + NEW_LINE +
                        "" + NEW_LINE +
                        "\t{" + NEW_LINE +
                        "\t  \"path\" : \"request_path\"" + NEW_LINE +
                        "\t}" + NEW_LINE +
                        "" + NEW_LINE +
                        " for action:" + NEW_LINE +
                        "" + NEW_LINE +
                        "\t{" + NEW_LINE +
                        "\t  \"port\" : 80," + NEW_LINE +
                        "\t  \"scheme\" : \"HTTP\"" + NEW_LINE +
                        "\t}" + NEW_LINE
        ));
    }

    @Test
    public void shouldFormatLogMessageWithASingleParameter() {
        // when
        String logMessage = StringFormatter.formatLogMessage("returning response:{}", response("response_body"));

        // then
        assertThat(logMessage, is(
                "returning response:" + NEW_LINE +
                        NEW_LINE +
                        "\t{" + NEW_LINE +
                        "\t  \"statusCode\" : 200," + NEW_LINE +
                        "\t  \"body\" : \"response_body\"" + NEW_LINE +
                        "\t}" + NEW_LINE
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
                        "\t{" + NEW_LINE +
                        "\t  \"statusCode\" : 200," + NEW_LINE +
                        "\t  \"body\" : \"response_body\"" + NEW_LINE +
                        "\t}" + NEW_LINE
        ));
    }

    @Test
    public void shouldIgnoreTooFewParameters() {
        // when
        String logMessage = StringFormatter.formatLogMessage("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response("response_body"));

        // then
        assertThat(logMessage, is(
                "returning response:" + NEW_LINE +
                        "" + NEW_LINE +
                        "\t{" + NEW_LINE +
                        "\t  \"statusCode\" : 200," + NEW_LINE +
                        "\t  \"body\" : \"response_body\"" + NEW_LINE +
                        "\t}" + NEW_LINE +
                        "" + NEW_LINE +
                        " for request:" + NEW_LINE +
                        " for action:"
        ));
    }

}
