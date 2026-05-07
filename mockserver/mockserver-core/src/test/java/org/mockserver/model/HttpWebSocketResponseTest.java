package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockserver.model.HttpWebSocketResponse.webSocketResponse;
import static org.mockserver.model.WebSocketMessage.webSocketMessage;

public class HttpWebSocketResponseTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(webSocketResponse(), webSocketResponse());
        assertNotSame(webSocketResponse(), webSocketResponse());
    }

    @Test
    public void shouldBuildWithAllFields() {
        WebSocketMessage message = webSocketMessage("hello");
        HttpWebSocketResponse response = webSocketResponse()
            .withSubprotocol("graphql-ws")
            .withMessage(message)
            .withCloseConnection(true);

        assertThat(response.getSubprotocol(), is("graphql-ws"));
        assertThat(response.getMessages(), hasSize(1));
        assertThat(response.getMessages().get(0), is(message));
        assertThat(response.getCloseConnection(), is(true));
    }

    @Test
    public void shouldHaveNullDefaultValues() {
        HttpWebSocketResponse response = webSocketResponse();

        assertThat(response.getSubprotocol(), is(nullValue()));
        assertThat(response.getMessages(), is(nullValue()));
        assertThat(response.getCloseConnection(), is(nullValue()));
    }

    @Test
    public void shouldReturnType() {
        assertThat(webSocketResponse().getType(), is(Action.Type.WEBSOCKET_RESPONSE));
    }

    @Test
    public void shouldReturnSubprotocol() {
        assertThat(webSocketResponse().withSubprotocol("graphql-ws").getSubprotocol(), is("graphql-ws"));
    }

    @Test
    public void shouldReturnCloseConnection() {
        assertThat(webSocketResponse().withCloseConnection(true).getCloseConnection(), is(true));
        assertThat(webSocketResponse().withCloseConnection(false).getCloseConnection(), is(false));
    }

    @Test
    public void shouldAddSingleMessage() {
        WebSocketMessage messageOne = webSocketMessage("msg1");
        WebSocketMessage messageTwo = webSocketMessage("msg2");

        HttpWebSocketResponse response = webSocketResponse()
            .withMessage(messageOne)
            .withMessage(messageTwo);

        assertThat(response.getMessages(), hasSize(2));
        assertThat(response.getMessages(), containsInAnyOrder(messageOne, messageTwo));
    }

    @Test
    public void shouldAddMultipleMessagesViaVarargs() {
        WebSocketMessage messageOne = webSocketMessage("msg1");
        WebSocketMessage messageTwo = webSocketMessage("msg2");

        HttpWebSocketResponse response = webSocketResponse()
            .withMessages(messageOne, messageTwo);

        assertThat(response.getMessages(), hasSize(2));
        assertThat(response.getMessages(), containsInAnyOrder(messageOne, messageTwo));
    }

    @Test
    public void shouldAddMultipleMessagesViaList() {
        WebSocketMessage messageOne = webSocketMessage("msg1");
        WebSocketMessage messageTwo = webSocketMessage("msg2");
        List<WebSocketMessage> messages = Arrays.asList(messageOne, messageTwo);

        HttpWebSocketResponse response = webSocketResponse()
            .withMessages(messages);

        assertThat(response.getMessages(), hasSize(2));
        assertThat(response.getMessages(), containsInAnyOrder(messageOne, messageTwo));
    }

    @Test
    public void shouldBeEqualWhenSameValues() {
        WebSocketMessage message = webSocketMessage("hello");
        HttpWebSocketResponse responseOne = webSocketResponse()
            .withSubprotocol("graphql-ws")
            .withMessages(message)
            .withCloseConnection(true);
        HttpWebSocketResponse responseTwo = webSocketResponse()
            .withSubprotocol("graphql-ws")
            .withMessages(message)
            .withCloseConnection(true);

        assertThat(responseOne, is(responseTwo));
    }

    @Test
    public void shouldHaveSameHashCodeWhenEqual() {
        WebSocketMessage message = webSocketMessage("hello");
        HttpWebSocketResponse responseOne = webSocketResponse()
            .withSubprotocol("graphql-ws")
            .withMessages(message)
            .withCloseConnection(true);
        HttpWebSocketResponse responseTwo = webSocketResponse()
            .withSubprotocol("graphql-ws")
            .withMessages(message)
            .withCloseConnection(true);

        assertThat(responseOne.hashCode(), is(responseTwo.hashCode()));
    }

    @Test
    public void shouldNotBeEqualWhenDifferentSubprotocol() {
        assertThat(
            webSocketResponse().withSubprotocol("graphql-ws"),
            is(not(webSocketResponse().withSubprotocol("other")))
        );
    }

    @Test
    public void shouldNotBeEqualWhenDifferentMessages() {
        assertThat(
            webSocketResponse().withMessages(webSocketMessage("msg1")),
            is(not(webSocketResponse().withMessages(webSocketMessage("msg2"))))
        );
    }

    @Test
    public void shouldNotBeEqualWhenDifferentCloseConnection() {
        assertThat(
            webSocketResponse().withCloseConnection(true),
            is(not(webSocketResponse().withCloseConnection(false)))
        );
    }

    @Test
    public void shouldNotBeEqualToNull() {
        assertThat(webSocketResponse().withSubprotocol("graphql-ws").equals(null), is(false));
    }

    @Test
    public void shouldNotBeEqualToDifferentType() {
        assertThat(webSocketResponse().withSubprotocol("graphql-ws").equals("response"), is(false));
    }

    @Test
    public void shouldBeEqualToItself() {
        HttpWebSocketResponse response = webSocketResponse().withSubprotocol("graphql-ws");

        assertThat(response, is(response));
    }

    @Test
    public void shouldSupportDelay() {
        HttpWebSocketResponse response = webSocketResponse()
            .withDelay(TimeUnit.SECONDS, 3);

        assertThat(response.getDelay(), is(new Delay(TimeUnit.SECONDS, 3)));
    }

    @Test
    public void shouldSupportDelayObject() {
        HttpWebSocketResponse response = webSocketResponse()
            .withDelay(Delay.milliseconds(500));

        assertThat(response.getDelay(), is(new Delay(TimeUnit.MILLISECONDS, 500)));
    }
}
