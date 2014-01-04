package org.mockserver.client.serialization;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = new ObjectMapper();

    public String serialize(Expectation expectation) {
        try {
            return objectMapper
                    .setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT)
                    .setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL)
                    .setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY)
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
                    "                new HttpRequest()");
            if (StringUtils.isNotEmpty(httpRequest.getMethod())) {
                output.append("\n" +
                        "                        .withMethod(\"" + httpRequest.getMethod() + "\")");
            }
            if (StringUtils.isNotEmpty(httpRequest.getURL())) {
                output.append("\n" +
                        "                        .withURL(\"" + httpRequest.getURL() + "\")");
            }
            if (StringUtils.isNotEmpty(httpRequest.getPath())) {
                output.append("\n" +
                        "                        .withPath(\"" + httpRequest.getPath() + "\")");
            }
            if (StringUtils.isNotEmpty(httpRequest.getQueryString())) {
                output.append("\n" +
                        "                        .withQueryString(\"" + httpRequest.getQueryString() + "\")");
            }
            if (httpRequest.getHeaders().size() > 0) {
                output.append("\n" +
                        "                        .withHeaders(\n");
                for (Header header : httpRequest.getHeaders()) {
                    output.append("" +
                            "                                new Header(\"" + header.getName() + "\"");
                    for (String value : header.getValues()) {
                        output.append(", \"" + value + "\"");
                    }
                    output.append(")\n");
                }
                output.append("" +
                        "                        )");
            }
            if (httpRequest.getCookies().size() > 0) {
                output.append("\n" +
                        "                        .withCookies(\n");
                for (Cookie cookie : httpRequest.getCookies()) {
                    output.append("" +
                            "                                new Cookie(\"" + cookie.getName() + "\"");
                    for (String value : cookie.getValues()) {
                        output.append(", \"" + value + "\"");
                    }
                    output.append(")\n");
                }
                output.append("" +
                        "                        )");
            }
            if (StringUtils.isNotEmpty(httpRequest.getBody())) {
                output.append("\n" +
                        "                        .withBody(\"" + httpRequest.getBody() + "\")");
            }
            output.append(",\n" +
                    "                Times.once()\n" +
                    "        )" +
                    "        .thenRespond(\n" +
                    "                new HttpResponse()\n");
            if (httpResponse.getStatusCode() != null) {
                output.append("" +
                        "                .withStatusCode(" + httpResponse.getStatusCode() + ")");
            }
            if (httpResponse.getHeaders().size() > 0) {
                output.append("\n" +
                        "                        .withHeaders(\n");
                for (Header header : httpResponse.getHeaders()) {
                    output.append("" +
                            "                                new Header(\"" + header.getName() + "\"");
                    for (String value : header.getValues()) {
                        output.append(", \"" + value + "\"");
                    }
                    output.append(")\n");
                }
                output.append("" +
                        "                        )");
            }
            if (httpResponse.getCookies().size() > 0) {
                output.append("\n" +
                        "                        .withCookies(\n");
                for (Cookie cookie : httpResponse.getCookies()) {
                    output.append("" +
                            "                                new Cookie(\"" + cookie.getName() + "\"");
                    for (String value : cookie.getValues()) {
                        output.append(", \"" + value + "\"");
                    }
                    output.append(")\n");
                }
                output.append("" +
                        "                        )");
            }
            if (httpResponse.getBody() != null && httpResponse.getBody().length > 0) {
                output.append("\n" +
                        "                .withBody(\"" + new String(httpResponse.getBody(), StandardCharsets.UTF_8) + "\")");
            }
            output.append("\n" +
                    "        );");
        }

        return output.toString();
    }

    public String serialize(Expectation[] expectation) {
        try {
            if (expectation != null && expectation.length > 0) {
                ExpectationDTO[] expectationDTOs = new ExpectationDTO[expectation.length];
                for (int i = 0; i < expectation.length; i++) {
                    expectationDTOs[i] = new ExpectationDTO(expectation[i]);
                }
                return objectMapper
                        .setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT)
                        .setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL)
                        .setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY)
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
