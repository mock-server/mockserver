package org.mockserver.client.serialization;

import com.google.common.base.Strings;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
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
            HttpResponse httpResponse = expectation.getHttpResponse(false);
            HttpForward httpForward = expectation.getHttpForward();
            output.append("\n" +
                    "new MockServerClient()\n" +
                    "        .when(\n" +
                    "                request()");
            if (StringUtils.isNotEmpty(httpRequest.getMethod())) {
                output.append("\n                        .withMethod(\"").append(httpRequest.getMethod()).append("\")");
            }
            if (StringUtils.isNotEmpty(httpRequest.getURL())) {
                output.append("\n                        .withURL(\"").append(httpRequest.getURL()).append("\")");
            }
            if (StringUtils.isNotEmpty(httpRequest.getPath())) {
                output.append("\n                        .withPath(\"").append(httpRequest.getPath()).append("\")");
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
                    output.append("\n                        .withBody(new StringBody(\"").append(StringEscapeUtils.escapeJava(((StringBody) httpRequest.getBody()).getValue())).append("\", Body.Type.").append(httpRequest.getBody().getType()).append("))");
                } else if (httpRequest.getBody() instanceof ParameterBody) {
                    output.append("\n                        .withBody(");
                    output.append("\n                                new ParameterBody(\n");
                    serializeAsJavaKeyToMultiValueList(output, "Parameter", new ArrayList<KeyToMultiValue>(((ParameterBody) httpRequest.getBody()).getParameters()), 40);
                    output.append("                                )");
                    output.append("\n                        )");
                } else if (httpRequest.getBody() instanceof BinaryBody) {
                    output.append("\n                        .withBody(new byte[0]) /* note: not possible to generate code for binary data */");
                }
            }
            output.append(",\n" +
                    "                Times.once()\n" +
                    "        )\n");
            if (httpResponse != null) {
                output.append("        .thenRespond(\n" +
                        "                response()\n");
                if (httpResponse.getStatusCode() != null) {
                    output.append("                        .withStatusCode(").append(httpResponse.getStatusCode()).append(")");
                }
                if (httpResponse.getHeaders().size() > 0) {
                    serializeAsJavaKeyToMultiValue(output, "Header", new ArrayList<KeyToMultiValue>(httpResponse.getHeaders()));
                }
                if (httpResponse.getCookies().size() > 0) {
                    serializeAsJavaKeyToMultiValue(output, "Cookie", new ArrayList<KeyToMultiValue>(httpResponse.getCookies()));
                }
                if (httpResponse.getBody() != null && httpResponse.getBody().length > 0) {
                    output.append("\n                        .withBody(\"").append(StringEscapeUtils.escapeJava(new String(httpResponse.getBody(), Charsets.UTF_8))).append("\")");
                }
                output.append("\n        );");
            }
            if (httpForward != null) {
                output.append("        .thenForward(\n" +
                        "                forward()\n");
                if (httpForward.getHost() != null) {
                    output.append("                        .withHost(\"").append(httpForward.getHost()).append("\")\n");
                }
                if (httpForward.getPort() != null) {
                    output.append("                        .withPort(").append(httpForward.getPort()).append(")\n");
                }
                if (httpForward.getScheme() != null) {
                    output.append("                        .withScheme(HttpForward.Scheme.").append(httpForward.getScheme()).append(")\n");
                }
                output.append("        );");
            }
        }

        return output.toString();
    }

    private void serializeAsJavaKeyToMultiValue(StringBuffer output, String name, List<KeyToMultiValue> keyToMultiValues) {
        output.append("\n                        .with").append(name).append("s(\n");
        serializeAsJavaKeyToMultiValueList(output, name, keyToMultiValues, 32);
        output.append("                        )");
    }

    private void serializeAsJavaKeyToMultiValueList(StringBuffer output, String name, List<KeyToMultiValue> keyToMultiValues, int indent) {
        for (int i = 0; i < keyToMultiValues.size(); i++) {
            KeyToMultiValue keyToMultiValue = keyToMultiValues.get(i);
            output.append(Strings.padStart("", indent, ' '));
            output.append("new ").append(name).append("(\"").append(keyToMultiValue.getName()).append("\"");
            for (String value : keyToMultiValue.getValues()) {
                output.append(", \"").append(value).append("\"");
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
