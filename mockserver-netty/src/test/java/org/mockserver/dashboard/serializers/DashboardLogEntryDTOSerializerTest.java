package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import io.netty.buffer.ByteBufUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.log.model.LogEntry;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
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

    @Test
    public void shouldSerialiseBinaryForward() throws JsonProcessingException {
        // given
        byte[] binaryRequest = ("" +
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
        byte[] binaryResponse = ("" +
            "You bite up because of your lower jaw." + NEW_LINE +
            "I am counting my calories, yet I really want dessert." + NEW_LINE +
            "The old apple revels in its authority." + NEW_LINE +
            "He is good at eating pickles and telling women about his emotional problems." + NEW_LINE +
            "Please tell me you don't work in a morgue." + NEW_LINE +
            "No matter how beautiful the sunset, it saddened her knowing she was one day older." + NEW_LINE +
            "She cried diamonds." + NEW_LINE +
            "My Mum tries to be cool by saying that she likes all the same things that I do." + NEW_LINE +
            "She works two jobs to make ends meet; at least, that was her reason for not having time to join us." + NEW_LINE +
            "The knives were out and she was sharpening hers."
        ).getBytes(StandardCharsets.UTF_8);
        Splitter splitter = Splitter.fixedLength(64);
        LogEntry logEntry = new LogEntry()
            .setType(FORWARDED_REQUEST)
            .setLogLevel(Level.INFO)
            .setEpochTime(epochTime)
            .setMessageFormat("returning binary response:{}from:{}for forwarded binary request:{}")
            .setArguments(Joiner.on("\n").join(splitter.split(ByteBufUtil.hexDump(binaryResponse))), new InetSocketAddress(1234), splitter.split(ByteBufUtil.hexDump(binaryRequest)));


        // when
        String json = objectWriter.writeValueAsString(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // then
        assertThat(json, is("{\n" +
            "  \"key\" : \"" + logEntry.id() + "_log\",\n" +
            "  \"value\" : {\n" +
            "    \"description\" : \"" + timeStamp + " FORWARDED_REQUEST \",\n" +
            "    \"style\" : {\n" +
            "      \"paddingBottom\" : \"4px\",\n" +
            "      \"whiteSpace\" : \"nowrap\",\n" +
            "      \"overflow\" : \"auto\",\n" +
            "      \"color\" : \"rgb(152, 208, 255)\",\n" +
            "      \"paddingTop\" : \"4px\"\n" +
            "    },\n" +
            "    \"messageParts\" : [ {\n" +
            "      \"key\" : \"" + logEntry.id() + "_0msg\",\n" +
            "      \"value\" : \"returning binary response:\"\n" +
            "    }, {\n" +
            "      \"key\" : \"" + logEntry.id() + "_0arg\",\n" +
            "      \"multiline\" : true,\n" +
            "      \"argument\" : true,\n" +
            "      \"value\" : [ \"596f7520626974652075702062656361757365206f6620796f7572206c6f7765\", \"72206a61772e0a4920616d20636f756e74696e67206d792063616c6f72696573\", \"2c207965742049207265616c6c792077616e7420646573736572742e0a546865\", \"206f6c64206170706c6520726576656c7320696e2069747320617574686f7269\", \"74792e0a486520697320676f6f6420617420656174696e67207069636b6c6573\", \"20616e642074656c6c696e6720776f6d656e2061626f75742068697320656d6f\", \"74696f6e616c2070726f626c656d732e0a506c656173652074656c6c206d6520\", \"796f7520646f6e277420776f726b20696e2061206d6f726775652e0a4e6f206d\", \"617474657220686f772062656175746966756c207468652073756e7365742c20\", \"69742073616464656e656420686572206b6e6f77696e67207368652077617320\", \"6f6e6520646179206f6c6465722e0a536865206372696564206469616d6f6e64\", \"732e0a4d79204d756d20747269657320746f20626520636f6f6c206279207361\", \"79696e67207468617420736865206c696b657320616c6c207468652073616d65\", \"207468696e67732074686174204920646f2e0a53686520776f726b732074776f\", \"206a6f627320746f206d616b6520656e6473206d6565743b206174206c656173\", \"742c2074686174207761732068657220726561736f6e20666f72206e6f742068\", \"6176696e672074696d6520746f206a6f696e2075732e0a546865206b6e697665\", \"732077657265206f757420616e642073686520776173207368617270656e696e\", \"6720686572732e\" ]\n" +
            "    }, {\n" +
            "      \"key\" : \"" + logEntry.id() + "_1msg\",\n" +
            "      \"value\" : \"from:\"\n" +
            "    }, {\n" +
            "      \"key\" : \"" + logEntry.id() + "_1arg\",\n" +
            "      \"json\" : true,\n" +
            "      \"argument\" : true,\n" +
            "      \"value\" : \"0.0.0.0:1234\"\n" +
            "    }, {\n" +
            "      \"key\" : \"" + logEntry.id() + "_2msg\",\n" +
            "      \"value\" : \"for forwarded binary request:\"\n" +
            "    }, {\n" +
            "      \"key\" : \"" + logEntry.id() + "_2arg\",\n" +
            "      \"json\" : true,\n" +
            "      \"argument\" : true,\n" +
            "      \"value\" : [ \"54686520747269636b20746f2067657474696e67206b69647320746f20656174\", \"20616e797468696e6720697320746f207075742063617463687570206f6e2069\", \"742e0a49206c696b6520746f206c6561766520776f726b206166746572206d79\", \"2065696768742d686f7572207465612d627265616b2e0a4865206261726b6564\", \"206f726465727320617420686973206461756768746572732062757420746865\", \"79206a75737420737461726564206261636b207769746820616d7573656d656e\", \"742e0a41732074686520796561727320706173732062792c20776520616c6c20\", \"6b6e6f77206f776e657273206c6f6f6b206d6f726520616e64206d6f7265206c\", \"696b6520746865697220646f67732e0a4974206d757374206265206669766520\", \"6f27636c6f636b20736f6d6577686572652e0a54686520666174686572206469\", \"656420647572696e67206368696c6462697274682e0a48652068616420636f6e\", \"636c7564656420746861742070696773206d7573742062652061626c6520746f\", \"20666c7920696e20486f672048656176656e2e0a546865207065742073686f70\", \"2073746f636b732065766572797468696e6720796f75206e65656420746f206b\", \"65657020796f757220616e61636f6e64612068617070792e0a57652073686f75\", \"6c6420706c61792077697468206c65676f732061742063616d702e0a49742064\", \"6f65736e277420736f756e64206c696b6520746861742077696c6c2065766572\", \"206265206f6e206d792074726176656c206c6973742e\" ]\n" +
            "    } ]\n" +
            "  }\n" +
            "}"));
    }
}
