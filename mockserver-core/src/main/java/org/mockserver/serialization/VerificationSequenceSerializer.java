package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.VerificationSequenceDTO;
import org.mockserver.validator.jsonschema.JsonSchemaVerificationSequenceValidator;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.event.Level;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;
import static org.mockserver.validator.jsonschema.JsonSchemaVerificationSequenceValidator.jsonSchemaVerificationSequenceValidator;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("FieldMayBeFinal")
public class VerificationSequenceSerializer implements Serializer<VerificationSequence> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonSchemaVerificationSequenceValidator verificationSequenceValidator;

    public VerificationSequenceSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    private JsonSchemaVerificationSequenceValidator getValidator() {
        if (verificationSequenceValidator == null) {
            verificationSequenceValidator = jsonSchemaVerificationSequenceValidator(mockServerLogger);
        }
        return verificationSequenceValidator;
    }

    public String serialize(VerificationSequence verificationSequence) {
        try {
            return objectWriter.writeValueAsString(new VerificationSequenceDTO(verificationSequence));
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing verificationSequence to JSON with value " + verificationSequence)
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing verificationSequence to JSON with value " + verificationSequence, e);
        }
    }

    public VerificationSequence deserialize(String jsonVerificationSequence) {
        if (isBlank(jsonVerificationSequence)) {
            throw new IllegalArgumentException(
                "1 error:" + NEW_LINE +
                    " - a verification sequence is required but value was \"" + jsonVerificationSequence + "\"" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            );
        } else {
            String validationErrors = getValidator().isValid(jsonVerificationSequence);
            if (validationErrors.isEmpty()) {
                VerificationSequence verificationSequence = null;
                try {
                    VerificationSequenceDTO verificationDTO = objectMapper.readValue(jsonVerificationSequence, VerificationSequenceDTO.class);
                    if (verificationDTO != null) {
                        verificationSequence = verificationDTO.buildObject();
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for VerificationSequence " + throwable.getMessage())
                            .setArguments(jsonVerificationSequence)
                            .setThrowable(throwable)
                    );
                    throw new IllegalArgumentException("exception while parsing [" + jsonVerificationSequence + "] for VerificationSequence", throwable);
                }
                return verificationSequence;
            } else {
                throw new IllegalArgumentException(StringUtils.removeEndIgnoreCase(formatLogMessage("incorrect verification sequence json format for:{}schema validation errors:{}", jsonVerificationSequence, validationErrors), "\n"));
            }
        }
    }

    @Override
    public Class<VerificationSequence> supportsType() {
        return VerificationSequence.class;
    }

}
