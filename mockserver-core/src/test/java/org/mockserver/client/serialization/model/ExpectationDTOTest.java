package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.isNull;

/**
 * @author jamesdbloom
 */
public class ExpectationDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // given
        HttpRequest httpRequest = new HttpRequest().withBody("some_body");
        HttpResponse httpResponse = new HttpResponse().withBody("some_response_body");
        HttpForward httpForward = new HttpForward().withHost("some_host");

        // when
        ExpectationDTO expectationWithResponse = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3)).thenRespond(httpResponse));

        // then
        assertThat(expectationWithResponse.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithResponse.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertThat(expectationWithResponse.getHttpResponse(), is(new HttpResponseDTO(httpResponse)));
        assertNull(expectationWithResponse.getHttpForward());

        // when
        ExpectationDTO expectationWithForward = new ExpectationDTO(new Expectation(httpRequest, Times.exactly(3)).thenForward(httpForward));

        // then
        assertThat(expectationWithForward.getHttpRequest(), is(new HttpRequestDTO(httpRequest)));
        assertThat(expectationWithForward.getTimes(), is(new TimesDTO(Times.exactly(3))));
        assertNull(expectationWithForward.getHttpResponse());
        assertThat(expectationWithForward.getHttpForward(), is(new HttpForwardDTO(httpForward)));
    }

    @Test
    public void shouldReturnValueSetInSetter() {
        // given
        HttpRequestDTO httpRequest = new HttpRequestDTO(new HttpRequest().withBody("some_body"));
        TimesDTO times = new TimesDTO(Times.exactly(3));
        HttpResponseDTO httpResponse = new HttpResponseDTO(new HttpResponse().withBody("some_response_body"));
        HttpForwardDTO httpForward = new HttpForwardDTO(new HttpForward().withHost("some_host"));

        // when
        ExpectationDTO expectation = new ExpectationDTO();
        expectation.setHttpRequest(httpRequest);
        expectation.setTimes(times);
        expectation.setHttpResponse(httpResponse);
        expectation.setHttpForward(httpForward);

        // then
        assertThat(expectation.getHttpRequest(), is(httpRequest));
        assertThat(expectation.getTimes(), is(times));
        assertThat(expectation.getHttpResponse(), is(httpResponse));
        assertThat(expectation.getHttpForward(), is(httpForward));
    }
}
