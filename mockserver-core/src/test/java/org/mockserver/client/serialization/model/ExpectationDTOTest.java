package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

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
        HttpForward httpForward = new HttpForward().withHost("some_host");
        HttpError httpError = new HttpError().withResponseBytes("some_bytes".getBytes());
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
        ExpectationDTO expectationWithForward = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForward));

        // then
        assertThat(expectationWithForward.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithForward.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertNull(expectationWithForward.getHttpResponse());
        assertThat(expectationWithForward.getHttpForward(), is(new HttpForwardDTO(httpForward)));
        assertNull(expectationWithForward.getHttpError());
        assertNull(expectationWithForward.getHttpClassCallback());
        assertNull(expectationWithForward.getHttpObjectCallback());

        // when
        ExpectationDTO expectationWithError = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenError(httpError));

        // then
        assertThat(expectationWithError.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithError.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertNull(expectationWithError.getHttpResponse());
        assertNull(expectationWithError.getHttpForward());
        assertThat(expectationWithError.getHttpError(), is(new HttpErrorDTO(httpError)));
        assertNull(expectationWithError.getHttpClassCallback());
        assertNull(expectationWithError.getHttpObjectCallback());

        // when
        ExpectationDTO expectationWithClassCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenCallback(httpClassCallback));

        // then
        assertThat(expectationWithClassCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithClassCallback.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertNull(expectationWithClassCallback.getHttpResponse());
        assertNull(expectationWithClassCallback.getHttpForward());
        assertNull(expectationWithClassCallback.getHttpError());
        assertThat(expectationWithClassCallback.getHttpClassCallback(), is(new HttpClassCallbackDTO(httpClassCallback)));
        assertNull(expectationWithClassCallback.getHttpObjectCallback());

        // when
        ExpectationDTO expectationWithObjectCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenCallback(httpObjectCallback));

        // then
        assertThat(expectationWithObjectCallback.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithObjectCallback.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertNull(expectationWithObjectCallback.getHttpResponse());
        assertNull(expectationWithObjectCallback.getHttpForward());
        assertNull(expectationWithObjectCallback.getHttpError());
        assertNull(expectationWithObjectCallback.getHttpClassCallback());
        assertThat(expectationWithObjectCallback.getHttpObjectCallback(), is(new HttpObjectCallbackDTO(httpObjectCallback)));
    }

    @Test
    public void shouldBuildObject() {
        // given
        HttpRequest httpRequest = new HttpRequest().withBody("some_body");
        HttpResponse httpResponse = new HttpResponse().withBody("some_response_body");
        HttpForward httpForward = new HttpForward().withHost("some_host");
        HttpError httpError = new HttpError().withResponseBytes("some_bytes".getBytes());
        HttpClassCallback httpClassCallback = new HttpClassCallback().withCallbackClass("some_class");
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback().withClientId("some_client_id");

        // when
        Expectation expectationWithResponse = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenRespond(httpResponse)).buildObject();

        // then
        assertThat(expectationWithResponse.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithResponse.getTimes(), is(Times.exactly(3)));
        assertThat(expectationWithResponse.getHttpResponse(), is(httpResponse));
        assertNull(expectationWithResponse.getHttpForward());
        assertNull(expectationWithResponse.getHttpError());
        assertNull(expectationWithResponse.getHttpClassCallback());
        assertNull(expectationWithResponse.getHttpObjectCallback());

        // when
        Expectation expectationWithForward = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenForward(httpForward)).buildObject();

        // then
        assertThat(expectationWithForward.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithForward.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithForward.getHttpResponse());
        assertThat(expectationWithForward.getHttpForward(), is(httpForward));
        assertNull(expectationWithForward.getHttpError());
        assertNull(expectationWithForward.getHttpClassCallback());
        assertNull(expectationWithForward.getHttpObjectCallback());

        // when
        Expectation expectationWithError = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenError(httpError)).buildObject();

        // then
        assertThat(expectationWithError.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithError.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithError.getHttpResponse());
        assertNull(expectationWithError.getHttpForward());
        assertThat(expectationWithError.getHttpError(), is(httpError));
        assertNull(expectationWithError.getHttpClassCallback());
        assertNull(expectationWithError.getHttpObjectCallback());

        // when
        Expectation expectationWithClassCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenCallback(httpClassCallback)).buildObject();

        // then
        assertThat(expectationWithClassCallback.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithClassCallback.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithClassCallback.getHttpResponse());
        assertNull(expectationWithClassCallback.getHttpForward());
        assertNull(expectationWithClassCallback.getHttpError());
        assertThat(expectationWithClassCallback.getHttpClassCallback(), is(httpClassCallback));
        assertNull(expectationWithClassCallback.getHttpObjectCallback());

        // when
        Expectation expectationWithObjectCallback = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3), TimeToLive.unlimited()).thenCallback(httpObjectCallback)).buildObject();

        // then
        assertThat(expectationWithObjectCallback.getHttpRequest(), is(httpRequest));
        assertThat(expectationWithObjectCallback.getTimes(), is(Times.exactly(3)));
        assertNull(expectationWithObjectCallback.getHttpResponse());
        assertNull(expectationWithObjectCallback.getHttpForward());
        assertNull(expectationWithObjectCallback.getHttpError());
        assertNull(expectationWithObjectCallback.getHttpClassCallback());
        assertThat(expectationWithObjectCallback.getHttpObjectCallback(), is(httpObjectCallback));
    }

    @Test
    public void shouldBuildObjectWithNulls() {
        // when
        Expectation expectation = new ExpectationDTO(new Expectation(null, null, TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward(null).thenError(null).thenCallback((HttpClassCallback)null).thenCallback((HttpObjectCallback)null)).buildObject();

        // then
        assertThat(expectation.getHttpRequest(), is(nullValue()));
        assertThat(expectation.getTimes(), is(Times.once()));
        assertThat(expectation.getHttpResponse(), is(nullValue()));
        assertThat(expectation.getHttpForward(), is(nullValue()));
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
        HttpForwardDTO httpForward = new HttpForwardDTO(new HttpForward().withHost("some_host"));
        HttpErrorDTO httpError = new HttpErrorDTO(new HttpError().withResponseBytes("some_bytes".getBytes()));
        HttpClassCallbackDTO httpClassCallback = new HttpClassCallbackDTO(new HttpClassCallback().withCallbackClass("some_class"));
        HttpObjectCallbackDTO httpObjectCallback = new HttpObjectCallbackDTO(new HttpObjectCallback().withClientId("some_client_id"));

        // when
        ExpectationDTO expectation = new ExpectationDTO();
        expectation.setHttpRequest(httpRequest);
        expectation.setTimes(times);
        expectation.setHttpResponse(httpResponse);
        expectation.setHttpForward(httpForward);
        expectation.setHttpError(httpError);
        expectation.setHttpClassCallback(httpClassCallback);
        expectation.setHttpObjectCallback(httpObjectCallback);

        // then
        assertThat(expectation.getHttpRequest(), is(httpRequest));
        assertThat(expectation.getTimes(), is(times));
        assertThat(expectation.getHttpResponse(), is(httpResponse));
        assertThat(expectation.getHttpForward(), is(httpForward));
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
        assertThat(expectationDTO.getHttpForward(), is(nullValue()));
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
        assertThat(expectationDTO.getHttpForward(), is(nullValue()));
        assertThat(expectationDTO.getHttpError(), is(nullValue()));
        assertThat(expectationDTO.getHttpClassCallback(), is(nullValue()));
        assertThat(expectationDTO.getHttpObjectCallback(), is(nullValue()));
    }
}
