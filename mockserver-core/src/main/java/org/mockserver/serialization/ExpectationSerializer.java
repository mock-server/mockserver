package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import org.mockserver.serialization.model.ExpectationDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.validator.jsonschema.JsonSchemaExpectationValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializer implements Serializer<Expectation> {
    private final MockServerLogger mockServerLogger;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonArraySerializer jsonArraySerializer = new JsonArraySerializer();
    private JsonSchemaExpectationValidator expectationValidator;

    public ExpectationSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        expectationValidator = new JsonSchemaExpectationValidator(mockServerLogger);
    }

    public String serialize(Expectation expectation) {
        try {
            return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(new ExpectationDTO(expectation));
        } catch (Exception e) {
            mockServerLogger.error(String.format("Exception while serializing expectation to JSON with value %s", expectation), e);
            throw new RuntimeException(String.format("Exception while serializing expectation to JSON with value %s", expectation), e);
        }
    }

    public String serialize(List<Expectation> expectations) {
        return serialize(expectations.toArray(new Expectation[expectations.size()]));
    }

    public String serialize(Expectation... expectations) {
        try {
            if (expectations != null && expectations.length > 0) {
                ExpectationDTO[] expectationDTOs = new ExpectationDTO[expectations.length];
                for (int i = 0; i < expectations.length; i++) {
                    expectationDTOs[i] = new ExpectationDTO(expectations[i]);
                }
                return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(expectationDTOs);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.error("Exception while serializing expectation to JSON with value " + Arrays.asList(expectations), e);
            throw new RuntimeException("Exception while serializing expectation to JSON with value " + Arrays.asList(expectations), e);
        }
    }

    public Expectation deserialize(String jsonExpectation) {
        if (Strings.isNullOrEmpty(jsonExpectation)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - an expectation is required but value was \"" + String.valueOf(jsonExpectation) + "\"");
        } else {
            String validationErrors = expectationValidator.isValid(jsonExpectation);
            if (validationErrors.isEmpty()) {
                Expectation expectation = null;
                try {
                    ExpectationDTO expectationDTO = objectMapper.readValue(jsonExpectation, ExpectationDTO.class);
                    if (expectationDTO != null) {
                        expectation = expectationDTO.buildObject();
                    }
                } catch (Exception e) {
                    mockServerLogger.error((HttpRequest) null, e, "exception while parsing {} for Expectation", jsonExpectation);
                    throw new RuntimeException("Exception while parsing [" + jsonExpectation + "] for Expectation", e);
                }
                return expectation;
            } else {
                mockServerLogger.error("validation failed:{}expectation:{}", validationErrors, jsonExpectation);
                throw new IllegalArgumentException(validationErrors);
            }
        }
    }

    @Override
    public Class<Expectation> supportsType() {
        return Expectation.class;
    }

    public Expectation[] deserializeArray(String jsonExpectations) {
        List<Expectation> expectations = new ArrayList<Expectation>();
        if (Strings.isNullOrEmpty(jsonExpectations)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - an expectation or expectation array is required but value was \"" + String.valueOf(jsonExpectations) + "\"");
        } else {
            List<String> jsonExpectationList = jsonArraySerializer.returnJSONObjects(jsonExpectations);
            if (jsonExpectationList.isEmpty()) {
                throw new IllegalArgumentException("1 error:" + NEW_LINE + " - an expectation or array of expectations is required");
            } else {
                List<String> validationErrorsList = new ArrayList<String>();
                for (String jsonExpecation : jsonExpectationList) {
                    try {
                        expectations.add(deserialize(jsonExpecation));
                    } catch (IllegalArgumentException iae) {
                        validationErrorsList.add(iae.getMessage());
                    }
                }
                if (!validationErrorsList.isEmpty()) {
                    if (validationErrorsList.size() > 1) {
                        throw new IllegalArgumentException(("[" + NEW_LINE + Joiner.on("," + NEW_LINE).join(validationErrorsList)).replaceAll(NEW_LINE, NEW_LINE + "  ") + NEW_LINE + "]");
                    } else {
                        throw new IllegalArgumentException(validationErrorsList.get(0));
                    }
                }
            }
        }
        return expectations.toArray(new Expectation[expectations.size()]);
    }

}
