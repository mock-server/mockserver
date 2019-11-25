package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.HttpOverrideForwardedRequest;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequestDTOTest {


    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        HttpRequest httpRequest = request("some_request");
        HttpResponse httpResponse = response("some_response");

        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest()
            .withHttpRequest(httpRequest)
            .withHttpResponse(httpResponse);

        // when
        HttpOverrideForwardedRequestDTO httpOverrideForwardedRequestDTO = new HttpOverrideForwardedRequestDTO(httpOverrideForwardedRequest);

        // then
        assertThat(httpOverrideForwardedRequestDTO.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(httpOverrideForwardedRequestDTO.getHttpResponse(), is(new HttpResponseDTO(httpResponse)));
    }

    @Test
    public void shouldBuildObject() {
        // given
        HttpRequest httpRequest = request("some_request");
        HttpResponse httpResponse = response("some_response");

        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest()
            .withHttpRequest(httpRequest)
            .withHttpResponse(httpResponse);

        // when
        HttpOverrideForwardedRequest builtHttpOverrideForwardedRequest = new HttpOverrideForwardedRequestDTO(httpOverrideForwardedRequest).buildObject();

        // then
        assertThat(builtHttpOverrideForwardedRequest.getHttpRequest(), is(httpRequest));
        assertThat(builtHttpOverrideForwardedRequest.getHttpResponse(), is(httpResponse));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        HttpRequestDTO httpRequest = new HttpRequestDTO(request("some_request"));
        HttpResponseDTO httpResponse = new HttpResponseDTO(response("some_response"));

        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest();

        // when
        HttpOverrideForwardedRequestDTO httpOverrideForwardedRequestDTO = new HttpOverrideForwardedRequestDTO(httpOverrideForwardedRequest);
        httpOverrideForwardedRequestDTO.setHttpRequest(httpRequest);
        httpOverrideForwardedRequestDTO.setHttpResponse(httpResponse);

        // then
        assertThat(httpOverrideForwardedRequestDTO.getHttpRequest(), is(httpRequest));
        assertThat(httpOverrideForwardedRequestDTO.getHttpResponse(), is(httpResponse));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpOverrideForwardedRequestDTO httpObjectCallbackDTO = new HttpOverrideForwardedRequestDTO(null);

        // then
        assertThat(httpObjectCallbackDTO.getHttpRequest(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getHttpResponse(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpOverrideForwardedRequestDTO httpObjectCallbackDTO = new HttpOverrideForwardedRequestDTO(new HttpOverrideForwardedRequest());

        // then
        assertThat(httpObjectCallbackDTO.getHttpRequest(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getHttpResponse(), is(nullValue()));
    }
}
