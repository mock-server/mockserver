package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.mockserver.client.serialization.model.VerificationDTO;
import org.mockserver.validator.jsonschema.JsonSchemaVerificationValidator;
import org.mockserver.verify.Verification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class VerificationSerializer implements Serializer<Verification> {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonSchemaVerificationValidator verificationValidator = new JsonSchemaVerificationValidator();

    public String serialize(Verification verification) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new VerificationDTO(verification));
        } catch (Exception e) {
            logger.error("Exception while serializing verification to JSON with value " + verification, e);
            throw new RuntimeException("Exception while serializing verification to JSON with value " + verification, e);
        }
    }

    public Verification deserialize(String jsonVerification) {
        if (Strings.isNullOrEmpty(jsonVerification)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a verification is required but value was \"" + String.valueOf(jsonVerification) + "\"");
        } else {
            String validationErrors = verificationValidator.isValid(jsonVerification);
            if (validationErrors.isEmpty()) {
                Verification verification = null;
                try {
                    VerificationDTO verificationDTO = objectMapper.readValue(jsonVerification, VerificationDTO.class);
                    if (verificationDTO != null) {
                        verification = verificationDTO.buildObject();
                    }
                } catch (Exception e) {
                    logger.info("Exception while parsing [" + jsonVerification + "] for Verification", e);
                    throw new RuntimeException("Exception while parsing [" + jsonVerification + "] for Verification", e);
                }
                return verification;
            } else {
                logger.info("Validation failed:" + NEW_LINE + validationErrors + NEW_LINE + "-- Expectation:" + NEW_LINE + jsonVerification + NEW_LINE + "-- Schema:" + NEW_LINE + verificationValidator.getSchema());
                throw new IllegalArgumentException(validationErrors);
            }
        }
    }

    @Override
    public Class<Verification> supportsType() {
        return Verification.class;
    }

}
