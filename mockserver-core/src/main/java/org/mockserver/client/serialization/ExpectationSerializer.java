package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.fge.jackson.JacksonUtils;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.mock.Expectation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializer implements Serializer<Expectation> {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public String serialize(Expectation expectation) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new ExpectationDTO(expectation));
        } catch (Exception e) {
            logger.error(String.format("Exception while serializing expectation to JSON with value %s", expectation), e);
            throw new RuntimeException(String.format("Exception while serializing expectation to JSON with value %s", expectation), e);
        }
    }

    public String serialize(Expectation... expectation) {
        try {
            if (expectation != null && expectation.length > 0) {
                ExpectationDTO[] expectationDTOs = new ExpectationDTO[expectation.length];
                for (int i = 0; i < expectation.length; i++) {
                    expectationDTOs[i] = new ExpectationDTO(expectation[i]);
                }
                return objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(expectationDTOs);
            }
            return "";
        } catch (Exception e) {
            logger.error("Exception while serializing expectation to JSON with value " + Arrays.asList(expectation), e);
            throw new RuntimeException("Exception while serializing expectation to JSON with value " + Arrays.asList(expectation), e);
        }
    }

    public Expectation deserialize(String jsonExpectation) {
        if (jsonExpectation == null || jsonExpectation.isEmpty()) {
            throw new IllegalArgumentException("Expected an JSON Expectation object but http body is empty");
        }
        Expectation expectation = null;
        try {
            ExpectationDTO expectationDTO = objectMapper.readValue(jsonExpectation, ExpectationDTO.class);
            if (expectationDTO != null) {
                expectation = expectationDTO.buildObject();
            }
        } catch (Exception e) {
            logger.error("Exception while parsing response [" + jsonExpectation + "] for Expectation", e);
            throw new RuntimeException("Exception while parsing response [" + jsonExpectation + "] for Expectation", e);
        }
        return expectation;
    }

    @Override
    public Class<Expectation> supportsType() {
        return Expectation.class;
    }

    public Expectation[] deserializeArray(String jsonExpectations) {
        Expectation[] expectations = new Expectation[]{};
        if (jsonExpectations != null && !jsonExpectations.isEmpty()) {
            try {
                ExpectationDTO[] expectationDTOs = objectMapper.readValue(jsonExpectations, ExpectationDTO[].class);
                if (expectationDTOs != null && expectationDTOs.length > 0) {
                    expectations = new Expectation[expectationDTOs.length];
                    for (int i = 0; i < expectationDTOs.length; i++) {
                        expectations[i] = expectationDTOs[i].buildObject();
                    }
                }
            } catch (Exception e) {
                logger.error("Exception while parsing response [" + jsonExpectations + "] for Expectation[]", e);
                throw new RuntimeException("Exception while parsing response [" + jsonExpectations + "] for Expectation[]", e);
            }
        }
        return expectations;
    }

    public List<String> returnJSONObjects(String jsonArray) {
        List<String> arrayItems = new ArrayList<String>();
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonArray);
            if (jsonNode instanceof ArrayNode) {
                for (JsonNode arrayElement : jsonNode) {
                    arrayItems.add(JacksonUtils.prettyPrint(arrayElement));
                }
            } else {
                arrayItems.add(JacksonUtils.prettyPrint(jsonNode));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return arrayItems;
    }
}
