package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.dashboard.model.LogEntryDTO;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class LogEntryDTOSerializerTest {

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
            .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_one")))
            .setMessageFormat("some random {} formatted string {}")
            .setArguments("one", "two")
            .setThrowable(new RuntimeException("TEST_EXCEPTION"));

        // when
        String json = ObjectMapperFactory
            .createObjectMapper(
                new LogEntryDTOSerializer(),
                new ThrowableSerializer()
            )
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(new LogEntryDTO(logEntry));

        // then
        assertThat(json, containsString("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.key() + "\"," + NEW_LINE +
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
}
