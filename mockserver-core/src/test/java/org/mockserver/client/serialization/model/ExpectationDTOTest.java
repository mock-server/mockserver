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
        HttpForward httpForward = new HttpForward().withHost("some_host");
        HttpTemplate httpForwardTemplate = new HttpTemplate(HttpTemplate.TemplateType.VELOCITY).withTemplate("some_forward_template");
        HttpError httpError = new HttpError().withResponseBytes("some_bytes".getBytes(UTF_8));
        HttpClassCallback httpClassCallback = new HttpClassCallback().withCallbackClass("some_class");
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback().withClientId("some_client_id");

        // when
        ExpectationDTO expectationWithResponse = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponse));

        // then
        assertThat(expectationWithResponse.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithResponse.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertThat(expectationWithResponse.getHttpResponse(), is(new HttpResponseDTO(httpResponse)));
        assertNull(expectationWithResponse.getHttpForward());
        assertNull(expectationWithResponse.getHttpError());
        assertNull(expectationWithResponse.getHttpClassCallback());
        assertNull(expectationWithResponse.getHttpObjectCallback());

        // when
        Expectation expectationWithResponseTemplate = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponseTemplate)).buildObject();

        // then
        assertThat(expectationWithResponseTemplate.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithResponseTemplate.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithResponseTemplate.getHttpResponse());
        assertThat(expectationWithResponseTemplate.getHttpResponseTemplate(), is(httpResponseTemplate));
        assertNull(expectationWithResponseTemplate.getHttpForward());
        assertNull(expectationWithResponseTemplate.getHttpForwardTemplate());
        assertNull(expectationWithResponseTemplate.getHttpError());
        assertNull(expectationWithResponseTemplate.getHttpClassCallback());
        assertNull(expectationWithResponseTemplate.getHttpObjectCallback());

        // when
        ExpectationDTO expectationWithForward = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForward));

        // then
        assertThat(expectationWithForward.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithForward.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertNull(expectationWithForward.getHttpResponse());
        assertNull(expectationWithForward.getHttpResponseTemplate());
        assertThat(expectationWithForward.getHttpForward(), is(new HttpForwardDTO(httpForward)));
        assertNull(expectationWithForward.getHttpForwardTemplate());
        assertNull(expectationWithForward.getHttpError());
        assertNull(expectationWithForward.getHttpClassCallback());
        assertNull(expectationWithForward.getHttpObjectCallback());

        // when
        Expectation expectationWithForwardTemplate = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForwardTemplate)).buildObject();

        // then
        assertThat(expectationWithForwardTemplate.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithForwardTemplate.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithForwardTemplate.getHttpResponse());
        assertNull(expectationWithForwardTemplate.getHttpResponseTemplate());
        assertNull(expectationWithForwardTemplate.getHttpForward());
        assertThat(expectationWithForwardTemplate.getHttpForwardTemplate(), is(httpForwardTemplate));
        assertNull(expectationWithForwardTemplate.getHttpError());
        assertNull(expectationWithForwardTemplate.getHttpClassCallback());
        assertNull(expectationWithForwardTemplate.getHttpObjectCallback());


        // when
        ExpectationDTO expectationWithError = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenError(httpError));

        // then
        assertThat(expectationWithError.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithError.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertNull(expectationWithError.getHttpResponse());
        assertNull(expectationWithError.getHttpResponseTemplate());
        assertNull(expectationWithError.getHttpForward());
        assertNull(expectationWithError.getHttpForwardTemplate());
        assertThat(expectationWithError.getHttpError(), is(new HttpErrorDTO(httpError)));
        assertNull(expectationWithError.getHttpClassCallback());
        assertNull(expectationWithError.getHttpObjectCallback());

        // when
        ExpectationDTO expectationWithClassCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenCallback(httpClassCallback));

        // then
        assertThat(expectationWithClassCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithClassCallback.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertNull(expectationWithClassCallback.getHttpResponse());
        assertNull(expectationWithClassCallback.getHttpResponseTemplate());
        assertNull(expectationWithClassCallback.getHttpForward());
        assertNull(expectationWithClassCallback.getHttpForwardTemplate());
        assertNull(expectationWithClassCallback.getHttpError());
        assertThat(expectationWithClassCallback.getHttpClassCallback(), is(new HttpClassCallbackDTO(httpClassCallback)));
        assertNull(expectationWithClassCallback.getHttpObjectCallback());

        // when
        ExpectationDTO expectationWithObjectCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenCallback(httpObjectCallback));

        // then
        assertThat(expectationWithObjectCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithObjectCallback.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertNull(expectationWithObjectCallback.getHttpResponse());
        assertNull(expectationWithObjectCallback.getHttpResponseTemplate());
        assertNull(expectationWithObjectCallback.getHttpForwardTemplate());
        assertNull(expectationWithObjectCallback.getHttpError());
        assertNull(expectationWithObjectCallback.getHttpClassCallback());
        assertThat(expectationWithObjectCallback.getHttpObjectCallback(), is(new HttpObjectCallbackDTO(httpObjectCallback)));
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

        // when
        Expectation expectationWithResponse = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponse)).buildObject();

        // then
        assertThat(expectationWithResponse.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithResponse.getTimes(), is(Times.exactly(3)));
        assertThat(expectationWithResponse.getHttpResponse(), is(httpResponse));
        assertNull(expectationWithResponse.getHttpResponseTemplate());
        assertNull(expectationWithResponse.getHttpForward());
        assertNull(expectationWithResponse.getHttpForwardTemplate());
        assertNull(expectationWithResponse.getHttpError());
        assertNull(expectationWithResponse.getHttpClassCallback());
        assertNull(expectationWithResponse.getHttpObjectCallback());

        // when
        Expectation expectationWithResponseTemplate = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponseTemplate)).buildObject();

        // then
        assertThat(expectationWithResponseTemplate.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithResponseTemplate.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithResponseTemplate.getHttpResponse());
        assertThat(expectationWithResponseTemplate.getHttpResponseTemplate(), is(httpResponseTemplate));
        assertNull(expectationWithResponseTemplate.getHttpForward());
        assertNull(expectationWithResponseTemplate.getHttpForwardTemplate());
        assertNull(expectationWithResponseTemplate.getHttpError());
        assertNull(expectationWithResponseTemplate.getHttpClassCallback());
        assertNull(expectationWithResponseTemplate.getHttpObjectCallback());

        // when
        Expectation expectationWithForward = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForward)).buildObject();

        // then
        assertThat(expectationWithForward.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithForward.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithForward.getHttpResponse());
        assertNull(expectationWithForward.getHttpResponseTemplate());
        assertThat(expectationWithForward.getHttpForward(), is(httpForward));
        assertNull(expectationWithForward.getHttpForwardTemplate());
        assertNull(expectationWithForward.getHttpError());
        assertNull(expectationWithForward.getHttpClassCallback());
        assertNull(expectationWithForward.getHttpObjectCallback());

        // when
        Expectation expectationWithForwardTemplate = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForwardTemplate)).buildObject();

        // then
        assertThat(expectationWithForwardTemplate.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithForwardTemplate.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithForwardTemplate.getHttpResponse());
        assertNull(expectationWithForwardTemplate.getHttpResponseTemplate());
        assertNull(expectationWithForwardTemplate.getHttpForward());
        assertThat(expectationWithForwardTemplate.getHttpForwardTemplate(), is(httpForwardTemplate));
        assertNull(expectationWithForwardTemplate.getHttpError());
        assertNull(expectationWithForwardTemplate.getHttpClassCallback());
        assertNull(expectationWithForwardTemplate.getHttpObjectCallback());

        // when
        Expectation expectationWithError = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenError(httpError)).buildObject();

        // then
        assertThat(expectationWithError.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithError.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithError.getHttpResponse());
        assertNull(expectationWithError.getHttpResponseTemplate());
        assertNull(expectationWithError.getHttpForward());
        assertNull(expectationWithError.getHttpForwardTemplate());
        assertThat(expectationWithError.getHttpError(), is(httpError));
        assertNull(expectationWithError.getHttpClassCallback());
        assertNull(expectationWithError.getHttpObjectCallback());

        // when
        Expectation expectationWithClassCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenCallback(httpClassCallback)).buildObject();

        // then
        assertThat(expectationWithClassCallback.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithClassCallback.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithClassCallback.getHttpResponse());
        assertNull(expectationWithClassCallback.getHttpResponseTemplate());
        assertNull(expectationWithClassCallback.getHttpForward());
        assertNull(expectationWithClassCallback.getHttpForwardTemplate());
        assertNull(expectationWithClassCallback.getHttpError());
        assertThat(expectationWithClassCallback.getHttpClassCallback(), is(httpClassCallback));
        assertNull(expectationWithClassCallback.getHttpObjectCallback());

        // when
        Expectation expectationWithObjectCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenCallback(httpObjectCallback)).buildObject();

        // then
        assertThat(expectationWithObjectCallback.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithObjectCallback.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithObjectCallback.getHttpResponse());
        assertNull(expectationWithObjectCallback.getHttpResponseTemplate());
        assertNull(expectationWithObjectCallback.getHttpForward());
        assertNull(expectationWithObjectCallback.getHttpForwardTemplate());
        assertNull(expectationWithObjectCallback.getHttpError());
        assertNull(expectationWithObjectCallback.getHttpClassCallback());
        assertThat(expectationWithObjectCallback.getHttpObjectCallback(), is(httpObjectCallback));
    }

    @Test
    public void shouldBuildObjectWithNulls() {
        // when
        Expectation expectation = new ExpectationDTO(new Expectation(null, null, TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).thenError(null).thenCallback((HttpClassCallback)null).thenCallback((HttpObjectCallback)null)).buildObject();

        // then
        assertThat(expectation.getHttpRequest(), is(nullValue()));
        assertThat(expectation.getTimes(), is(Times.once()));
        assertThat(expectation.getHttpResponse(), is(nullValue()));
        assertThat(expectation.getHttpResponseTemplate(), is(nullValue()));
        assertThat(expectation.getHttpForward(), is(nullValue()));
        assertThat(expectation.getHttpForwardTemplate(), is(nullValue()));
        assertThat(expectation.getHttpError(), is(nullValue()));
        assertThat(expectation.getHttpClassCallback(), is(nullValue()));
        assertThat(expectation.getHttpObjectCallback(), is(nullValue()));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        HttpRequestDTO httpRequest = new HttpRequestDTO(new HttpRequest().withBody("some_body"));
        TimesDTO times = new TimesDTO(Times.exactly(3));
        HttpResponseDTO httpResponse = new HttpResponseDTO(new HttpResponse().withBody("some_response_body"));
        HttpTemplateDTO httpResponseTemplate = new HttpTemplateDTO(new HttpTemplate(HttpTemplate.TemplateType.JAVASCRIPT).withTemplate("some_repoonse_template"));
        HttpForwardDTO httpForward = new HttpForwardDTO(new HttpForward().withHost("some_host"));
        HttpTemplateDTO httpForwardTemplate = new HttpTemplateDTO(new HttpTemplate(HttpTemplate.TemplateType.VELOCITY).withTemplate("some_forward_template"));
        HttpErrorDTO httpError = new HttpErrorDTO(new HttpError().withResponseBytes("some_bytes".getBytes(UTF_8)));
        HttpClassCallbackDTO httpClassCallback = new HttpClassCallbackDTO(new HttpClassCallback().withCallbackClass("some_class"));
        HttpObjectCallbackDTO httpObjectCallback = new HttpObjectCallbackDTO(new HttpObjectCallback().withClientId("some_client_id"));

        // when
        ExpectationDTO expectation = new ExpectationDTO();
        expectation.setHttpRequest(httpRequest);
        expectation.setTimes(times);
        expectation.setHttpResponse(httpResponse);
        expectation.setHttpResponseTemplate(httpResponseTemplate);
        expectation.setHttpForward(httpForward);
        expectation.setHttpForwardTemplate(httpForwardTemplate);
        expectation.setHttpError(httpError);
        expectation.setHttpClassCallback(httpClassCallback);
        expectation.setHttpObjectCallback(httpObjectCallback);

        // then
        assertThat(expectation.getHttpRequest(), is(httpRequest));
        assertThat(expectation.getTimes(), is(times));
        assertThat(expectation.getHttpResponse(), is(httpResponse));
        assertThat(expectation.getHttpResponseTemplate(), is(httpResponseTemplate));
        assertThat(expectation.getHttpForward(), is(httpForward));
        assertThat(expectation.getHttpForwardTemplate(), is(httpForwardTemplate));
        assertThat(expectation.getHttpError(), is(httpError));
        assertThat(expectation.getHttpClassCallback(), is(httpClassCallback));
        assertThat(expectation.getHttpObjectCallback(), is(httpObjectCallback));
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
        assertThat(expectationDTO.getHttpForward(), is(nullValue()));
        assertThat(expectationDTO.getHttpForwardTemplate(), is(nullValue()));
        assertThat(expectationDTO.getHttpError(), is(nullValue()));
        assertThat(expectationDTO.getHttpClassCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpObjectCallback(), is(nullValue()));
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
        assertThat(expectationDTO.getHttpForward(), is(nullValue()));
        assertThat(expectationDTO.getHttpForwardTemplate(), is(nullValue()));
        assertThat(expectationDTO.getHttpError(), is(nullValue()));
        assertThat(expectationDTO.getHttpClassCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpObjectCallback(), is(nullValue()));
    }
}
