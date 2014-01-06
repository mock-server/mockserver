package org.mockserver.client.serialization;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = new ObjectMapper();

    public ExpectationSerializer() {
        // ignore failures
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
        // relax parsing
        objectMapper.configure(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.configure(DeserializationConfig.Feature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        // use arrays
        objectMapper.configure(DeserializationConfig.Feature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);
        // remove empty values from JSON
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT);
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY);
    }

    public String serialize(Expectation expectation) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new ExpectationDTO(expectation));
        } catch (IOException ioe) {
            logger.error(String.format("Exception while serializing expectation to JSON with value %s", expectation), ioe);
            throw new RuntimeException(String.format("Exception while serializing expectation to JSON with value %s", expectation), ioe);
        }
    }

    public String serializeAsJava(Expectation expectation) {
        StringBuffer output = new StringBuffer();
        if (expectation != null) {
            HttpRequest httpRequest = expectation.getHttpRequest();
            HttpResponse httpResponse = expectation.getHttpResponse();
            output.append("\n" +
                    "new MockServerClient()\n" +
                    "        .when(\n" +
                    "                request()");
            if (StringUtils.isNotEmpty(httpRequest.getMethod())) {
                output.append("\n                        .withMethod(\"" + httpRequest.getMethod() + "\")");
            }
            if (StringUtils.isNotEmpty(httpRequest.getURL())) {
                output.append("\n                        .withURL(\"" + httpRequest.getURL() + "\")");
            }
            if (StringUtils.isNotEmpty(httpRequest.getPath())) {
                output.append("\n                        .withPath(\"" + httpRequest.getPath() + "\")");
            }
            if (StringUtils.isNotEmpty(httpRequest.getQueryString())) {
                output.append("\n                        .withQueryString(\"" + httpRequest.getQueryString() + "\")");
            }
            if (httpRequest.getHeaders().size() > 0) {
                serializeAsJavaKeyToMultiValue(output, "Header", new ArrayList<KeyToMultiValue>(httpRequest.getHeaders()));
            }
            if (httpRequest.getCookies().size() > 0) {
                serializeAsJavaKeyToMultiValue(output, "Cookie", new ArrayList<KeyToMultiValue>(httpRequest.getCookies()));
            }
            if (StringUtils.isNotEmpty(httpRequest.getBody())) {
                output.append("\n                        .withBody(\"" + httpRequest.getBody() + "\")");
            }
            output.append(",\n" +
                    "                Times.once()\n" +
                    "        )\n" +
                    "        .thenRespond(\n" +
                    "                response()\n");
            if (httpResponse.getStatusCode() != null) {
                output.append("                        .withStatusCode(" + httpResponse.getStatusCode() + ")");
            }
            if (httpResponse.getHeaders().size() > 0) {
                serializeAsJavaKeyToMultiValue(output, "Header", new ArrayList<KeyToMultiValue>(httpResponse.getHeaders()));
            }
            if (httpResponse.getCookies().size() > 0) {
                serializeAsJavaKeyToMultiValue(output, "Cookie", new ArrayList<KeyToMultiValue>(httpResponse.getCookies()));
            }
            if (httpResponse.getBody() != null && httpResponse.getBody().length > 0) {
                output.append("\n                        .withBody(\"" + new String(httpResponse.getBody(), StandardCharsets.UTF_8) + "\")");
            }
            output.append("\n        );");
        }

        return output.toString();
    }

    private void serializeAsJavaKeyToMultiValue(StringBuffer output, String name, List<KeyToMultiValue> keyToMultiValues) {
        output.append("\n                        .with" + name + "s(\n");
        for (int i = 0; i < keyToMultiValues.size(); i++) {
            KeyToMultiValue keyToMultiValue = keyToMultiValues.get(i);
            output.append("                                new " + name + "(\"" + keyToMultiValue.getName() + "\"");
            for (String value : keyToMultiValue.getValues()) {
                output.append(", \"" + value + "\"");
            }
            output.append(")");
            if (i < (keyToMultiValues.size() - 1)) {
                output.append(",");
            }
            output.append("\n");
        }
        output.append("                        )");
    }

    public String serialize(Expectation[] expectation) {
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
        } catch (IOException ioe) {
            logger.error("Exception while serializing expectation to JSON with value " + Arrays.asList(expectation), ioe);
            throw new RuntimeException("Exception while serializing expectation to JSON with value " + Arrays.asList(expectation), ioe);
        }
    }

    public Expectation deserialize(byte[] jsonExpectation) {
        if (jsonExpectation.length == 0) throw new IllegalArgumentException("Expected an JSON expectation object but http body is empty");
        Expectation expectation = null;
        try {
            ExpectationDTO expectationDTO = objectMapper.readValue(jsonExpectation, ExpectationDTO.class);
            if (expectationDTO != null) {
                expectation = expectationDTO.buildObject();
            }
        } catch (IOException ioe) {
            logger.error("Exception while parsing response [" + new String(jsonExpectation) + "] for http response expectation", ioe);
            throw new RuntimeException("Exception while parsing response [" + new String(jsonExpectation) + "] for http response expectation", ioe);
        }
        return expectation;
    }

    public Expectation[] deserializeArray(byte[] jsonExpectation) {
        if (jsonExpectation.length == 0) throw new IllegalArgumentException("Expected an JSON expectation array object but http body is empty");
        Expectation[] expectations = null;
        try {
            ExpectationDTO[] expectationDTOs = objectMapper.readValue(jsonExpectation, ExpectationDTO[].class);
            if (expectationDTOs != null && expectationDTOs.length > 0) {
                expectations = new Expectation[expectationDTOs.length];
                for (int i = 0; i < expectationDTOs.length; i++) {
                    expectations[i] = expectationDTOs[i].buildObject();
                }
            }
        } catch (IOException ioe) {
            logger.error("Exception while parsing response [" + new String(jsonExpectation) + "] for http response expectation array", ioe);
            throw new RuntimeException("Exception while parsing response [" + new String(jsonExpectation) + "] for http response expectation array", ioe);
        }
        return expectations;
    }
}
