package org.jamesdbloom.mockserver.client.serialization;

import org.codehaus.jackson.map.ObjectMapper;
import org.jamesdbloom.mockserver.client.serialization.model.ExpectationDTO;
import org.jamesdbloom.mockserver.mock.Expectation;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializer {

    private ObjectMapper objectMapper = new ObjectMapper();

    public String serialize(Expectation expectation) {
        try {
            return objectMapper.writeValueAsString(new ExpectationDTO(expectation));
        } catch (IOException ioe) {
            RuntimeException runtimeException = new RuntimeException(String.format("Exception while serializing expectation to JSON with value %s", expectation), ioe);
            runtimeException.printStackTrace();
            throw runtimeException;
        }
    }

    public Expectation deserialize(InputStream inputStream) {
        Expectation expectation = null;
        try {
            ExpectationDTO expectationDTO = objectMapper.readValue(inputStream, ExpectationDTO.class);
            if (expectationDTO != null) {
                expectation = expectationDTO.buildObject();
            }
        } catch (IOException ioe) {
            RuntimeException runtimeException = new RuntimeException("Exception while parsing response for http response expectation with value of", ioe);
            runtimeException.printStackTrace();
            throw runtimeException;
        }
        return expectation;
    }
}
