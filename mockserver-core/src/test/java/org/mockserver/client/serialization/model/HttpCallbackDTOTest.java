package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.HttpCallback;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class HttpCallbackDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        String callbackClass = HttpCallbackDTOTest.class.getName();

        HttpCallback httpCallback = new HttpCallback()
                .withCallbackClass(callbackClass);

        // when
        HttpCallbackDTO httpCallbackDTO = new HttpCallbackDTO(httpCallback);

        // then
        assertThat(httpCallbackDTO.getCallbackClass(), is(callbackClass));
    }

    @Test
    public void shouldBuildObject() {
        // given
        String callbackClass = HttpCallbackDTOTest.class.getName();

        HttpCallback httpCallback = new HttpCallback()
                .withCallbackClass(callbackClass);

        // when
        HttpCallback builtHttpCallback = new HttpCallbackDTO(httpCallback).buildObject();

        // then
        assertThat(builtHttpCallback.getCallbackClass(), is(callbackClass));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        String callbackClass = HttpCallbackDTOTest.class.getName();

        HttpCallback httpCallback = new HttpCallback();

        // when
        HttpCallbackDTO httpCallbackDTO = new HttpCallbackDTO(httpCallback);
        httpCallbackDTO.setCallbackClass(callbackClass);

        // then
        assertThat(httpCallbackDTO.getCallbackClass(), is(callbackClass));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpCallbackDTO httpCallbackDTO = new HttpCallbackDTO(null);

        // then
        assertThat(httpCallbackDTO.getCallbackClass(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpCallbackDTO httpCallbackDTO = new HttpCallbackDTO(new HttpCallback());

        // then
        assertThat(httpCallbackDTO.getCallbackClass(), is(nullValue()));
    }
}
