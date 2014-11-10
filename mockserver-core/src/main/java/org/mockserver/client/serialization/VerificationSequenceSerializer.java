package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.client.serialization.model.VerificationSequenceDTO;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceSerializer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public String serialize(VerificationSequence verificationSequence) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new VerificationSequenceDTO(verificationSequence));
        } catch (IOException ioe) {
            logger.error(String.format("Exception while serializing verificationSequence to JSON with value %s", verificationSequence), ioe);
            throw new RuntimeException(String.format("Exception while serializing verificationSequence to JSON with value %s", verificationSequence), ioe);
        }
    }

    public VerificationSequence deserialize(String jsonVerification) {
        VerificationSequence verificationSequence = null;
        if (jsonVerification != null && !jsonVerification.isEmpty()) {
            try {
                VerificationSequenceDTO verificationDTO = objectMapper.readValue(jsonVerification, VerificationSequenceDTO.class);
                if (verificationDTO != null) {
                    verificationSequence = verificationDTO.buildObject();
                }
            } catch (IOException ioe) {
                logger.info("Exception while parsing response [" + jsonVerification + "] for verificationSequence", ioe);
            }
        }
        return verificationSequence;
    }

}
