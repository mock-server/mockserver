package org.mockserver.client.serialization;

import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

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
            if (httpRequest.getHeaders().size() > 0) {
                serializeAsJavaKeyToMultiValue(output, "Header", new ArrayList<KeyToMultiValue>(httpRequest.getHeaders()));
            }
            if (httpRequest.getCookies().size() > 0) {
                serializeAsJavaKeyToMultiValue(output, "Cookie", new ArrayList<KeyToMultiValue>(httpRequest.getCookies()));
            }
            if (httpRequest.getQueryStringParameters().size() > 0) {
                serializeAsJavaKeyToMultiValue(output, "QueryStringParameter", new ArrayList<KeyToMultiValue>(httpRequest.getQueryStringParameters()));
            }
            if (httpRequest.getBody() != null) {
                if (httpRequest.getBody() instanceof StringBody) {
                    output.append("\n                        .withBody(new StringBody(\"" + StringEscapeUtils.escapeJava(((StringBody) httpRequest.getBody()).getValue()) + "\", Body.Type." + httpRequest.getBody().getType() + "))");
                } else if (httpRequest.getBody() instanceof ParameterBody) {
                    output.append("\n                        .withBody(new ParameterBody(Arrays.asList(");
                    serializeAsJavaKeyToMultiValueList(output, "Parameter", new ArrayList<KeyToMultiValue>(((ParameterBody) httpRequest.getBody()).getParameters()));
                    output.append("), Body.Type." + httpRequest.getBody().getType() + "))");
                }
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
                output.append("\n                        .withBody(\"" + StringEscapeUtils.escapeJava(new String(httpResponse.getBody(), Charsets.UTF_8)) + "\")");
            }
            output.append("\n        );");
        }

        return output.toString();
    }

    private void serializeAsJavaKeyToMultiValue(StringBuffer output, String name, List<KeyToMultiValue> keyToMultiValues) {
        output.append("\n                        .with" + name + "s(\n");
        serializeAsJavaKeyToMultiValueList(output, name, keyToMultiValues);
        output.append("                        )");
    }

    private void serializeAsJavaKeyToMultiValueList(StringBuffer output, String name, List<KeyToMultiValue> keyToMultiValues) {
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

    public Expectation deserialize(String jsonExpectation) {
        if (jsonExpectation == null || jsonExpectation.isEmpty()) throw new IllegalArgumentException("Expected an JSON expectation object but http body is empty");
        Expectation expectation = null;
        try {
            ExpectationDTO expectationDTO = objectMapper.readValue(jsonExpectation, ExpectationDTO.class);
            if (expectationDTO != null) {
                expectation = expectationDTO.buildObject();
            }
        } catch (IOException ioe) {
            logger.error("Exception while parsing response [" + jsonExpectation + "] for http response expectation", ioe);
            throw new RuntimeException("Exception while parsing response [" + jsonExpectation + "] for http response expectation", ioe);
        }
        return expectation;
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
            } catch (IOException ioe) {
                logger.error("Exception while parsing response [" + jsonExpectations + "] for http response expectation array", ioe);
                throw new RuntimeException("Exception while parsing response [" + jsonExpectations + "] for http response expectation array", ioe);
            }
        }
        return expectations;
    }
}
