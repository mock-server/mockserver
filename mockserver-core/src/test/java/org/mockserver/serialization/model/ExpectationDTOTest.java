package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import static java.nio.charset.StandardCharsets.UTF_8;
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
        Times times = Times.exactly(3);
        TimeToLive timeToLive = TimeToLive.unlimited();
        int priority = 0;

        // when
        ExpectationDTO expectationWithResponse = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpResponse));

        // then
        assertThat(expectationWithResponse.getTimes(), is(new TimesDTO(times)));
        assertThat(expectationWithResponse.getTimeToLive(), is(new TimeToLiveDTO(timeToLive)));
        assertThat(expectationWithResponse.getPriority(), is(priority));
        assertThat(expectationWithResponse.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
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
        ExpectationDTO expectationWithResponseTemplate = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpResponseTemplate));

        // then
        assertThat(expectationWithResponseTemplate.getTimes(), is(new TimesDTO(times)));
        assertThat(expectationWithResponseTemplate.getTimeToLive(), is(new TimeToLiveDTO(timeToLive)));
        assertThat(expectationWithResponseTemplate.getPriority(), is(priority));
        assertThat(expectationWithResponseTemplate.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertNull(expectationWithResponseTemplate.getHttpResponse());
        assertThat(expectationWithResponseTemplate.getHttpResponseTemplate(), is(new HttpTemplateDTO(httpResponseTemplate)));
        assertNull(expectationWithResponseTemplate.getHttpResponseClassCallback());
        assertNull(expectationWithResponseTemplate.getHttpResponseObjectCallback());
        assertNull(expectationWithResponseTemplate.getHttpForward());
        assertNull(expectationWithResponseTemplate.getHttpForwardTemplate());
        assertNull(expectationWithResponseTemplate.getHttpForwardClassCallback());
        assertNull(expectationWithResponseTemplate.getHttpForwardObjectCallback());
        assertNull(expectationWithResponseTemplate.getHttpOverrideForwardedRequest());
        assertNull(expectationWithResponseTemplate.getHttpError());


        // when
        ExpectationDTO expectationWithResponseClassCallback = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpResponseClassCallback));

        // then
        assertThat(expectationWithResponseClassCallback.getTimes(), is(new TimesDTO(times)));
        assertThat(expectationWithResponseClassCallback.getTimeToLive(), is(new TimeToLiveDTO(timeToLive)));
        assertThat(expectationWithResponseClassCallback.getPriority(), is(priority));
        assertThat(expectationWithResponseClassCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
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
        ExpectationDTO expectationWithResponseObjectCallback = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpResponseObjectCallback));

        // then
        assertThat(expectationWithResponseObjectCallback.getTimes(), is(new TimesDTO(times)));
        assertThat(expectationWithResponseObjectCallback.getTimeToLive(), is(new TimeToLiveDTO(timeToLive)));
        assertThat(expectationWithResponseObjectCallback.getPriority(), is(priority));
        assertThat(expectationWithResponseObjectCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
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
        ExpectationDTO expectationWithForward = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpForward));

        // then
        assertThat(expectationWithForward.getTimes(), is(new TimesDTO(times)));
        assertThat(expectationWithForward.getTimeToLive(), is(new TimeToLiveDTO(timeToLive)));
        assertThat(expectationWithForward.getPriority(), is(priority));
        assertThat(expectationWithForward.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
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
        ExpectationDTO expectationWithForwardTemplate = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpForwardTemplate));

        // then
        assertThat(expectationWithForwardTemplate.getTimes(), is(new TimesDTO(times)));
        assertThat(expectationWithForwardTemplate.getTimeToLive(), is(new TimeToLiveDTO(timeToLive)));
        assertThat(expectationWithForwardTemplate.getPriority(), is(priority));
        assertThat(expectationWithForwardTemplate.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertNull(expectationWithForwardTemplate.getHttpResponse());
        assertNull(expectationWithForwardTemplate.getHttpResponseTemplate());
        assertNull(expectationWithForwardTemplate.getHttpResponseClassCallback());
        assertNull(expectationWithForwardTemplate.getHttpResponseObjectCallback());
        assertNull(expectationWithForwardTemplate.getHttpForward());
        assertThat(expectationWithForwardTemplate.getHttpForwardTemplate(), is(new HttpTemplateDTO(httpForwardTemplate)));
        assertNull(expectationWithForwardTemplate.getHttpForwardClassCallback());
        assertNull(expectationWithForwardTemplate.getHttpForwardObjectCallback());
        assertNull(expectationWithForwardTemplate.getHttpOverrideForwardedRequest());
        assertNull(expectationWithForwardTemplate.getHttpError());


        // when
        ExpectationDTO expectationWithForwardClassCallback = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpForwardClassCallback));

        // then
        assertThat(expectationWithForwardClassCallback.getTimes(), is(new TimesDTO(times)));
        assertThat(expectationWithForwardClassCallback.getTimeToLive(), is(new TimeToLiveDTO(timeToLive)));
        assertThat(expectationWithForwardClassCallback.getPriority(), is(priority));
        assertThat(expectationWithForwardClassCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
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
        ExpectationDTO expectationWithForwardObjectCallback = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpForwardObjectCallback));

        // then
        assertThat(expectationWithForwardObjectCallback.getTimes(), is(new TimesDTO(times)));
        assertThat(expectationWithForwardObjectCallback.getTimeToLive(), is(new TimeToLiveDTO(timeToLive)));
        assertThat(expectationWithForwardObjectCallback.getPriority(), is(priority));
        assertThat(expectationWithForwardObjectCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
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
        ExpectationDTO expectationWithOverrideForwardedRequest = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpOverrideForwardedRequest));

        // then
        assertThat(expectationWithOverrideForwardedRequest.getTimes(), is(new TimesDTO(times)));
        assertThat(expectationWithOverrideForwardedRequest.getTimeToLive(), is(new TimeToLiveDTO(timeToLive)));
        assertThat(expectationWithOverrideForwardedRequest.getPriority(), is(priority));
        assertThat(expectationWithOverrideForwardedRequest.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
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
        ExpectationDTO expectationWithError = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenError(httpError));

        // then
        assertThat(expectationWithError.getTimes(), is(new TimesDTO(times)));
        assertThat(expectationWithError.getTimeToLive(), is(new TimeToLiveDTO(timeToLive)));
        assertThat(expectationWithError.getPriority(), is(priority));
        assertThat(expectationWithError.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
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
        Times times = Times.exactly(3);
        TimeToLive timeToLive = TimeToLive.unlimited();
        int priority = 0;

        // when
        Expectation expectationWithResponse = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpResponse)).buildObject();

        // then
        assertThat(expectationWithResponse.getTimes(), is(times));
        assertThat(expectationWithResponse.getTimeToLive(), is(timeToLive));
        assertThat(expectationWithResponse.getPriority(), is(priority));
        assertThat(expectationWithResponse.getHttpRequest(), is(httpRequest));
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
        Expectation expectationWithResponseTemplate = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpResponseTemplate)).buildObject();

        // then
        assertThat(expectationWithResponseTemplate.getTimes(), is(times));
        assertThat(expectationWithResponseTemplate.getTimeToLive(), is(timeToLive));
        assertThat(expectationWithResponseTemplate.getPriority(), is(priority));
        assertThat(expectationWithResponseTemplate.getHttpRequest(), is(httpRequest));
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
        Expectation expectationWithResponseClassCallback = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpClassCallback)).buildObject();

        // then
        assertThat(expectationWithResponseClassCallback.getTimes(), is(times));
        assertThat(expectationWithResponseClassCallback.getTimeToLive(), is(timeToLive));
        assertThat(expectationWithResponseClassCallback.getPriority(), is(priority));
        assertThat(expectationWithResponseClassCallback.getHttpRequest(), is(httpRequest));
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
        Expectation expectationWithResponseObjectCallback = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpObjectCallback)).buildObject();

        // then
        assertThat(expectationWithResponseObjectCallback.getTimes(), is(times));
        assertThat(expectationWithResponseObjectCallback.getTimeToLive(), is(timeToLive));
        assertThat(expectationWithResponseObjectCallback.getPriority(), is(priority));
        assertThat(expectationWithResponseObjectCallback.getHttpRequest(), is(httpRequest));
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
        Expectation expectationWithForward = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpForward)).buildObject();

        // then
        assertThat(expectationWithForward.getTimes(), is(times));
        assertThat(expectationWithForward.getTimeToLive(), is(timeToLive));
        assertThat(expectationWithForward.getPriority(), is(priority));
        assertThat(expectationWithForward.getHttpRequest(), is(httpRequest));
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
        Expectation expectationWithForwardTemplate = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpForwardTemplate)).buildObject();

        // then
        assertThat(expectationWithForwardTemplate.getTimes(), is(times));
        assertThat(expectationWithForwardTemplate.getTimeToLive(), is(timeToLive));
        assertThat(expectationWithForwardTemplate.getPriority(), is(priority));
        assertThat(expectationWithForwardTemplate.getHttpRequest(), is(httpRequest));
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
        Expectation expectationWithForwardClassCallback = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpClassCallback)).buildObject();

        // then
        assertThat(expectationWithForwardClassCallback.getTimes(), is(times));
        assertThat(expectationWithForwardClassCallback.getTimeToLive(), is(timeToLive));
        assertThat(expectationWithForwardClassCallback.getPriority(), is(priority));
        assertThat(expectationWithForwardClassCallback.getHttpRequest(), is(httpRequest));
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
        Expectation expectationWithForwardObjectCallback = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpObjectCallback)).buildObject();

        // then
        assertThat(expectationWithForwardObjectCallback.getTimes(), is(times));
        assertThat(expectationWithForwardObjectCallback.getTimeToLive(), is(timeToLive));
        assertThat(expectationWithForwardObjectCallback.getPriority(), is(priority));
        assertThat(expectationWithForwardObjectCallback.getHttpRequest(), is(httpRequest));
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
        Expectation expectationWithOverrideForwardedRequest = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpOverrideForwardedRequest)).buildObject();

        // then
        assertThat(expectationWithOverrideForwardedRequest.getTimes(), is(times));
        assertThat(expectationWithOverrideForwardedRequest.getTimeToLive(), is(timeToLive));
        assertThat(expectationWithOverrideForwardedRequest.getPriority(), is(priority));
        assertThat(expectationWithOverrideForwardedRequest.getHttpRequest(), is(httpRequest));
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
        Expectation expectationWithError = new ExpectationDTO(new Expectation(httpRequest, times, timeToLive, priority).thenError(httpError)).buildObject();

        // then
        assertThat(expectationWithError.getTimes(), is(times));
        assertThat(expectationWithError.getTimeToLive(), is(timeToLive));
        assertThat(expectationWithError.getPriority(), is(priority));
        assertThat(expectationWithError.getHttpRequest(), is(httpRequest));
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
        Expectation expectation = new ExpectationDTO(new Expectation(null, null, null, null).thenRespond((HttpResponse) null).thenForward((HttpForward) null).thenError(null).thenRespond((HttpClassCallback) null).thenRespond((HttpObjectCallback) null)).buildObject();

        // then
        assertThat(expectation.getTimes(), is(Times.unlimited()));
        assertThat(expectation.getTimeToLive(), is(TimeToLive.unlimited()));
        assertThat(expectation.getPriority(), is(0));
        assertThat(expectation.getHttpRequest(), is(nullValue()));
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
        TimesDTO times = new TimesDTO(Times.exactly(3));
        TimeToLiveDTO timeToLive = new TimeToLiveDTO(TimeToLive.unlimited());
        int priority = 0;

        // when
        ExpectationDTO expectation = new ExpectationDTO();
        expectation.setTimes(times);
        expectation.setTimeToLive(timeToLive);
        expectation.setPriority(priority);
        expectation.setHttpRequest(httpRequest);
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
        assertThat(expectation.getTimes(), is(times));
        assertThat(expectation.getTimeToLive(), is(timeToLive));
        assertThat(expectation.getPriority(), is(priority));
        assertThat(expectation.getHttpRequest(), is(httpRequest));
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
        assertThat(expectationDTO.getTimes(), is(nullValue()));
        assertThat(expectationDTO.getTimeToLive(), is(nullValue()));
        assertThat(expectationDTO.getPriority(), is(nullValue()));
        assertThat(expectationDTO.getHttpRequest(), is(nullValue()));
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
        ExpectationDTO expectationDTO = new ExpectationDTO(new Expectation(null, null, null, null));

        // then
        assertThat(expectationDTO.getTimes(), is(nullValue()));
        assertThat(expectationDTO.getTimeToLive(), is(nullValue()));
        assertThat(expectationDTO.getPriority(), is(nullValue()));
        assertThat(expectationDTO.getHttpRequest(), is(nullValue()));
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
