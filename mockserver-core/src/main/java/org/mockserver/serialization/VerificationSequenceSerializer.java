package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.VerificationSequenceDTO;
import org.mockserver.validator.jsonschema.JsonSchemaVerificationSequenceValidator;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.event.Level;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.VERIFICATION_FAILED;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceSerializer implements Serializer<VerificationSequence> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonSchemaVerificationSequenceValidator verificationSequenceValidator;

    public VerificationSequenceSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        verificationSequenceValidator = new JsonSchemaVerificationSequenceValidator(mockServerLogger);
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
            String validationErrors = verificationSequenceValidator.isValid(jsonVerificationSequence);
            if (validationErrors.isEmpty()) {
                VerificationSequence verificationSequence = null;
                try {
                    VerificationSequenceDTO verificationDTO = objectMapper.readValue(jsonVerificationSequence, VerificationSequenceDTO.class);
                    if (verificationDTO != null) {
                        verificationSequence = verificationDTO.buildObject();
                    }
                } catch (Exception e) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for VerificationSequence")
                            .setArguments(jsonVerificationSequence)
                            .setThrowable(e)
                    );
                    throw new RuntimeException("Exception while parsing [" + jsonVerificationSequence + "] for VerificationSequence", e);
                }
                return verificationSequence;
            } else {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(VERIFICATION_FAILED)
                        .setLogLevel(Level.INFO)
                        .setHttpRequest(request())
                        .setMessageFormat("validation failed:{}verification sequence:{}")
                        .setArguments(validationErrors, jsonVerificationSequence)
                );
                throw new IllegalArgumentException(validationErrors);
            }
        }
    }

    @Override
    public Class<VerificationSequence> supportsType() {
        return VerificationSequence.class;
    }

}
