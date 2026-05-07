package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpWebSocketResponse;
import org.mockserver.model.WebSocketMessage;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class HttpWebSocketResponseDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        Delay delay = new Delay(TimeUnit.HOURS, 1);
        String subprotocol = "graphql-ws";
        Boolean closeConnection = Boolean.TRUE;
        WebSocketMessage message1 = WebSocketMessage.webSocketMessage().withText("hello");
        WebSocketMessage message2 = WebSocketMessage.webSocketMessage().withText("world");

        HttpWebSocketResponse httpWebSocketResponse = HttpWebSocketResponse.webSocketResponse()
            .withDelay(delay)
            .withSubprotocol(subprotocol)
            .withCloseConnection(closeConnection)
            .withMessages(message1, message2);

        HttpWebSocketResponseDTO dto = new HttpWebSocketResponseDTO(httpWebSocketResponse);

        assertThat(dto.getDelay(), is(new DelayDTO(delay)));
        assertThat(dto.getSubprotocol(), is(subprotocol));
        assertThat(dto.getCloseConnection(), is(closeConnection));
        assertThat(dto.getMessages(), is(Arrays.asList(
            new WebSocketMessageModelDTO(message1),
            new WebSocketMessageModelDTO(message2)
        )));
    }

    @Test
    public void shouldBuildObject() {
        Delay delay = new Delay(TimeUnit.HOURS, 1);
        String subprotocol = "graphql-ws";
        Boolean closeConnection = Boolean.TRUE;
        WebSocketMessage message1 = WebSocketMessage.webSocketMessage().withText("hello");
        WebSocketMessage message2 = WebSocketMessage.webSocketMessage().withText("world");

        HttpWebSocketResponse httpWebSocketResponse = HttpWebSocketResponse.webSocketResponse()
            .withDelay(delay)
            .withSubprotocol(subprotocol)
            .withCloseConnection(closeConnection)
            .withMessages(message1, message2);

        HttpWebSocketResponse builtResponse = new HttpWebSocketResponseDTO(httpWebSocketResponse).buildObject();

        assertThat(builtResponse.getDelay(), is(delay));
        assertThat(builtResponse.getSubprotocol(), is(subprotocol));
        assertThat(builtResponse.getCloseConnection(), is(closeConnection));
        assertThat(builtResponse.getMessages(), is(Arrays.asList(message1, message2)));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        DelayDTO delay = new DelayDTO(new Delay(TimeUnit.HOURS, 1));
        String subprotocol = "graphql-ws";
        Boolean closeConnection = Boolean.TRUE;
        List<WebSocketMessageModelDTO> messages = Arrays.asList(
            new WebSocketMessageModelDTO(WebSocketMessage.webSocketMessage().withText("hello")),
            new WebSocketMessageModelDTO(WebSocketMessage.webSocketMessage().withText("world"))
        );

        HttpWebSocketResponseDTO dto = new HttpWebSocketResponseDTO(null);
        dto.setDelay(delay);
        dto.setSubprotocol(subprotocol);
        dto.setCloseConnection(closeConnection);
        dto.setMessages(messages);

        assertThat(dto.getDelay(), is(delay));
        assertThat(dto.getSubprotocol(), is(subprotocol));
        assertThat(dto.getCloseConnection(), is(closeConnection));
        assertThat(dto.getMessages(), is(messages));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        HttpWebSocketResponseDTO dto = new HttpWebSocketResponseDTO(null);

        assertThat(dto.getDelay(), is(nullValue()));
        assertThat(dto.getSubprotocol(), is(nullValue()));
        assertThat(dto.getCloseConnection(), is(nullValue()));
        assertThat(dto.getMessages(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        HttpWebSocketResponseDTO dto = new HttpWebSocketResponseDTO(HttpWebSocketResponse.webSocketResponse());

        assertThat(dto.getDelay(), is(nullValue()));
        assertThat(dto.getSubprotocol(), is(nullValue()));
        assertThat(dto.getCloseConnection(), is(nullValue()));
        assertThat(dto.getMessages(), is(nullValue()));
    }
}
