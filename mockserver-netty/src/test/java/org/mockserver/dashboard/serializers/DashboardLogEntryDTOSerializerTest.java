package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

public class DashboardLogEntryDTOSerializerTest {

    final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper(
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
            .setMessageFormat("some random {} formatted string {}")
            .setArguments("one", "two")
            .setThrowable(new RuntimeException("TEST_EXCEPTION"));

        // when
        String json = objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(new DashboardLogEntryDTO(logEntry));

        // then
        assertThat(json, containsString("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"logLevel\" : \"WARN\"," + NEW_LINE +
            "    \"timestamp\" : \"" + logEntry.getTimestamp() + "\"," + NEW_LINE +
            "    \"type\" : \"TEMPLATE_GENERATED\"," + NEW_LINE +
            "    \"httpRequests\" : [ {" + NEW_LINE +
            "      \"path\" : \"request_one\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"path\" : \"request_two\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"httpResponse\" : {" + NEW_LINE +
            "      \"statusCode\" : 200," + NEW_LINE +
            "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "      \"body\" : \"response_one\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"httpError\" : {" + NEW_LINE +
            "      \"dropConnection\" : true" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"expectation\" : {" + NEW_LINE +
            "      \"id\" : \"key_one\"," + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"request_one\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"times\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"timeToLive\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"response_one\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"message\" : [ \"some random \", \"\", \"   one\", \"\", \" formatted string \", \"\", \"   two\" ]," + NEW_LINE +
            "    \"messageFormat\" : \"some random {} formatted string {}\"," + NEW_LINE +
            "    \"arguments\" : [ \"one\", \"two\" ]," + NEW_LINE +
            "    \"throwable\" : [ \"java.lang.RuntimeException: TEST_EXCEPTION\", \"\\tat "));
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
                    .withBody("some random string body"), Times.once(), TimeToLive.unlimited())
                    .withId("key_one")
                    .thenRespond(response("response_one")
                        .withBody(json("{\"derivationMode\":\"HASHED\",\"deviceUrn\":\"411323184fd0c2bf724713149de137f4dde072c1fd31f0e29256800a1b2c1afc\",\"ciphertextBytesFormat\":\"JSON\"}")))
            );

        // when
        String json = objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(new DashboardLogEntryDTO(logEntry));

        // then
        assertThat(json, containsString("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"logLevel\" : \"WARN\"," + NEW_LINE +
            "    \"timestamp\" : \"" + logEntry.getTimestamp() + "\"," + NEW_LINE +
            "    \"type\" : \"FORWARDED_REQUEST\"," + NEW_LINE +
            "    \"httpRequests\" : [ {" + NEW_LINE +
            "      \"path\" : \"request_one\"," + NEW_LINE +
            "      \"body\" : {" + NEW_LINE +
            "        \"derivationMode\" : \"HASHED\"," + NEW_LINE +
            "        \"deviceUrn\" : \"411323184fd0c2bf724713149de137f4dde072c1fd31f0e29256800a1b2c1afc\"," + NEW_LINE +
            "        \"ciphertextBytesFormat\" : \"JSON\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"path\" : \"request_two\"," + NEW_LINE +
            "      \"body\" : \"some random string body\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"httpResponse\" : {" + NEW_LINE +
            "      \"statusCode\" : 200," + NEW_LINE +
            "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "      \"body\" : {" + NEW_LINE +
            "        \"derivationMode\" : \"HASHED\"," + NEW_LINE +
            "        \"deviceUrn\" : \"411323184fd0c2bf724713149de137f4dde072c1fd31f0e29256800a1b2c1afc\"," + NEW_LINE +
            "        \"ciphertextBytesFormat\" : \"JSON\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"expectation\" : {" + NEW_LINE +
            "      \"id\" : \"key_one\"," + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"request_one\"," + NEW_LINE +
            "        \"body\" : \"some random string body\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"times\" : {" + NEW_LINE +
            "        \"remainingTimes\" : 1" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"timeToLive\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "          \"type\" : \"JSON\"," + NEW_LINE +
            "          \"json\" : \"{\\\"derivationMode\\\":\\\"HASHED\\\",\\\"deviceUrn\\\":\\\"411323184fd0c2bf724713149de137f4dde072c1fd31f0e29256800a1b2c1afc\\\",\\\"ciphertextBytesFormat\\\":\\\"JSON\\\"}\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }
}
