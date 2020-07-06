package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.log.model.LogEntry;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class DashboardLogEntryDTOSerializerTest {

    final ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true,
        new DashboardLogEntryDTOSerializer(),
        new DashboardLogEntryDTOGroupSerializer(),
        new DescriptionSerializer(),
        new ThrowableSerializer()
    );

    private final long epochTime = 1593582678216L;
    private final String timeStamp = StringUtils.substringAfter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(epochTime)), "-");

    private Description getDescription(LogEntry logEntry) {
        return new LogMessageDescription(StringUtils.substringAfter(logEntry.getTimestamp(), "-"), logEntry.getType().name(), new DescriptionProcessor());
    }

    @Test
    public void shouldSerialiseEventWithSingleLineStringArgument() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                "one",
                "two"
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"one\\\"\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"two\\\"\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithMultiLineStringArgumentAsFirst() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                "line one" + NEW_LINE +
                    "line two" + NEW_LINE +
                    "line three",
                "two"
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"multiline\" : true," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : [ \"line one\", \"line two\", \"line three\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"two\\\"\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithMultiLineStringArgumentAsAll() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                "line one_one" + NEW_LINE +
                    "line one_two" + NEW_LINE +
                    "line one_three",
                "line two_one" + NEW_LINE +
                    "line two_two" + NEW_LINE +
                    "line two_three"
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"multiline\" : true," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : [ \"line one_one\", \"line one_two\", \"line one_three\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"multiline\" : true," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : [ \"line two_one\", \"line two_two\", \"line two_three\" ]" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithBecauseArgumentAsFirst() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                "line one" + NEW_LINE +
                    "line two" + NEW_LINE +
                    "line three",
                "two"
            )
            .setBecause(
                "line one" + NEW_LINE +
                    "line two" + NEW_LINE +
                    "line three"
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"because\" : true," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : [ \"line one\", \"line two\", \"line three\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"two\\\"\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithBecauseArgumentAsLast() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                "one",
                "line one" + NEW_LINE +
                    "line two" + NEW_LINE +
                    "line three"
            )
            .setBecause(
                "line one" + NEW_LINE +
                    "line two" + NEW_LINE +
                    "line three"
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"one\\\"\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"because\" : true," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : [ \"line one\", \"line two\", \"line three\" ]" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithBecauseArgumentNotMatching() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                "one",
                "line one" + NEW_LINE +
                    "line two" + NEW_LINE +
                    "line three"
            )
            .setBecause(
                "other line one" + NEW_LINE +
                    "other line two" + NEW_LINE +
                    "other line three"
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"one\\\"\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"multiline\" : true," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : [ \"line one\", \"line two\", \"line three\" ]" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithObjectArgumentAsFirst() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                request().withPath("somePath"),
                "two"
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"json\" : true," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : {" + NEW_LINE +
            "        \"path\" : \"somePath\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"two\\\"\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithObjectArgumentAsAll() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                request().withPath("somePath"),
                response().withBody("someBody")
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"json\" : true," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : {" + NEW_LINE +
            "        \"path\" : \"somePath\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"json\" : true," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : {" + NEW_LINE +
            "        \"body\" : \"someBody\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithTooManyArguments() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                "one",
                "two",
                "three"
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"one\\\"\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"two\\\"\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithTooFewArguments() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}with extra part{}")
            .setArguments(
                "one",
                "two"
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"one\\\"\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"two\\\"\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_2msg\"," + NEW_LINE +
            "      \"value\" : \"with extra part\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithMultiLineString() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some\n random{}formatted\n string{}")
            .setArguments(
                "one",
                "two"
            );

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some\\n random\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"one\\\"\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1msg\"," + NEW_LINE +
            "      \"value\" : \"formatted\\n string\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_1arg\"," + NEW_LINE +
            "      \"multiline\" : false," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : \"\\\"two\\\"\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }

    @Test
    public void shouldSerialiseEventWithThrowable() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some message")
            .setThrowable(new RuntimeException("TEST EXCEPTION"));

        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, containsString("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.id() + "_log\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"description\" : \"" + timeStamp + " TEMPLATE_GENERATED \"," + NEW_LINE +
            "    \"style\" : {" + NEW_LINE +
            "      \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "      \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "      \"overflow\" : \"auto\"," + NEW_LINE +
            "      \"color\" : \"rgb(241, 186, 27)\"," + NEW_LINE +
            "      \"paddingTop\" : \"4px\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"messageParts\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_0msg\"," + NEW_LINE +
            "      \"value\" : \"some message\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_throwable_msg\"," + NEW_LINE +
            "      \"value\" : \"exception:\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntry.id() + "_throwable_value\"," + NEW_LINE +
            "      \"multiline\" : true," + NEW_LINE +
            "      \"argument\" : true," + NEW_LINE +
            "      \"value\" : [ \"java.lang.RuntimeException: TEST EXCEPTION\", \"\\tat org.mockserver.dashboard.serializers.DashboardLogEntryDTOSerializerTest.shouldSerialiseEventWithThrowable"));
    }
}
