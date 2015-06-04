package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.client.serialization.model.VerificationDTO;
import org.mockserver.verify.Verification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class VerificationSerializer {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

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
        Verification verification = null;
        if (jsonVerification != null && !jsonVerification.isEmpty()) {
            try {
                VerificationDTO verificationDTO = objectMapper.readValue(jsonVerification, VerificationDTO.class);
                if (verificationDTO != null) {
                    verification = verificationDTO.buildObject();
                }
            } catch (Exception e) {
                logger.info("Exception while parsing response [" + jsonVerification + "] for verification", e);
                throw new RuntimeException("Exception while parsing response [" + jsonVerification + "] for verification", e);
            }
        }
        return verification;
    }

}
