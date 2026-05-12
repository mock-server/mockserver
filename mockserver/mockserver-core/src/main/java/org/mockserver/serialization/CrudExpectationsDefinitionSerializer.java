package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.CrudExpectationsDefinition;
import org.slf4j.event.Level;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;

@SuppressWarnings("FieldMayBeFinal")
public class CrudExpectationsDefinitionSerializer implements Serializer<CrudExpectationsDefinition> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true, false);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public CrudExpectationsDefinitionSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public String serialize(CrudExpectationsDefinition crudDefinition) {
        if (crudDefinition != null) {
            try {
                return objectWriter.writeValueAsString(crudDefinition);
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while serializing CrudExpectationsDefinition to JSON with value " + crudDefinition)
                        .setThrowable(e)
                );
                throw new RuntimeException("Exception while serializing CrudExpectationsDefinition to JSON with value " + crudDefinition, e);
            }
        } else {
            return "";
        }
    }

    public CrudExpectationsDefinition deserialize(String jsonCrudDefinition) {
        if (isBlank(jsonCrudDefinition)) {
            throw new IllegalArgumentException(
                "1 error:" + NEW_LINE
                    + " - a CRUD expectations definition is required but value was \"" + jsonCrudDefinition + "\""
            );
        } else {
            try {
                return objectMapper.readValue(jsonCrudDefinition, CrudExpectationsDefinition.class);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while parsing{}for CrudExpectationsDefinition " + throwable.getMessage())
                        .setArguments(jsonCrudDefinition)
                        .setThrowable(throwable)
                );
                throw new IllegalArgumentException("exception while parsing [" + jsonCrudDefinition + "] for CrudExpectationsDefinition", throwable);
            }
        }
    }

    @Override
    public Class<CrudExpectationsDefinition> supportsType() {
        return CrudExpectationsDefinition.class;
    }
}
