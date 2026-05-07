package org.mockserver.model;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockserver.model.WebSocketMessage.webSocketMessage;

public class WebSocketMessageTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(webSocketMessage(), webSocketMessage());
        assertNotSame(webSocketMessage(), webSocketMessage());
    }

    @Test
    public void shouldBuildWithAllFields() {
        byte[] binary = new byte[]{1, 2, 3};
        WebSocketMessage message = webSocketMessage()
            .withText("hello")
            .withBinary(binary)
            .withDelay(TimeUnit.MILLISECONDS, 100);

        assertThat(message.getText(), is("hello"));
        assertThat(message.getBinary(), is(binary));
        assertThat(message.getDelay(), is(new Delay(TimeUnit.MILLISECONDS, 100)));
    }

    @Test
    public void shouldBuildWithTextFactory() {
        WebSocketMessage message = webSocketMessage("hello");

        assertThat(message.getText(), is("hello"));
        assertThat(message.getBinary(), is(nullValue()));
        assertThat(message.getDelay(), is(nullValue()));
    }

    @Test
    public void shouldHaveNullDefaultValues() {
        WebSocketMessage message = webSocketMessage();

        assertThat(message.getText(), is(nullValue()));
        assertThat(message.getBinary(), is(nullValue()));
        assertThat(message.getDelay(), is(nullValue()));
    }

    @Test
    public void shouldReturnText() {
        assertThat(webSocketMessage().withText("hello").getText(), is("hello"));
    }

    @Test
    public void shouldReturnBinary() {
        byte[] binary = new byte[]{4, 5, 6};
        assertThat(webSocketMessage().withBinary(binary).getBinary(), is(binary));
    }

    @Test
    public void shouldSetDelayWithTimeUnit() {
        WebSocketMessage message = webSocketMessage().withDelay(TimeUnit.MILLISECONDS, 100);

        assertThat(message.getDelay(), is(new Delay(TimeUnit.MILLISECONDS, 100)));
    }

    @Test
    public void shouldSetDelayWithDelayObject() {
        WebSocketMessage message = webSocketMessage().withDelay(Delay.milliseconds(100));

        assertThat(message.getDelay(), is(new Delay(TimeUnit.MILLISECONDS, 100)));
    }

    @Test
    public void shouldBeEqualWhenSameValues() {
        byte[] binary = new byte[]{1, 2, 3};
        WebSocketMessage messageOne = webSocketMessage()
            .withText("hello")
            .withBinary(binary)
            .withDelay(TimeUnit.MILLISECONDS, 100);
        WebSocketMessage messageTwo = webSocketMessage()
            .withText("hello")
            .withBinary(binary)
            .withDelay(TimeUnit.MILLISECONDS, 100);

        assertThat(messageOne, is(messageTwo));
    }

    @Test
    public void shouldHaveSameHashCodeWhenEqual() {
        byte[] binary = new byte[]{1, 2, 3};
        WebSocketMessage messageOne = webSocketMessage()
            .withText("hello")
            .withBinary(binary);
        WebSocketMessage messageTwo = webSocketMessage()
            .withText("hello")
            .withBinary(binary);

        assertThat(messageOne.hashCode(), is(messageTwo.hashCode()));
    }

    @Test
    public void shouldNotBeEqualWhenDifferentText() {
        assertThat(webSocketMessage().withText("hello"), is(not(webSocketMessage().withText("world"))));
    }

    @Test
    public void shouldNotBeEqualWhenDifferentBinary() {
        assertThat(
            webSocketMessage().withBinary(new byte[]{1, 2}),
            is(not(webSocketMessage().withBinary(new byte[]{3, 4})))
        );
    }

    @Test
    public void shouldNotBeEqualWhenDifferentDelay() {
        assertThat(
            webSocketMessage().withDelay(TimeUnit.MILLISECONDS, 100),
            is(not(webSocketMessage().withDelay(TimeUnit.SECONDS, 5)))
        );
    }

    @Test
    public void shouldNotBeEqualToNull() {
        assertThat(webSocketMessage().withText("hello").equals(null), is(false));
    }

    @Test
    public void shouldNotBeEqualToDifferentType() {
        assertThat(webSocketMessage().withText("hello").equals("hello"), is(false));
    }

    @Test
    public void shouldBeEqualToItself() {
        WebSocketMessage message = webSocketMessage().withText("hello");

        assertThat(message, is(message));
    }
}
