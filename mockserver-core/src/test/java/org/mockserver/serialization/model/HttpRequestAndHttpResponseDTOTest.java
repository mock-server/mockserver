package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpResponse;

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

        HttpRequestAndHttpResponse httpOverrideForwardedRequest = new HttpRequestAndHttpResponse()
            .withHttpRequest(httpRequest)
            .withHttpResponse(httpResponse);

        // when
        HttpRequestAndHttpResponseDTO httpOverrideForwardedRequestDTO = new HttpRequestAndHttpResponseDTO(httpOverrideForwardedRequest);

        // then
        assertThat(httpOverrideForwardedRequestDTO.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(httpOverrideForwardedRequestDTO.getHttpResponse(), is(new HttpResponseDTO(httpResponse)));
    }

    @Test
    public void shouldBuildObject() {
        // given
        HttpRequest httpRequest = request("some_request");
        HttpResponse httpResponse = response("some_response");

        HttpRequestAndHttpResponse httpOverrideForwardedRequest = new HttpRequestAndHttpResponse()
            .withHttpRequest(httpRequest)
            .withHttpResponse(httpResponse);

        // when
        HttpRequestAndHttpResponse builtHttpRequestAndHttpResponse = new HttpRequestAndHttpResponseDTO(httpOverrideForwardedRequest).buildObject();

        // then
        assertThat(builtHttpRequestAndHttpResponse.getHttpRequest(), is(httpRequest));
        assertThat(builtHttpRequestAndHttpResponse.getHttpResponse(), is(httpResponse));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        HttpRequestDTO httpRequest = new HttpRequestDTO(request("some_request"));
        HttpResponseDTO httpResponse = new HttpResponseDTO(response("some_response"));

        HttpRequestAndHttpResponse httpOverrideForwardedRequest = new HttpRequestAndHttpResponse();

        // when
        HttpRequestAndHttpResponseDTO httpOverrideForwardedRequestDTO = new HttpRequestAndHttpResponseDTO(httpOverrideForwardedRequest);
        httpOverrideForwardedRequestDTO.setHttpRequest(httpRequest);
        httpOverrideForwardedRequestDTO.setHttpResponse(httpResponse);

        // then
        assertThat(httpOverrideForwardedRequestDTO.getHttpRequest(), is(httpRequest));
        assertThat(httpOverrideForwardedRequestDTO.getHttpResponse(), is(httpResponse));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpRequestAndHttpResponseDTO httpObjectCallbackDTO = new HttpRequestAndHttpResponseDTO(null);

        // then
        assertThat(httpObjectCallbackDTO.getHttpRequest(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getHttpResponse(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpRequestAndHttpResponseDTO httpObjectCallbackDTO = new HttpRequestAndHttpResponseDTO(new HttpRequestAndHttpResponse());

        // then
        assertThat(httpObjectCallbackDTO.getHttpRequest(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getHttpResponse(), is(nullValue()));
    }
}
