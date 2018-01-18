package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.mockserver.client.serialization.model.VerificationSequenceDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.validator.jsonschema.JsonSchemaVerificationSequenceValidator;
import org.mockserver.verify.VerificationSequence;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceSerializer implements Serializer<VerificationSequence> {
    private final MockServerLogger mockServerLogger;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonSchemaVerificationSequenceValidator verificationSequenceValidator;

    public VerificationSequenceSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        verificationSequenceValidator = new JsonSchemaVerificationSequenceValidator(mockServerLogger);
    }

    public String serialize(VerificationSequence verificationSequence) {
        try {
            return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(new VerificationSequenceDTO(verificationSequence));
        } catch (Exception e) {
            mockServerLogger.error("Exception while serializing verificationSequence to JSON with value " + verificationSequence, e);
            throw new RuntimeException("Exception while serializing verificationSequence to JSON with value " + verificationSequence, e);
        }
    }

    public VerificationSequence deserialize(String jsonVerificationSequence) {
        if (Strings.isNullOrEmpty(jsonVerificationSequence)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a verification sequence is required but value was \"" + String.valueOf(jsonVerificationSequence) + "\"");
        } else {
            String validationErrors = verificationSequenceValidator.isValid(jsonVerificationSequence);
            if (validationErrors.isEmpty()) {
                VerificationSequence verificationSequence = null;
                try {
                    VerificationSequenceDTO verificationDTO = objectMapper.readValue(jsonVerificationSequence, VerificationSequenceDTO.class);
                    if (verificationDTO != null) {
                        verificationSequence = verificationDTO.buildObject();
                    }
                } catch (Exception ioe) {
                    mockServerLogger.error("Exception while parsing [" + jsonVerificationSequence + "] for VerificationSequence", ioe);
                    throw new RuntimeException("Exception while parsing [" + jsonVerificationSequence + "] for VerificationSequence", ioe);
                }
                return verificationSequence;
            } else {
                mockServerLogger.info("Validation failed:{}" + NEW_LINE + " VerificationSequence:{}" + NEW_LINE + " Schema:{}", validationErrors, jsonVerificationSequence, verificationSequenceValidator.getSchema());
                throw new IllegalArgumentException(validationErrors);
            }
        }
    }

    @Override
    public Class<VerificationSequence> supportsType() {
        return VerificationSequence.class;
    }

}
