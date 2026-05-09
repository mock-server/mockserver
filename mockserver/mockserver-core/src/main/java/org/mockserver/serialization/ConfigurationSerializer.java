package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.ConfigurationDTO;
import org.slf4j.event.Level;

@SuppressWarnings("FieldMayBeFinal")
public class ConfigurationSerializer implements Serializer<Configuration> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true, false);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public ConfigurationSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public String serialize(Configuration configuration) {
        try {
            return objectWriter.writeValueAsString(new ConfigurationDTO(configuration));
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing configuration to JSON with value " + configuration)
                    .setThrowable(throwable)
            );
            throw new RuntimeException("Exception while serializing configuration to JSON with value " + configuration, throwable);
        }
    }

    public Configuration deserialize(String jsonConfiguration) {
        Configuration configuration = null;
        if (jsonConfiguration != null && !jsonConfiguration.isEmpty()) {
            try {
                ConfigurationDTO configurationDTO = objectMapper.readValue(jsonConfiguration, ConfigurationDTO.class);
                if (configurationDTO != null) {
                    configuration = configurationDTO.buildObject();
                }
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while parsing configuration JSON")
                        .setThrowable(throwable)
                );
                throw new IllegalArgumentException("exception while parsing configuration JSON", throwable);
            }
        }
        return configuration;
    }

    @Override
    public Class<Configuration> supportsType() {
        return Configuration.class;
    }
}
