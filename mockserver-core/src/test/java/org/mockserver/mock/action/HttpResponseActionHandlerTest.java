package org.mockserver.mock.action;

import org.junit.Test;
import org.mockserver.model.HttpResponse;

import static org.mockito.Mockito.*;

/**
 * @author jamesdbloom
 */
public class HttpResponseActionHandlerTest {

    @Test
    public void shouldHandleHttpRequests() {
        // given
        HttpResponse httpResponse = mock(HttpResponse.class);
        HttpResponseActionHandler httpResponseActionHandler = new HttpResponseActionHandler();
        when(httpResponse.applyDelay()).thenReturn(httpResponse);

        // when
        httpResponseActionHandler.handle(httpResponse);

        // then
        verify(httpResponse).applyDelay();
        verify(httpResponse).clone();
    }

}