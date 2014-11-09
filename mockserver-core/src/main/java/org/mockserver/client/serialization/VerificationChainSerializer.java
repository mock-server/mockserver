package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.client.serialization.model.VerificationChainDTO;
import org.mockserver.verify.VerificationChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class VerificationChainSerializer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public String serialize(VerificationChain verificationChain) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new VerificationChainDTO(verificationChain));
        } catch (IOException ioe) {
            logger.error(String.format("Exception while serializing verificationChain to JSON with value %s", verificationChain), ioe);
            throw new RuntimeException(String.format("Exception while serializing verificationChain to JSON with value %s", verificationChain), ioe);
        }
    }

    public VerificationChain deserialize(String jsonVerification) {
        VerificationChain verificationChain = null;
        if (jsonVerification != null && !jsonVerification.isEmpty()) {
            try {
                VerificationChainDTO verificationDTO = objectMapper.readValue(jsonVerification, VerificationChainDTO.class);
                if (verificationDTO != null) {
                    verificationChain = verificationDTO.buildObject();
                }
            } catch (IOException ioe) {
                logger.info("Exception while parsing response [" + jsonVerification + "] for verificationChain", ioe);
            }
        }
        return verificationChain;
    }

}
