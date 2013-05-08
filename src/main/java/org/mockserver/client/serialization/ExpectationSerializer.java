package org.mockserver.client.serialization;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.CharSet;
import org.codehaus.jackson.map.ObjectMapper;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.mock.Expectation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializer {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private ObjectMapper objectMapper = new ObjectMapper();

    public String serialize(Expectation expectation) {
        try {
            return objectMapper.writeValueAsString(new ExpectationDTO(expectation));
        } catch (IOException ioe) {
            logger.error(String.format("Exception while serializing expectation to JSON with value %s", expectation), ioe);
            throw new RuntimeException(String.format("Exception while serializing expectation to JSON with value %s", expectation), ioe);
        }
    }

    public Expectation deserialize(InputStream inputStream) {
        Expectation expectation = null;
        try {
            byte[] jsonExpectation = IOUtils.toByteArray(new InputStreamReader(inputStream), Charset.forName(CharEncoding.UTF_8));
            logger.debug("Received JSON expectation:\n" + new String(jsonExpectation));
            ExpectationDTO expectationDTO = objectMapper.readValue(jsonExpectation, ExpectationDTO.class);
            if (expectationDTO != null) {
                expectation = expectationDTO.buildObject();
            }
        } catch (IOException ioe) {
            logger.error("Exception while parsing response for http response expectation", ioe);
            throw new RuntimeException("Exception while parsing response for http response expectation", ioe);
        }
        return expectation;
    }
}
