package org.jamesdbloom.mockserver.client.serialization;

import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.http.HttpStatus;
import org.jamesdbloom.mockserver.client.serialization.model.*;
import org.jamesdbloom.mockserver.mock.Expectation;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializer {

    private ObjectMapper objectMapper = new ObjectMapper();

    public String serialize(Expectation expectation) {
        try {
            return objectMapper.writeValueAsString(mapToDTO(expectation));
        } catch (IOException ioe) {
            RuntimeException runtimeException = new RuntimeException(String.format("Exception while serializing expectation to JSON with value %s", expectation), ioe);
            runtimeException.printStackTrace();
            throw runtimeException;
        }
    }

    public Expectation deserialize(InputStream inputStream) {
        Expectation expectation;
        try {
            expectation = mapFromDTO(objectMapper.readValue(inputStream, ExpectationDTO.class));
        } catch (IOException ioe) {
            RuntimeException runtimeException = new RuntimeException("Exception while parsing response for http response expectation with value of", ioe);
            runtimeException.printStackTrace();
            throw runtimeException;
        }
        return expectation;
    }

    public ExpectationDTO mapToDTO(Expectation expectation) {
        return new ExpectationDTO(expectation);
    }

    public Expectation mapFromDTO(ExpectationDTO expectation) {
        return null;
    }
}
