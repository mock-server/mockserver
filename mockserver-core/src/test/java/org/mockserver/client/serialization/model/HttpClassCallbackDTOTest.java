package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.HttpClassCallback;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpClassCallback.callback;

/**
 * @author jamesdbloom
 */
public class HttpClassCallbackDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        String callbackClass = HttpClassCallbackDTOTest.class.getName();

        HttpClassCallback httpClassCallback = callback(callbackClass);

        // when
        HttpClassCallbackDTO httpClassCallbackDTO = new HttpClassCallbackDTO(httpClassCallback);

        // then
        assertThat(httpClassCallbackDTO.getCallbackClass(), is(callbackClass));
    }

    @Test
    public void shouldBuildObject() {
        // given
        String callbackClass = HttpClassCallbackDTOTest.class.getName();

        HttpClassCallback httpClassCallback = new HttpClassCallback()
                .withCallbackClass(callbackClass);

        // when
        HttpClassCallback builtHttpClassCallback = new HttpClassCallbackDTO(httpClassCallback).buildObject();

        // then
        assertThat(builtHttpClassCallback.getCallbackClass(), is(callbackClass));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        String callbackClass = HttpClassCallbackDTOTest.class.getName();

        HttpClassCallback httpClassCallback = new HttpClassCallback();

        // when
        HttpClassCallbackDTO httpClassCallbackDTO = new HttpClassCallbackDTO(httpClassCallback);
        httpClassCallbackDTO.setCallbackClass(callbackClass);

        // then
        assertThat(httpClassCallbackDTO.getCallbackClass(), is(callbackClass));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpClassCallbackDTO httpClassCallbackDTO = new HttpClassCallbackDTO(null);

        // then
        assertThat(httpClassCallbackDTO.getCallbackClass(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpClassCallbackDTO httpClassCallbackDTO = new HttpClassCallbackDTO(new HttpClassCallback());

        // then
        assertThat(httpClassCallbackDTO.getCallbackClass(), is(nullValue()));
    }
}
