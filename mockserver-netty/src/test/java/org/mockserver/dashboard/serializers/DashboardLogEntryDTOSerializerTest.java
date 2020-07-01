package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.log.model.LogEntry;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

public class DashboardLogEntryDTOSerializerTest {

    // TODO(jamesdbloom)
    //    LogEntry Serialisation
    //    arguments type:
    //    - because
    //    - multiline string
    //    - singleline string
    //    - json
    //    List Construction (to serialised: payload, key, type, description & value)
    //    - log entries
    //    - expectations with http request
    //    - expectations with open api
    //    - recorded requests
    //    - proxied requests
    //    - request filtering (i.e. by path and method)

    final ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true,
        new DashboardLogEntryDTOSerializer(),
        new ThrowableSerializer()
    );

    @Test
    public void shouldSerialiseFullEvent() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(System.currentTimeMillis())
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setHttpRequests(new HttpRequest[]{request("request_one"), request("request_two")})
            .setHttpResponse(response("response_one"))
            .setHttpError(error().withDropConnection(true))
            .setExpectation(new Expectation(request("request_one")).withId("key_one").thenRespond(response("response_one")))
            .setMessageFormat("some random{}formatted string{}")
            .setArguments("one", "two")
            .setThrowable(new RuntimeException("TEST_EXCEPTION"));

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry));

        // then
        assertThat(json, containsString("{\n" +
            "  \"key\" : \"" + logEntry.id() + "_log\",\n" +
            "  \"value\" : {\n" +
            "    \"style\" : {\n" +
            "      \"paddingBottom\" : \"4px\",\n" +
            "      \"whiteSpace\" : \"nowrap\",\n" +
            "      \"overflow\" : \"auto\",\n" +
            "      \"color\" : \"rgb(241, 186, 27)\",\n" +
            "      \"paddingTop\" : \"4px\"\n" +
            "    },\n" +
            "    \"messageParts\" : [ {\n" +
            "      \"key\" : \"" + logEntry.id() + "_0msg\",\n" +
            "      \"value\" : \"some random\"\n" +
            "    }, {\n" +
            "      \"key\" : \"" + logEntry.id() + "_0arg\",\n" +
            "      \"multiline\" : false,\n" +
            "      \"argument\" : true,\n" +
            "      \"value\" : \"one\"\n" +
            "    }, {\n" +
            "      \"key\" : \"" + logEntry.id() + "_1msg\",\n" +
            "      \"value\" : \"formatted string\"\n" +
            "    }, {\n" +
            "      \"key\" : \"" + logEntry.id() + "_1arg\",\n" +
            "      \"multiline\" : false,\n" +
            "      \"argument\" : true,\n" +
            "      \"value\" : \"two\"\n" +
            "    } ]\n" +
            "  }\n" +
            "}"));
    }

    @Test
    public void shouldSerialiseStringBody() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(System.currentTimeMillis())
            .setType(LogEntry.LogMessageType.FORWARDED_REQUEST)
            .setHttpRequests(new HttpRequest[]{
                request("request_one")
                    .withBody(json("{\"derivationMode\":\"HASHED\",\"deviceUrn\":\"411323184fd0c2bf724713149de137f4dde072c1fd31f0e29256800a1b2c1afc\",\"ciphertextBytesFormat\":\"JSON\"}")),
                request("request_two")
                    .withBody("some random string body")
            })
            .setHttpResponse(
                response("response_one")
                    .withBody(json("{\"derivationMode\":\"HASHED\",\"deviceUrn\":\"411323184fd0c2bf724713149de137f4dde072c1fd31f0e29256800a1b2c1afc\",\"ciphertextBytesFormat\":\"JSON\"}"))
            )
            .setExpectation(
                new Expectation(request("request_one")
                    .withBody("some random string body"), Times.once(), TimeToLive.unlimited(), 0)
                    .withId("key_one")
                    .thenRespond(response("response_one")
                        .withBody(json("{\"derivationMode\":\"HASHED\",\"deviceUrn\":\"411323184fd0c2bf724713149de137f4dde072c1fd31f0e29256800a1b2c1afc\",\"ciphertextBytesFormat\":\"JSON\"}")))
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry));

        // then
        assertThat(json, containsString("{\n" +
            "  \"key\" : \"" + logEntry.id() + "_log\",\n" +
            "  \"value\" : {\n" +
            "    \"style\" : {\n" +
            "      \"paddingBottom\" : \"4px\",\n" +
            "      \"whiteSpace\" : \"nowrap\",\n" +
            "      \"overflow\" : \"auto\",\n" +
            "      \"color\" : \"rgb(152, 208, 255)\",\n" +
            "      \"paddingTop\" : \"4px\"\n" +
            "    }\n" +
            "  }\n" +
            "}"));
    }
}
