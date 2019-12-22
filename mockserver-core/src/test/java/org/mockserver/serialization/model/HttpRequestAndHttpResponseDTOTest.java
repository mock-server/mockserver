package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.LogEventRequestAndResponse;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpRequestAndHttpResponseDTOTest {
    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        HttpRequest httpRequest = request("some_request");
        HttpResponse httpResponse = response("some_response");

        LogEventRequestAndResponse httpOverrideForwardedRequest = new LogEventRequestAndResponse()
            .withHttpRequest(httpRequest)
            .withHttpResponse(httpResponse);

        // when
        LogEventRequestAndResponseDTO httpOverrideForwardedRequestDTO = new LogEventRequestAndResponseDTO(httpOverrideForwardedRequest);

        // then
        assertThat(httpOverrideForwardedRequestDTO.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(httpOverrideForwardedRequestDTO.getHttpResponse(), is(new HttpResponseDTO(httpResponse)));
    }

    @Test
    public void shouldBuildObject() {
        // given
        HttpRequest httpRequest = request("some_request");
        HttpResponse httpResponse = response("some_response");

        LogEventRequestAndResponse httpOverrideForwardedRequest = new LogEventRequestAndResponse()
            .withHttpRequest(httpRequest)
            .withHttpResponse(httpResponse);

        // when
        LogEventRequestAndResponse builtHttpRequestAndHttpResponse = new LogEventRequestAndResponseDTO(httpOverrideForwardedRequest).buildObject();

        // then
        assertThat(builtHttpRequestAndHttpResponse.getHttpRequest(), is(httpRequest));
        assertThat(builtHttpRequestAndHttpResponse.getHttpResponse(), is(httpResponse));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        HttpRequestDTO httpRequest = new HttpRequestDTO(request("some_request"));
        HttpResponseDTO httpResponse = new HttpResponseDTO(response("some_response"));

        LogEventRequestAndResponse httpOverrideForwardedRequest = new LogEventRequestAndResponse();

        // when
        LogEventRequestAndResponseDTO httpOverrideForwardedRequestDTO = new LogEventRequestAndResponseDTO(httpOverrideForwardedRequest);
        httpOverrideForwardedRequestDTO.setHttpRequest(httpRequest);
        httpOverrideForwardedRequestDTO.setHttpResponse(httpResponse);

        // then
        assertThat(httpOverrideForwardedRequestDTO.getHttpRequest(), is(httpRequest));
        assertThat(httpOverrideForwardedRequestDTO.getHttpResponse(), is(httpResponse));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        LogEventRequestAndResponseDTO httpObjectCallbackDTO = new LogEventRequestAndResponseDTO(null);

        // then
        assertThat(httpObjectCallbackDTO.getHttpRequest(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getHttpResponse(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        LogEventRequestAndResponseDTO httpObjectCallbackDTO = new LogEventRequestAndResponseDTO(new LogEventRequestAndResponse());

        // then
        assertThat(httpObjectCallbackDTO.getHttpRequest(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getHttpResponse(), is(nullValue()));
    }
}
