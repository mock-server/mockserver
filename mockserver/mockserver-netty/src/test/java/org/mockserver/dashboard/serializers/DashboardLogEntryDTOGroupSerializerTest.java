package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.dashboard.model.DashboardLogEntryDTOGroup;
import org.mockserver.log.model.LogEntry;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;

public class DashboardLogEntryDTOGroupSerializerTest {

    final ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true,
            false, new DashboardLogEntryDTOSerializer(),
        new DashboardLogEntryDTOGroupSerializer(),
        new DescriptionSerializer(),
        new ThrowableSerializer()
    );

    private final long epochTime = 1593582678216L;
    private final String timeStamp = StringUtils.substringAfter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(epochTime)), "-");
    private DescriptionProcessor descriptionProcessor;

    private Description getDescription(LogEntry logEntry) {
        return new LogMessageDescription(StringUtils.substringAfter(logEntry.getTimestamp(), "-"), logEntry.getType().name(), descriptionProcessor);
    }

    @Before
    public void resetDescriptionProcessor() {
        descriptionProcessor = new DescriptionProcessor();
    }

    @Test
    public void shouldSerialiseGroupWithSingleEvent() throws JsonProcessingException {
        // given
        DashboardLogEntryDTOGroup dashboardLogEntryDTOGroup = new DashboardLogEntryDTOGroup(descriptionProcessor);
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                "one",
                "two"
            );
        dashboardLogEntryDTOGroup.getLogEntryDTOS().add(new DashboardLogEntryDTO(logEntry).setDescription(getDescription(logEntry)));

        // when
        String json = objectWriter.writeValueAsString(dashboardLogEntryDTOGroup);

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
    public void shouldSerialiseGroupWithMultipleEvents() throws JsonProcessingException {
        // given
        DashboardLogEntryDTOGroup dashboardLogEntryDTOGroup = new DashboardLogEntryDTOGroup(descriptionProcessor);
        LogEntry logEntryForwardRequest = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.FORWARDED_REQUEST)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                "one",
                "two"
            );
        dashboardLogEntryDTOGroup.getLogEntryDTOS().add(new DashboardLogEntryDTO(logEntryForwardRequest).setDescription(getDescription(logEntryForwardRequest)));
        LogEntry logEntryExpectationNotMatched = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(epochTime)
            .setType(LogEntry.LogMessageType.EXPECTATION_NOT_MATCHED)
            .setMessageFormat("some random{}formatted string{}")
            .setArguments(
                "one",
                "two"
            );
        dashboardLogEntryDTOGroup.getLogEntryDTOS().add(new DashboardLogEntryDTO(logEntryExpectationNotMatched).setDescription(getDescription(logEntryExpectationNotMatched)));

        // when
        String json = objectWriter.writeValueAsString(dashboardLogEntryDTOGroup);

        // then
        assertThat(json, is("{\n" +
            "  \"key\" : \"" + logEntryForwardRequest.id() + "_log_group\",\n" +
            "  \"group\" : {\n" +
            "    \"key\" : \"" + logEntryForwardRequest.id() + "_log\",\n" +
            "    \"value\" : {\n" +
            "      \"description\" : \"" + timeStamp + " FORWARDED_REQUEST   \",\n" +
            "      \"style\" : {\n" +
            "        \"paddingBottom\" : \"4px\",\n" +
            "        \"whiteSpace\" : \"nowrap\",\n" +
            "        \"overflow\" : \"auto\",\n" +
            "        \"color\" : \"rgb(152, 208, 255)\",\n" +
            "        \"paddingTop\" : \"4px\"\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  \"value\" : [ {\n" +
            "    \"key\" : \"" + logEntryForwardRequest.id() + "_log\",\n" +
            "    \"value\" : {\n" +
            "      \"description\" : \"" + timeStamp + " FORWARDED_REQUEST   \",\n" +
            "      \"style\" : {\n" +
            "        \"paddingBottom\" : \"4px\",\n" +
            "        \"whiteSpace\" : \"nowrap\",\n" +
            "        \"overflow\" : \"auto\",\n" +
            "        \"color\" : \"rgb(152, 208, 255)\",\n" +
            "        \"paddingTop\" : \"4px\"\n" +
            "      },\n" +
            "      \"messageParts\" : [ {\n" +
            "        \"key\" : \"" + logEntryForwardRequest.id() + "_0msg\",\n" +
            "        \"value\" : \"some random\"\n" +
            "      }, {\n" +
            "        \"key\" : \"" + logEntryForwardRequest.id() + "_0arg\",\n" +
            "        \"multiline\" : false,\n" +
            "        \"argument\" : true,\n" +
            "        \"value\" : \"\\\"one\\\"\"\n" +
            "      }, {\n" +
            "        \"key\" : \"" + logEntryForwardRequest.id() + "_1msg\",\n" +
            "        \"value\" : \"formatted string\"\n" +
            "      }, {\n" +
            "        \"key\" : \"" + logEntryForwardRequest.id() + "_1arg\",\n" +
            "        \"multiline\" : false,\n" +
            "        \"argument\" : true,\n" +
            "        \"value\" : \"\\\"two\\\"\"\n" +
            "      } ]\n" +
            "    }\n" +
            "  }, {\n" +
            "    \"key\" : \"" + logEntryExpectationNotMatched.id() + "_log\",\n" +
            "    \"value\" : {\n" +
            "      \"description\" : \"" + timeStamp + " EXPECTATION_NOT_MATCHED \",\n" +
            "      \"style\" : {\n" +
            "        \"paddingBottom\" : \"4px\",\n" +
            "        \"whiteSpace\" : \"nowrap\",\n" +
            "        \"overflow\" : \"auto\",\n" +
            "        \"color\" : \"rgb(204,165,163)\",\n" +
            "        \"paddingTop\" : \"4px\"\n" +
            "      },\n" +
            "      \"messageParts\" : [ {\n" +
            "        \"key\" : \"" + logEntryExpectationNotMatched.id() + "_0msg\",\n" +
            "        \"value\" : \"some random\"\n" +
            "      }, {\n" +
            "        \"key\" : \"" + logEntryExpectationNotMatched.id() + "_0arg\",\n" +
            "        \"multiline\" : false,\n" +
            "        \"argument\" : true,\n" +
            "        \"value\" : \"\\\"one\\\"\"\n" +
            "      }, {\n" +
            "        \"key\" : \"" + logEntryExpectationNotMatched.id() + "_1msg\",\n" +
            "        \"value\" : \"formatted string\"\n" +
            "      }, {\n" +
            "        \"key\" : \"" + logEntryExpectationNotMatched.id() + "_1arg\",\n" +
            "        \"multiline\" : false,\n" +
            "        \"argument\" : true,\n" +
            "        \"value\" : \"\\\"two\\\"\"\n" +
            "      } ]\n" +
            "    }\n" +
            "  } ]\n" +
            "}"));
    }

}