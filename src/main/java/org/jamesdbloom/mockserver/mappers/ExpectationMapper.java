package org.jamesdbloom.mockserver.mappers;

import org.codehaus.jackson.map.ObjectMapper;
import org.jamesdbloom.mockserver.client.ExpectationDTO;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.mock.Expectation;
import org.jamesdbloom.mockserver.model.HttpRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class ExpectationMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpRequestMatcher transformsToMatcher(HttpRequest httpRequest) {
        return new HttpRequestMatcher()
                .withPath(httpRequest.getPath())
                .withBody(httpRequest.getBody())
                .withHeaders(httpRequest.getHeaders())
                .withCookies(httpRequest.getCookies())
                .withQueryParameters(httpRequest.getQueryParameters())
                .withBodyParameters(httpRequest.getBodyParameters());
    }

    public String serialize(ExpectationDTO expectationDTO) {
        try {
            return objectMapper.writeValueAsString(expectationDTO);
        } catch (IOException ioe) {
            throw new RuntimeException(String.format("Exception while serializing expectation to JSON with value %s", expectationDTO), ioe);
        }
    }

    public Expectation deserialize(HttpServletRequest httpServletRequest) {
        Expectation expectation;
        try {
            ExpectationDTO expectationDTO = objectMapper.readValue(httpServletRequest.getInputStream(), ExpectationDTO.class);
            expectation = new Expectation(expectationDTO.getHttpRequestMatcher(), expectationDTO.getTimes()).respond(expectationDTO.getHttpResponse());
        } catch (IOException ioe) {
            throw new RuntimeException("Exception while parsing response for http response expectation with value of", ioe);
        }
        return expectation;
    }
}
