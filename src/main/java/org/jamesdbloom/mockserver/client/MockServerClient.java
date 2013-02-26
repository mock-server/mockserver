package org.jamesdbloom.mockserver.client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class MockServerClient {

    private String mockServerURI = "http://localhost:8080/";

    private ExpectationMapper expectationMapper = new ExpectationMapper();

    public ExpectationDTO when(final HttpRequest httpRequest) {
        return when(httpRequest, Times.unlimited());
    }

    public ExpectationDTO when(HttpRequest httpRequest, Times times) {
        return new ExpectationDTO(this, expectationMapper.transformsToMatcher(httpRequest), times);
    }

    public void sendExpectation(ExpectationDTO expectationDTO) {
        HttpClient httpClient = new HttpClient();
        try {
            httpClient.start();
        } catch (Exception e) {
            throw new RuntimeException("Exception starting HttpClient", e);
        }
        httpClient.newRequest(mockServerURI).method(HttpMethod.POST).content(new StringContentProvider(expectationMapper.serialize(expectationDTO)));
    }

    public void setMockServerURI(String mockServerURI) {
        this.mockServerURI = mockServerURI;
    }
}
