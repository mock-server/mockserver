package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.*;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Headers.headers;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpRequestModifier.requestModifier;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpResponseModifier.responseModifier;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequestDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        HttpRequest httpRequest = request("some_request");
        HttpRequestModifier httpRequestModifier = requestModifier().withPath("someRegex", "someSubstitution");
        HttpResponse httpResponse = response("some_response");
        HttpResponseModifier httpResponseModifier = responseModifier().withHeaders(headers(), headers(), null);

        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest()
            .withRequestOverride(httpRequest)
            .withRequestModifier(httpRequestModifier)
            .withResponseOverride(httpResponse)
            .withResponseModifier(httpResponseModifier);

        // when
        HttpOverrideForwardedRequestDTO httpOverrideForwardedRequestDTO = new HttpOverrideForwardedRequestDTO(httpOverrideForwardedRequest);

        // then
        assertThat(httpOverrideForwardedRequestDTO.getRequestOverride(), is(new HttpRequestDTO(httpRequest)));
        assertThat(httpOverrideForwardedRequestDTO.getRequestModifier(), is(new HttpRequestModifierDTO(httpRequestModifier)));
        assertThat(httpOverrideForwardedRequestDTO.getResponseOverride(), is(new HttpResponseDTO(httpResponse)));
        assertThat(httpOverrideForwardedRequestDTO.getResponseModifier(), is(new HttpResponseModifierDTO(httpResponseModifier)));
    }

    @Test
    public void shouldBuildObject() {
        // given
        HttpRequest httpRequest = request("some_request");
        HttpRequestModifier httpRequestModifier = requestModifier().withPath("someRegex", "someSubstitution");
        HttpResponse httpResponse = response("some_response");
        HttpResponseModifier httpResponseModifier = responseModifier().withHeaders(headers(), headers(), null);

        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest()
            .withRequestOverride(httpRequest)
            .withRequestModifier(httpRequestModifier)
            .withResponseOverride(httpResponse)
            .withResponseModifier(httpResponseModifier);

        // when
        HttpOverrideForwardedRequest builtHttpOverrideForwardedRequest = new HttpOverrideForwardedRequestDTO(httpOverrideForwardedRequest).buildObject();

        // then
        assertThat(builtHttpOverrideForwardedRequest.getRequestOverride(), is(httpRequest));
        assertThat(builtHttpOverrideForwardedRequest.getRequestModifier(), is(httpRequestModifier));
        assertThat(builtHttpOverrideForwardedRequest.getResponseOverride(), is(httpResponse));
        assertThat(builtHttpOverrideForwardedRequest.getResponseModifier(), is(httpResponseModifier));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        HttpRequestDTO httpRequest = new HttpRequestDTO(request("some_request"));
        HttpRequestModifierDTO httpRequestModifier = new HttpRequestModifierDTO(requestModifier().withPath("someRegex", "someSubstitution"));
        HttpResponseDTO httpResponse = new HttpResponseDTO(response("some_response"));
        HttpResponseModifierDTO httpResponseModifier = new HttpResponseModifierDTO(responseModifier().withHeaders(headers(), headers(), null));

        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest();

        // when
        HttpOverrideForwardedRequestDTO httpOverrideForwardedRequestDTO = new HttpOverrideForwardedRequestDTO(httpOverrideForwardedRequest);
        httpOverrideForwardedRequestDTO.setRequestOverride(httpRequest);
        httpOverrideForwardedRequestDTO.setRequestModifier(httpRequestModifier);
        httpOverrideForwardedRequestDTO.setResponseOverride(httpResponse);
        httpOverrideForwardedRequestDTO.setResponseModifier(httpResponseModifier);

        // then
        assertThat(httpOverrideForwardedRequestDTO.getRequestOverride(), is(httpRequest));
        assertThat(httpOverrideForwardedRequestDTO.getRequestModifier(), is(httpRequestModifier));
        assertThat(httpOverrideForwardedRequestDTO.getResponseOverride(), is(httpResponse));
        assertThat(httpOverrideForwardedRequestDTO.getResponseModifier(), is(httpResponseModifier));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpOverrideForwardedRequestDTO httpObjectCallbackDTO = new HttpOverrideForwardedRequestDTO(null);

        // then
        assertThat(httpObjectCallbackDTO.getRequestOverride(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getRequestModifier(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getResponseOverride(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getResponseModifier(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpOverrideForwardedRequestDTO httpObjectCallbackDTO = new HttpOverrideForwardedRequestDTO(new HttpOverrideForwardedRequest());

        // then
        assertThat(httpObjectCallbackDTO.getRequestOverride(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getRequestModifier(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getResponseOverride(), is(nullValue()));
        assertThat(httpObjectCallbackDTO.getResponseModifier(), is(nullValue()));
    }
}
