package org.mockserver.client.serialization.model;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class WebSocketMessageDTOTest {

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        String type = "some_type";
        String value = "some_value";

        // when
        WebSocketMessageDTO webSocketMessageDTO = new WebSocketMessageDTO();
        webSocketMessageDTO.setType(type);
        webSocketMessageDTO.setValue(value);

        // then
        assertThat(webSocketMessageDTO.getType(), is(type));
        assertThat(webSocketMessageDTO.getValue(), is(value));
    }

    @Test
    public void shouldHandleNullValuesSetInSetter() {
        // given
        String type = null;
        String value = null;

        // when
        WebSocketMessageDTO webSocketMessageDTO = new WebSocketMessageDTO();
        webSocketMessageDTO.setType(type);
        webSocketMessageDTO.setValue(value);

        // then
        assertThat(webSocketMessageDTO.getType(), nullValue());
        assertThat(webSocketMessageDTO.getValue(), nullValue());
    }
}
