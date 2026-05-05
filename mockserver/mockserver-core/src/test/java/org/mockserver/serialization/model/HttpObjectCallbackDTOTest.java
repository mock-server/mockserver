package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.uuid.UUIDService;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class HttpObjectCallbackDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        String clientId = UUIDService.getUUID();

        HttpObjectCallback httpObjectCallback = new HttpObjectCallback()
            .withClientId(clientId);

        // when
        HttpObjectCallbackDTO httpObjectCallbackDTO = new HttpObjectCallbackDTO(httpObjectCallback);

        // then
        assertThat(httpObjectCallbackDTO.getClientId(), is(clientId));
    }

    @Test
    public void shouldBuildObject() {
        // given
        String clientId = UUIDService.getUUID();

        HttpObjectCallback httpObjectCallback = new HttpObjectCallback()
            .withClientId(clientId);

        // when
        HttpObjectCallback builtHttpObjectCallback = new HttpObjectCallbackDTO(httpObjectCallback).buildObject();

        // then
        assertThat(builtHttpObjectCallback.getClientId(), is(clientId));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        String clientId = UUIDService.getUUID();

        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();

        // when
        HttpObjectCallbackDTO httpObjectCallbackDTO = new HttpObjectCallbackDTO(httpObjectCallback);
        httpObjectCallbackDTO.setClientId(clientId);

        // then
        assertThat(httpObjectCallbackDTO.getClientId(), is(clientId));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpObjectCallbackDTO httpObjectCallbackDTO = new HttpObjectCallbackDTO(null);

        // then
        assertThat(httpObjectCallbackDTO.getClientId(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpObjectCallbackDTO httpObjectCallbackDTO = new HttpObjectCallbackDTO(new HttpObjectCallback());

        // then
        assertThat(httpObjectCallbackDTO.getClientId(), is(nullValue()));
    }
}
