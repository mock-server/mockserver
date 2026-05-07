package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Delay;
import org.mockserver.model.WebSocketMessage;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class WebSocketMessageModelDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        Delay delay = new Delay(TimeUnit.MILLISECONDS, 100);
        byte[] binary = new byte[]{1, 2, 3};
        WebSocketMessage webSocketMessage = WebSocketMessage.webSocketMessage()
            .withText("hello")
            .withBinary(binary)
            .withDelay(delay);

        WebSocketMessageModelDTO dto = new WebSocketMessageModelDTO(webSocketMessage);

        assertThat(dto.getText(), is("hello"));
        assertThat(dto.getBinary(), is(binary));
        assertThat(dto.getDelay(), is(new DelayDTO(delay)));
    }

    @Test
    public void shouldBuildObject() {
        Delay delay = new Delay(TimeUnit.MILLISECONDS, 100);
        byte[] binary = new byte[]{1, 2, 3};
        WebSocketMessage webSocketMessage = WebSocketMessage.webSocketMessage()
            .withText("hello")
            .withBinary(binary)
            .withDelay(delay);

        WebSocketMessage builtMessage = new WebSocketMessageModelDTO(webSocketMessage).buildObject();

        assertThat(builtMessage.getText(), is("hello"));
        assertThat(builtMessage.getBinary(), is(binary));
        assertThat(builtMessage.getDelay(), is(delay));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        DelayDTO delay = new DelayDTO(new Delay(TimeUnit.MILLISECONDS, 100));
        byte[] binary = new byte[]{1, 2, 3};

        WebSocketMessageModelDTO dto = new WebSocketMessageModelDTO(null);
        dto.setText("hello");
        dto.setBinary(binary);
        dto.setDelay(delay);

        assertThat(dto.getText(), is("hello"));
        assertThat(dto.getBinary(), is(binary));
        assertThat(dto.getDelay(), is(delay));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        WebSocketMessageModelDTO dto = new WebSocketMessageModelDTO(null);

        assertThat(dto.getText(), is(nullValue()));
        assertThat(dto.getBinary(), is(nullValue()));
        assertThat(dto.getDelay(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        WebSocketMessageModelDTO dto = new WebSocketMessageModelDTO(WebSocketMessage.webSocketMessage());

        assertThat(dto.getText(), is(nullValue()));
        assertThat(dto.getBinary(), is(nullValue()));
        assertThat(dto.getDelay(), is(nullValue()));
    }
}
