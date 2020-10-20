package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.log.model.LogEntry;
import org.mockserver.serialization.ObjectMapperFactory;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.OpenAPIDefinition.openAPI;

public class DescriptionProcessorTest {

    final ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true,
        new DashboardLogEntryDTOSerializer(),
        new DescriptionSerializer(),
        new ThrowableSerializer()
    );

    private final long epochTime = 1593582678216L;
    private final String timeStamp = StringUtils.substringAfter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(epochTime)), "-");

    @Test
    public void shouldSerialiseMultipleLogMessageDescriptions() throws JsonProcessingException {
        // given
        DescriptionProcessor descriptionProcessor = new DescriptionProcessor();
        List<Description> logMessageDescriptions = Arrays.asList(
            descriptionProcessor.description(new DashboardLogEntryDTO(new LogEntry().setEpochTime(epochTime).setType(EXPECTATION_RESPONSE))),
            descriptionProcessor.description(new DashboardLogEntryDTO(new LogEntry().setEpochTime(epochTime).setType(DEBUG))),
            descriptionProcessor.description(new DashboardLogEntryDTO(new LogEntry().setEpochTime(epochTime).setType(TEMPLATE_GENERATED)))
        );

        // when
        String json = objectWriter.writeValueAsString(logMessageDescriptions);

        // then
        assertThat(json, is("[ " +
            "\"" + timeStamp + " EXPECTATION_RESPONSE   \", " +
            "\"" + timeStamp + " DEBUG                  \", " +
            "\"" + timeStamp + " TEMPLATE_GENERATED     \" " +
            "]"));
    }

    @Test
    public void shouldSerialiseMultipleOpenAPIDefinitions() throws JsonProcessingException {
        // given
        DescriptionProcessor descriptionProcessor = new DescriptionProcessor();
        List<Description> logMessageDescriptions = Arrays.asList(
            descriptionProcessor.description(
                openAPI()
                    .withSpecUrlOrPayload("---" + NEW_LINE +
                        "openapi: 3.0.0" + NEW_LINE +
                        "paths:" + NEW_LINE +
                        "  \"/somePath\":" + NEW_LINE +
                        "    get:" + NEW_LINE +
                        "      operationId: someOperation" + NEW_LINE)
                    .withOperationId("showPetById")
            ),
            descriptionProcessor.description(
                openAPI()
                    .withSpecUrlOrPayload("---" + NEW_LINE +
                        "openapi: 3.0.0" + NEW_LINE +
                        "paths:" + NEW_LINE +
                        "  \"/somePath\":" + NEW_LINE +
                        "    get:" + NEW_LINE +
                        "      operationId: someOperation" + NEW_LINE)
            ),
            descriptionProcessor.description(
                openAPI()
                    .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
            ),
            descriptionProcessor.description(
                openAPI()
                    .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                    .withOperationId("someOtherOperationId")
            ),
            descriptionProcessor.description(
                openAPI()
                    .withSpecUrlOrPayload("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v3.0/petstore-expanded.yaml")
                    .withOperationId("someOtherOtherOperationId")
            ),
            descriptionProcessor.description(
                openAPI()
                    .withSpecUrlOrPayload("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v3.0/petstore-expanded.yaml")
            )
        );

        // when
        String json = objectWriter.writeValueAsString(logMessageDescriptions);

        // then
        assertThat(json, is("[ " +
            "{" + NEW_LINE +
            "  \"json\" : true," + NEW_LINE +
            "  \"object\" : {" + NEW_LINE +
            "    \"openapi\" : \"3.0.0\"," + NEW_LINE +
            "    \"servers\" : [ {" + NEW_LINE +
            "      \"url\" : \"/\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"paths\" : {" + NEW_LINE +
            "      \"/somePath\" : {" + NEW_LINE +
            "        \"get\" : {" + NEW_LINE +
            "          \"operationId\" : \"someOperation\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"components\" : { }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"first\" : \"spec \"," + NEW_LINE +
            "  \"second\" : \" showPetById\"" + NEW_LINE +
            "}, " +
            "{" + NEW_LINE +
            "  \"json\" : true," + NEW_LINE +
            "  \"object\" : {" + NEW_LINE +
            "    \"openapi\" : \"3.0.0\"," + NEW_LINE +
            "    \"servers\" : [ {" + NEW_LINE +
            "      \"url\" : \"/\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"paths\" : {" + NEW_LINE +
            "      \"/somePath\" : {" + NEW_LINE +
            "        \"get\" : {" + NEW_LINE +
            "          \"operationId\" : \"someOperation\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"components\" : { }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"first\" : \"spec \"," + NEW_LINE +
            "  \"second\" : \"            \"" + NEW_LINE +
            "}, " +
            "\"openapi_petstore_example.json                      \", " +
            "\"openapi_petstore_example.json  someOtherOperationId\", " +
            "\"petstore-expanded.yaml    someOtherOtherOperationId\", " +
            "\"petstore-expanded.yaml                             \" " +
            "]"));
    }

    @Test
    public void shouldSerialiseMultipleHttpRequestDefinitions() throws JsonProcessingException {
        // given
        DescriptionProcessor descriptionProcessor = new DescriptionProcessor();
        List<Description> logMessageDescriptions = Arrays.asList(
            descriptionProcessor.description(
                request()
                    .withPath("somePathOne")
                    .withMethod("POST")
            ),
            descriptionProcessor.description(
                request()
                    .withPath("veryLongPathThatHasASillyLength")
            ),
            descriptionProcessor.description(
                request()
                    .withMethod("POST")
            ),
            descriptionProcessor.description(
                request()
                    .withPath("somePathOne")
            ),
            descriptionProcessor.description(
                request()
            )
        );

        // when
        String json = objectWriter.writeValueAsString(logMessageDescriptions);

        // then
        assertThat(json, is("[ " +
            "\"POST                  somePathOne\", " +
            "\"  veryLongPathThatHasASillyLength\", " +
            "\"POST                             \", " +
            "\"                      somePathOne\", " +
            "\"                                 \" " +
            "]"));
    }

    @Test
    public void shouldSerialiseMultipleHttpRequestAndOpenAPIDefinitions() throws JsonProcessingException {
        // given
        DescriptionProcessor descriptionProcessor = new DescriptionProcessor();
        List<Description> logMessageDescriptions = Arrays.asList(
            descriptionProcessor.description(
                request()
                    .withPath("somePathOne")
                    .withMethod("POST")
            ),
            descriptionProcessor.description(
                request()
                    .withPath("veryLongPathThatHasASillyLength")
            ),
            descriptionProcessor.description(
                request()
                    .withMethod("POST")
            ),
            descriptionProcessor.description(
                request()
                    .withPath("somePathOne")
            ),
            descriptionProcessor.description(
                request()
            ),
            descriptionProcessor.description(
                openAPI()
                    .withSpecUrlOrPayload("---" + NEW_LINE +
                        "openapi: 3.0.0" + NEW_LINE +
                        "paths:" + NEW_LINE +
                        "  \"/somePath\":" + NEW_LINE +
                        "    get:" + NEW_LINE +
                        "      operationId: someOperation" + NEW_LINE)
            ),
            descriptionProcessor.description(
                openAPI()
                    .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
            ),
            descriptionProcessor.description(
                openAPI()
                    .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                    .withOperationId("someOtherOperationId")
            )
        );

        // when
        String json = objectWriter.writeValueAsString(logMessageDescriptions);

        // then
        assertThat(json, is("[ " +
            "\"POST                  somePathOne\", " +
            "\"  veryLongPathThatHasASillyLength\", " +
            "\"POST                             \", " +
            "\"                      somePathOne\", " +
            "\"                                 \", " +
            "{" + NEW_LINE +
            "  \"json\" : true," + NEW_LINE +
            "  \"object\" : {" + NEW_LINE +
            "    \"openapi\" : \"3.0.0\"," + NEW_LINE +
            "    \"servers\" : [ {" + NEW_LINE +
            "      \"url\" : \"/\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"paths\" : {" + NEW_LINE +
            "      \"/somePath\" : {" + NEW_LINE +
            "        \"get\" : {" + NEW_LINE +
            "          \"operationId\" : \"someOperation\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"components\" : { }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"first\" : \"spec \"," + NEW_LINE +
            "  \"second\" : \" \"" + NEW_LINE +
            "}, " +
            "\"openapi_petstore_example.json                      \", " +
            "\"openapi_petstore_example.json  someOtherOperationId\" " +
            "]"));
    }

}