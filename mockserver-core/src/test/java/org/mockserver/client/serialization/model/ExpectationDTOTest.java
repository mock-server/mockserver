package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import static com.google.common.base.Charsets.UTF_8;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class ExpectationDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        HttpRequest httpRequest = new HttpRequest().withBody("some_body");
        HttpResponse httpResponse = new HttpResponse().withBody("some_response_body");
        HttpTemplate httpResponseTemplate = new HttpTemplate(HttpTemplate.TemplateType.JAVASCRIPT).withTemplate("some_repoonse_template");
        HttpClassCallback httpResponseClassCallback = new HttpClassCallback().withCallbackClass("some_response_class");
        HttpObjectCallback httpResponseObjectCallback = new HttpObjectCallback().withClientId("some_response_client_id");
        HttpForward httpForward = new HttpForward().withHost("some_host");
        HttpTemplate httpForwardTemplate = new HttpTemplate(HttpTemplate.TemplateType.VELOCITY).withTemplate("some_forward_template");
        HttpClassCallback httpForwardClassCallback = new HttpClassCallback().withCallbackClass("some_forward_class");
        HttpObjectCallback httpForwardObjectCallback = new HttpObjectCallback().withClientId("some_forward_client_id");
        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest().withHttpRequest(httpRequest);
        HttpError httpError = new HttpError().withResponseBytes("some_bytes".getBytes(UTF_8));

        // when
        ExpectationDTO expectationWithResponse = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponse));

        // then
        assertThat(expectationWithResponse.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithResponse.getTimes(), is(new org.mockserver.client.serialization.model.TimesDTO(Times.exactly(3))));
        assertThat(expectationWithResponse.getHttpResponse(), is(new HttpResponseDTO(httpResponse)));
        assertNull(expectationWithResponse.getHttpResponseTemplate());
        assertNull(expectationWithResponse.getHttpResponseClassCallback());
        assertNull(expectationWithResponse.getHttpResponseObjectCallback());
        assertNull(expectationWithResponse.getHttpForward());
        assertNull(expectationWithResponse.getHttpForwardTemplate());
        assertNull(expectationWithResponse.getHttpForwardClassCallback());
        assertNull(expectationWithResponse.getHttpForwardObjectCallback());
        assertNull(expectationWithResponse.getHttpOverrideForwardedRequest());
        assertNull(expectationWithResponse.getHttpError());

        // when
        Expectation expectationWithResponseTemplate = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponseTemplate)).buildObject();

        // then
        assertThat(expectationWithResponseTemplate.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithResponseTemplate.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithResponseTemplate.getHttpResponse());
        assertThat(expectationWithResponseTemplate.getHttpResponseTemplate(), is(httpResponseTemplate));
        assertNull(expectationWithResponseTemplate.getHttpResponseClassCallback());
        assertNull(expectationWithResponseTemplate.getHttpResponseObjectCallback());
        assertNull(expectationWithResponseTemplate.getHttpForward());
        assertNull(expectationWithResponseTemplate.getHttpForwardTemplate());
        assertNull(expectationWithResponseTemplate.getHttpForwardClassCallback());
        assertNull(expectationWithResponseTemplate.getHttpForwardObjectCallback());
        assertNull(expectationWithResponseTemplate.getHttpOverrideForwardedRequest());
        assertNull(expectationWithResponseTemplate.getHttpError());


        // when
        ExpectationDTO expectationWithResponseClassCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponseClassCallback));

        // then
        assertThat(expectationWithResponseClassCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithResponseClassCallback.getTimes(), is(new org.mockserver.client.serialization.model.TimesDTO(Times.exactly(3))));
        assertNull(expectationWithResponseClassCallback.getHttpResponse());
        assertNull(expectationWithResponseClassCallback.getHttpResponseTemplate());
        assertThat(expectationWithResponseClassCallback.getHttpResponseClassCallback(), is(new HttpClassCallbackDTO(httpResponseClassCallback)));
        assertNull(expectationWithResponseClassCallback.getHttpResponseObjectCallback());
        assertNull(expectationWithResponseClassCallback.getHttpForward());
        assertNull(expectationWithResponseClassCallback.getHttpForwardTemplate());
        assertNull(expectationWithResponseClassCallback.getHttpForwardClassCallback());
        assertNull(expectationWithResponseClassCallback.getHttpForwardObjectCallback());
        assertNull(expectationWithResponseClassCallback.getHttpOverrideForwardedRequest());
        assertNull(expectationWithResponseClassCallback.getHttpError());

        // when
        ExpectationDTO expectationWithResponseObjectCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponseObjectCallback));

        // then
        assertThat(expectationWithResponseObjectCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithResponseObjectCallback.getTimes(), is(new org.mockserver.client.serialization.model.TimesDTO(Times.exactly(3))));
        assertNull(expectationWithResponseObjectCallback.getHttpResponse());
        assertNull(expectationWithResponseObjectCallback.getHttpResponseTemplate());
        assertNull(expectationWithResponseObjectCallback.getHttpResponseClassCallback());
        assertThat(expectationWithResponseObjectCallback.getHttpResponseObjectCallback(), is(new HttpObjectCallbackDTO(httpResponseObjectCallback)));
        assertNull(expectationWithResponseObjectCallback.getHttpForward());
        assertNull(expectationWithResponseObjectCallback.getHttpForwardTemplate());
        assertNull(expectationWithResponseObjectCallback.getHttpForwardClassCallback());
        assertNull(expectationWithResponseObjectCallback.getHttpForwardObjectCallback());
        assertNull(expectationWithResponseObjectCallback.getHttpOverrideForwardedRequest());
        assertNull(expectationWithResponseObjectCallback.getHttpError());

        // when
        ExpectationDTO expectationWithForward = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForward));

        // then
        assertThat(expectationWithForward.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithForward.getTimes(), is(new org.mockserver.client.serialization.model.TimesDTO(Times.exactly(3))));
        assertNull(expectationWithForward.getHttpResponse());
        assertNull(expectationWithForward.getHttpResponseTemplate());
        assertNull(expectationWithForward.getHttpResponseClassCallback());
        assertNull(expectationWithForward.getHttpResponseObjectCallback());
        assertThat(expectationWithForward.getHttpForward(), is(new HttpForwardDTO(httpForward)));
        assertNull(expectationWithForward.getHttpForwardTemplate());
        assertNull(expectationWithForward.getHttpForwardClassCallback());
        assertNull(expectationWithForward.getHttpForwardObjectCallback());
        assertNull(expectationWithForward.getHttpOverrideForwardedRequest());
        assertNull(expectationWithForward.getHttpError());

        // when
        Expectation expectationWithForwardTemplate = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForwardTemplate)).buildObject();

        // then
        assertThat(expectationWithForwardTemplate.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithForwardTemplate.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithForwardTemplate.getHttpResponse());
        assertNull(expectationWithForwardTemplate.getHttpResponseTemplate());
        assertNull(expectationWithForwardTemplate.getHttpResponseClassCallback());
        assertNull(expectationWithForwardTemplate.getHttpResponseObjectCallback());
        assertNull(expectationWithForwardTemplate.getHttpForward());
        assertThat(expectationWithForwardTemplate.getHttpForwardTemplate(), is(httpForwardTemplate));
        assertNull(expectationWithForwardTemplate.getHttpForwardClassCallback());
        assertNull(expectationWithForwardTemplate.getHttpForwardObjectCallback());
        assertNull(expectationWithForwardTemplate.getHttpOverrideForwardedRequest());
        assertNull(expectationWithForwardTemplate.getHttpError());


        // when
        ExpectationDTO expectationWithForwardClassCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForwardClassCallback));

        // then
        assertThat(expectationWithForwardClassCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithForwardClassCallback.getTimes(), is(new org.mockserver.client.serialization.model.TimesDTO(Times.exactly(3))));
        assertNull(expectationWithForwardClassCallback.getHttpResponse());
        assertNull(expectationWithForwardClassCallback.getHttpResponseTemplate());
        assertNull(expectationWithForwardClassCallback.getHttpResponseClassCallback());
        assertNull(expectationWithForwardClassCallback.getHttpResponseObjectCallback());
        assertNull(expectationWithForwardClassCallback.getHttpForward());
        assertNull(expectationWithForwardClassCallback.getHttpForwardTemplate());
        assertThat(expectationWithForwardClassCallback.getHttpForwardClassCallback(), is(new HttpClassCallbackDTO(httpForwardClassCallback)));
        assertNull(expectationWithForwardClassCallback.getHttpForwardObjectCallback());
        assertNull(expectationWithForwardClassCallback.getHttpOverrideForwardedRequest());
        assertNull(expectationWithForwardClassCallback.getHttpError());

        // when
        ExpectationDTO expectationWithForwardObjectCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForwardObjectCallback));

        // then
        assertThat(expectationWithForwardObjectCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithForwardObjectCallback.getTimes(), is(new org.mockserver.client.serialization.model.TimesDTO(Times.exactly(3))));
        assertNull(expectationWithForwardObjectCallback.getHttpResponse());
        assertNull(expectationWithForwardObjectCallback.getHttpResponseTemplate());
        assertNull(expectationWithForwardObjectCallback.getHttpResponseClassCallback());
        assertNull(expectationWithForwardObjectCallback.getHttpResponseObjectCallback());
        assertNull(expectationWithForwardObjectCallback.getHttpForward());
        assertNull(expectationWithForwardObjectCallback.getHttpForwardTemplate());
        assertNull(expectationWithForwardObjectCallback.getHttpForwardClassCallback());
        assertThat(expectationWithForwardObjectCallback.getHttpForwardObjectCallback(), is(new HttpObjectCallbackDTO(httpForwardObjectCallback)));
        assertNull(expectationWithForwardObjectCallback.getHttpOverrideForwardedRequest());
        assertNull(expectationWithForwardObjectCallback.getHttpError());

        // when
        ExpectationDTO expectationWithOverrideForwardedRequest = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpOverrideForwardedRequest));

        // then
        assertThat(expectationWithOverrideForwardedRequest.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithOverrideForwardedRequest.getTimes(), is(new org.mockserver.client.serialization.model.TimesDTO(Times.exactly(3))));
        assertNull(expectationWithOverrideForwardedRequest.getHttpResponse());
        assertNull(expectationWithOverrideForwardedRequest.getHttpResponseTemplate());
        assertNull(expectationWithOverrideForwardedRequest.getHttpResponseClassCallback());
        assertNull(expectationWithOverrideForwardedRequest.getHttpResponseObjectCallback());
        assertNull(expectationWithOverrideForwardedRequest.getHttpForward());
        assertNull(expectationWithOverrideForwardedRequest.getHttpForwardTemplate());
        assertNull(expectationWithOverrideForwardedRequest.getHttpForwardClassCallback());
        assertNull(expectationWithOverrideForwardedRequest.getHttpForwardObjectCallback());
        assertThat(expectationWithOverrideForwardedRequest.getHttpOverrideForwardedRequest(), is(new HttpOverrideForwardedRequestDTO(httpOverrideForwardedRequest)));
        assertNull(expectationWithOverrideForwardedRequest.getHttpError());

        // when
        ExpectationDTO expectationWithError = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenError(httpError));

        // then
        assertThat(expectationWithError.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithError.getTimes(), is(new org.mockserver.client.serialization.model.TimesDTO(Times.exactly(3))));
        assertNull(expectationWithError.getHttpResponse());
        assertNull(expectationWithError.getHttpResponseTemplate());
        assertNull(expectationWithError.getHttpResponseClassCallback());
        assertNull(expectationWithError.getHttpResponseObjectCallback());
        assertNull(expectationWithError.getHttpForward());
        assertNull(expectationWithError.getHttpForwardTemplate());
        assertNull(expectationWithError.getHttpForwardClassCallback());
        assertNull(expectationWithError.getHttpForwardObjectCallback());
        assertNull(expectationWithError.getHttpOverrideForwardedRequest());
        assertThat(expectationWithError.getHttpError(), is(new HttpErrorDTO(httpError)));
    }

    @Test
    public void shouldBuildObject() {
        // given
        HttpRequest httpRequest = new HttpRequest().withBody("some_body");
        HttpResponse httpResponse = new HttpResponse().withBody("some_response_body");
        HttpTemplate httpResponseTemplate = new HttpTemplate(HttpTemplate.TemplateType.JAVASCRIPT).withTemplate("some_repoonse_template");
        HttpForward httpForward = new HttpForward().withHost("some_host");
        HttpTemplate httpForwardTemplate = new HttpTemplate(HttpTemplate.TemplateType.VELOCITY).withTemplate("some_forward_template");
        HttpError httpError = new HttpError().withResponseBytes("some_bytes".getBytes(UTF_8));
        HttpClassCallback httpClassCallback = new HttpClassCallback().withCallbackClass("some_class");
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback().withClientId("some_client_id");
        HttpOverrideForwardedRequest httpOverrideForwardedRequest = new HttpOverrideForwardedRequest().withHttpRequest(httpRequest);

        // when
        Expectation expectationWithResponse = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponse)).buildObject();

        // then
        assertThat(expectationWithResponse.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithResponse.getTimes(), is(Times.exactly(3)));
        assertThat(expectationWithResponse.getHttpResponse(), is(httpResponse));
        assertNull(expectationWithResponse.getHttpResponseTemplate());
        assertNull(expectationWithResponse.getHttpResponseClassCallback());
        assertNull(expectationWithResponse.getHttpResponseObjectCallback());
        assertNull(expectationWithResponse.getHttpForward());
        assertNull(expectationWithResponse.getHttpForwardTemplate());
        assertNull(expectationWithResponse.getHttpForwardClassCallback());
        assertNull(expectationWithResponse.getHttpForwardObjectCallback());
        assertNull(expectationWithResponse.getHttpOverrideForwardedRequest());
        assertNull(expectationWithResponse.getHttpError());

        // when
        Expectation expectationWithResponseTemplate = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponseTemplate)).buildObject();

        // then
        assertThat(expectationWithResponseTemplate.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithResponseTemplate.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithResponseTemplate.getHttpResponse());
        assertThat(expectationWithResponseTemplate.getHttpResponseTemplate(), is(httpResponseTemplate));
        assertNull(expectationWithResponseTemplate.getHttpResponseClassCallback());
        assertNull(expectationWithResponseTemplate.getHttpResponseObjectCallback());
        assertNull(expectationWithResponseTemplate.getHttpForward());
        assertNull(expectationWithResponseTemplate.getHttpForwardTemplate());
        assertNull(expectationWithResponseTemplate.getHttpForwardClassCallback());
        assertNull(expectationWithResponseTemplate.getHttpForwardObjectCallback());
        assertNull(expectationWithResponseTemplate.getHttpOverrideForwardedRequest());
        assertNull(expectationWithResponseTemplate.getHttpError());

        // when
        Expectation expectationWithResponseClassCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpClassCallback)).buildObject();

        // then
        assertThat(expectationWithResponseClassCallback.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithResponseClassCallback.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithResponseClassCallback.getHttpResponse());
        assertNull(expectationWithResponseClassCallback.getHttpResponseTemplate());
        assertThat(expectationWithResponseClassCallback.getHttpResponseClassCallback(), is(httpClassCallback));
        assertNull(expectationWithResponseClassCallback.getHttpResponseObjectCallback());
        assertNull(expectationWithResponseClassCallback.getHttpForward());
        assertNull(expectationWithResponseClassCallback.getHttpForwardTemplate());
        assertNull(expectationWithResponseClassCallback.getHttpForwardClassCallback());
        assertNull(expectationWithResponseClassCallback.getHttpForwardObjectCallback());
        assertNull(expectationWithResponseClassCallback.getHttpOverrideForwardedRequest());
        assertNull(expectationWithResponseClassCallback.getHttpError());

        // when
        Expectation expectationWithResponseObjectCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpObjectCallback)).buildObject();

        // then
        assertThat(expectationWithResponseObjectCallback.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithResponseObjectCallback.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithResponseObjectCallback.getHttpResponse());
        assertNull(expectationWithResponseObjectCallback.getHttpResponseTemplate());
        assertNull(expectationWithResponseObjectCallback.getHttpResponseClassCallback());
        assertThat(expectationWithResponseObjectCallback.getHttpResponseObjectCallback(), is(httpObjectCallback));
        assertNull(expectationWithResponseObjectCallback.getHttpForward());
        assertNull(expectationWithResponseObjectCallback.getHttpForwardTemplate());
        assertNull(expectationWithResponseObjectCallback.getHttpForwardClassCallback());
        assertNull(expectationWithResponseObjectCallback.getHttpForwardObjectCallback());
        assertNull(expectationWithResponseObjectCallback.getHttpOverrideForwardedRequest());
        assertNull(expectationWithResponseObjectCallback.getHttpError());

        // when
        Expectation expectationWithForward = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForward)).buildObject();

        // then
        assertThat(expectationWithForward.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithForward.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithForward.getHttpResponse());
        assertNull(expectationWithForward.getHttpResponseTemplate());
        assertNull(expectationWithForward.getHttpResponseClassCallback());
        assertNull(expectationWithForward.getHttpResponseObjectCallback());
        assertThat(expectationWithForward.getHttpForward(), is(httpForward));
        assertNull(expectationWithForward.getHttpForwardTemplate());
        assertNull(expectationWithForward.getHttpForwardClassCallback());
        assertNull(expectationWithForward.getHttpForwardObjectCallback());
        assertNull(expectationWithForward.getHttpOverrideForwardedRequest());
        assertNull(expectationWithForward.getHttpError());

        // when
        Expectation expectationWithForwardTemplate = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForwardTemplate)).buildObject();

        // then
        assertThat(expectationWithForwardTemplate.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithForwardTemplate.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithForwardTemplate.getHttpResponse());
        assertNull(expectationWithForwardTemplate.getHttpResponseTemplate());
        assertNull(expectationWithForwardTemplate.getHttpResponseClassCallback());
        assertNull(expectationWithForwardTemplate.getHttpResponseObjectCallback());
        assertNull(expectationWithForwardTemplate.getHttpForward());
        assertThat(expectationWithForwardTemplate.getHttpForwardTemplate(), is(httpForwardTemplate));
        assertNull(expectationWithForwardTemplate.getHttpForwardClassCallback());
        assertNull(expectationWithForwardTemplate.getHttpForwardObjectCallback());
        assertNull(expectationWithForwardTemplate.getHttpOverrideForwardedRequest());
        assertNull(expectationWithForwardTemplate.getHttpError());

        // when
        Expectation expectationWithForwardClassCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpClassCallback)).buildObject();

        // then
        assertThat(expectationWithForwardClassCallback.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithForwardClassCallback.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithForwardClassCallback.getHttpResponse());
        assertNull(expectationWithForwardClassCallback.getHttpResponseTemplate());
        assertNull(expectationWithForwardClassCallback.getHttpResponseClassCallback());
        assertNull(expectationWithForwardClassCallback.getHttpResponseObjectCallback());
        assertNull(expectationWithForwardClassCallback.getHttpForward());
        assertNull(expectationWithForwardClassCallback.getHttpForwardTemplate());
        assertThat(expectationWithForwardClassCallback.getHttpForwardClassCallback(), is(httpClassCallback));
        assertNull(expectationWithForwardClassCallback.getHttpForwardObjectCallback());
        assertNull(expectationWithForwardClassCallback.getHttpOverrideForwardedRequest());
        assertNull(expectationWithForwardClassCallback.getHttpError());

        // when
        Expectation expectationWithForwardObjectCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpObjectCallback)).buildObject();

        // then
        assertThat(expectationWithForwardObjectCallback.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithForwardObjectCallback.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithForwardObjectCallback.getHttpResponse());
        assertNull(expectationWithForwardObjectCallback.getHttpResponseTemplate());
        assertNull(expectationWithForwardObjectCallback.getHttpResponseClassCallback());
        assertNull(expectationWithForwardObjectCallback.getHttpResponseObjectCallback());
        assertNull(expectationWithForwardObjectCallback.getHttpForward());
        assertNull(expectationWithForwardObjectCallback.getHttpForwardTemplate());
        assertNull(expectationWithForwardObjectCallback.getHttpForwardClassCallback());
        assertThat(expectationWithForwardObjectCallback.getHttpForwardObjectCallback(), is(httpObjectCallback));
        assertNull(expectationWithForwardObjectCallback.getHttpOverrideForwardedRequest());
        assertNull(expectationWithForwardObjectCallback.getHttpError());

        // when
        Expectation expectationWithOverrideForwardedRequest = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpOverrideForwardedRequest)).buildObject();

        // then
        assertThat(expectationWithOverrideForwardedRequest.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithOverrideForwardedRequest.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithOverrideForwardedRequest.getHttpResponse());
        assertNull(expectationWithOverrideForwardedRequest.getHttpResponseTemplate());
        assertNull(expectationWithOverrideForwardedRequest.getHttpResponseClassCallback());
        assertNull(expectationWithOverrideForwardedRequest.getHttpResponseObjectCallback());
        assertNull(expectationWithOverrideForwardedRequest.getHttpForward());
        assertNull(expectationWithOverrideForwardedRequest.getHttpForwardTemplate());
        assertNull(expectationWithOverrideForwardedRequest.getHttpForwardClassCallback());
        assertNull(expectationWithOverrideForwardedRequest.getHttpForwardObjectCallback());
        assertThat(expectationWithOverrideForwardedRequest.getHttpOverrideForwardedRequest(), is(httpOverrideForwardedRequest));
        assertNull(expectationWithOverrideForwardedRequest.getHttpError());

        // when
        Expectation expectationWithError = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenError(httpError)).buildObject();

        // then
        assertThat(expectationWithError.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithError.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithError.getHttpResponse());
        assertNull(expectationWithError.getHttpResponseTemplate());
        assertNull(expectationWithError.getHttpResponseClassCallback());
        assertNull(expectationWithError.getHttpResponseObjectCallback());
        assertNull(expectationWithError.getHttpForward());
        assertNull(expectationWithError.getHttpForwardTemplate());
        assertNull(expectationWithError.getHttpForwardClassCallback());
        assertNull(expectationWithError.getHttpForwardObjectCallback());
        assertNull(expectationWithError.getHttpOverrideForwardedRequest());
        assertThat(expectationWithError.getHttpError(), is(httpError));
    }

    @Test
    public void shouldBuildObjectWithNulls() {
        // when
        Expectation expectation = new ExpectationDTO(new Expectation(null, null, TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).thenError(null).thenRespond((HttpClassCallback)null).thenRespond((HttpObjectCallback)null)).buildObject();

        // then
        assertThat(expectation.getHttpRequest(), is(nullValue()));
        assertThat(expectation.getTimes(), is(Times.unlimited()));
        assertThat(expectation.getHttpResponse(), is(nullValue()));
        assertThat(expectation.getHttpResponseTemplate(), is(nullValue()));
        assertThat(expectation.getHttpResponseClassCallback(), is(nullValue()));
        assertThat(expectation.getHttpResponseObjectCallback(), is(nullValue()));
        assertThat(expectation.getHttpForward(), is(nullValue()));
        assertThat(expectation.getHttpForwardTemplate(), is(nullValue()));
        assertThat(expectation.getHttpForwardClassCallback(), is(nullValue()));
        assertThat(expectation.getHttpForwardObjectCallback(), is(nullValue()));
        assertThat(expectation.getHttpOverrideForwardedRequest(), is(nullValue()));
        assertThat(expectation.getHttpError(), is(nullValue()));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        HttpRequestDTO httpRequest = new HttpRequestDTO(new HttpRequest().withBody("some_body"));
        org.mockserver.client.serialization.model.TimesDTO times = new org.mockserver.client.serialization.model.TimesDTO(Times.exactly(3));
        HttpResponseDTO httpResponse = new HttpResponseDTO(new HttpResponse().withBody("some_response_body"));
        HttpTemplateDTO httpResponseTemplate = new HttpTemplateDTO(new HttpTemplate(HttpTemplate.TemplateType.JAVASCRIPT).withTemplate("some_repoonse_template"));
        HttpClassCallbackDTO httpResponseClassCallback = new HttpClassCallbackDTO(new HttpClassCallback().withCallbackClass("some_response_class"));
        HttpObjectCallbackDTO httpResponseObjectCallback = new HttpObjectCallbackDTO(new HttpObjectCallback().withClientId("some_response_client_id"));
        HttpForwardDTO httpForward = new HttpForwardDTO(new HttpForward().withHost("some_host"));
        HttpTemplateDTO httpForwardTemplate = new HttpTemplateDTO(new HttpTemplate(HttpTemplate.TemplateType.VELOCITY).withTemplate("some_forward_template"));
        HttpClassCallbackDTO httpForwardClassCallback = new HttpClassCallbackDTO(new HttpClassCallback().withCallbackClass("some_forward_class"));
        HttpObjectCallbackDTO httpForwardObjectCallback = new HttpObjectCallbackDTO(new HttpObjectCallback().withClientId("some_forward_client_id"));
        HttpOverrideForwardedRequestDTO httpOverrideForwardedRequest = new HttpOverrideForwardedRequestDTO(new HttpOverrideForwardedRequest().withHttpRequest(request("some_path")));
        HttpErrorDTO httpError = new HttpErrorDTO(new HttpError().withResponseBytes("some_bytes".getBytes(UTF_8)));

        // when
        ExpectationDTO expectation = new ExpectationDTO();
        expectation.setHttpRequest(httpRequest);
        expectation.setTimes(times);
        expectation.setHttpResponse(httpResponse);
        expectation.setHttpResponseTemplate(httpResponseTemplate);
        expectation.setHttpResponseClassCallback(httpResponseClassCallback);
        expectation.setHttpResponseObjectCallback(httpResponseObjectCallback);
        expectation.setHttpForward(httpForward);
        expectation.setHttpForwardTemplate(httpForwardTemplate);
        expectation.setHttpForwardClassCallback(httpForwardClassCallback);
        expectation.setHttpForwardObjectCallback(httpForwardObjectCallback);
        expectation.setHttpOverrideForwardedRequest(httpOverrideForwardedRequest);
        expectation.setHttpError(httpError);

        // then
        assertThat(expectation.getHttpRequest(), is(httpRequest));
        assertThat(expectation.getTimes(), is(times));
        assertThat(expectation.getHttpResponse(), is(httpResponse));
        assertThat(expectation.getHttpResponseTemplate(), is(httpResponseTemplate));
        assertThat(expectation.getHttpResponseClassCallback(), is(httpResponseClassCallback));
        assertThat(expectation.getHttpResponseObjectCallback(), is(httpResponseObjectCallback));
        assertThat(expectation.getHttpForward(), is(httpForward));
        assertThat(expectation.getHttpForwardTemplate(), is(httpForwardTemplate));
        assertThat(expectation.getHttpForwardClassCallback(), is(httpForwardClassCallback));
        assertThat(expectation.getHttpForwardObjectCallback(), is(httpForwardObjectCallback));
        assertThat(expectation.getHttpOverrideForwardedRequest(), is(httpOverrideForwardedRequest));
        assertThat(expectation.getHttpError(), is(httpError));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        ExpectationDTO expectationDTO = new ExpectationDTO(null);

        // then
        assertThat(expectationDTO.getHttpRequest(), is(nullValue()));
        assertThat(expectationDTO.getTimes(), is(nullValue()));
        assertThat(expectationDTO.getHttpResponse(), is(nullValue()));
        assertThat(expectationDTO.getHttpResponseTemplate(), is(nullValue()));
        assertThat(expectationDTO.getHttpResponseClassCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpResponseObjectCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpForward(), is(nullValue()));
        assertThat(expectationDTO.getHttpForwardTemplate(), is(nullValue()));
        assertThat(expectationDTO.getHttpForwardClassCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpForwardObjectCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpOverrideForwardedRequest(), is(nullValue()));
        assertThat(expectationDTO.getHttpError(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        ExpectationDTO expectationDTO = new ExpectationDTO(new Expectation(null, null, TimeToLive.unlimited()));

        // then
        assertThat(expectationDTO.getHttpRequest(), is(nullValue()));
        assertThat(expectationDTO.getTimes(), is(nullValue()));
        assertThat(expectationDTO.getHttpResponse(), is(nullValue()));
        assertThat(expectationDTO.getHttpResponseTemplate(), is(nullValue()));
        assertThat(expectationDTO.getHttpResponseClassCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpResponseObjectCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpForward(), is(nullValue()));
        assertThat(expectationDTO.getHttpForwardTemplate(), is(nullValue()));
        assertThat(expectationDTO.getHttpForwardClassCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpForwardObjectCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpOverrideForwardedRequest(), is(nullValue()));
        assertThat(expectationDTO.getHttpError(), is(nullValue()));
    }
}
