package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.model.VerificationDTO;
import org.mockserver.validator.jsonschema.JsonSchemaVerificationValidator;
import org.mockserver.verify.Verification;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.VERIFICATION_FAILED;

/**
 * @author jamesdbloom
 */
public class VerificationSerializer implements Serializer<Verification> {
    private final MockServerLogger mockServerLogger;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonSchemaVerificationValidator verificationValidator;

    public VerificationSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        verificationValidator = new JsonSchemaVerificationValidator(mockServerLogger);
    }

    public String serialize(Verification verification) {
        try {
            return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(new VerificationDTO(verification));
        } catch (Exception e) {
            mockServerLogger.error("Exception while serializing verification to JSON with value " + verification, e);
            throw new RuntimeException("Exception while serializing verification to JSON with value " + verification, e);
        }
    }

    public Verification deserialize(String jsonVerification) {
        if (isBlank(jsonVerification)) {
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
                    mockServerLogger.error((HttpRequest) null, e, "exception while parsing {}for Verification", jsonVerification);
                    throw new RuntimeException("Exception while parsing [" + jsonVerification + "] for Verification", e);
                }
                return verification;
            } else {
                mockServerLogger.info(VERIFICATION_FAILED, "validation failed:{}verification:{}", validationErrors, jsonVerification);
                throw new IllegalArgumentException(validationErrors);
            }
        }
    }

    @Override
    public Class<Verification> supportsType() {
        return Verification.class;
    }

}
