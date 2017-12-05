package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.mockserver.client.serialization.model.VerificationSequenceDTO;
import org.mockserver.validator.jsonschema.JsonSchemaVerificationSequenceValidator;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceSerializer implements Serializer<VerificationSequence> {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonSchemaVerificationSequenceValidator verificationSequenceValidator = new JsonSchemaVerificationSequenceValidator();

    public String serialize(VerificationSequence verificationSequence) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new VerificationSequenceDTO(verificationSequence));
        } catch (Exception e) {
            logger.error("Exception while serializing verificationSequence to JSON with value " + verificationSequence, e);
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
                    logger.info("Exception while parsing [" + jsonVerificationSequence + "] for VerificationSequence", ioe);
                    throw new RuntimeException("Exception while parsing [" + jsonVerificationSequence + "] for VerificationSequence", ioe);
                }
                return verificationSequence;
            } else {
                logger.info("Validation failed:" + NEW_LINE + validationErrors + NEW_LINE + "-- Expectation:" + NEW_LINE + jsonVerificationSequence + NEW_LINE + "-- Schema:" + NEW_LINE + verificationSequenceValidator.getSchema());
                throw new IllegalArgumentException(validationErrors);
            }
        }
    }

    @Override
    public Class<VerificationSequence> supportsType() {
        return VerificationSequence.class;
    }

}
