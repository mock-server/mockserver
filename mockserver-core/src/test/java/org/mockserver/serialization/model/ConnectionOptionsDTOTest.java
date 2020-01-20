package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.Delay;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ConnectionOptionsDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        ConnectionOptionsDTO connectionOptions = new ConnectionOptionsDTO(
            new ConnectionOptions()
                .withSuppressContentLengthHeader(true)
                .withContentLengthHeaderOverride(50)
                .withSuppressConnectionHeader(true)
                .withKeepAliveOverride(true)
                .withCloseSocket(true)
                .withCloseSocketDelay(new Delay(SECONDS, 10))
        );

        // then
        assertThat(connectionOptions.getSuppressContentLengthHeader(), is(true));
        assertThat(connectionOptions.getContentLengthHeaderOverride(), is(50));
        assertThat(connectionOptions.getSuppressConnectionHeader(), is(true));
        assertThat(connectionOptions.getKeepAliveOverride(), is(true));
        assertThat(connectionOptions.getCloseSocket(), is(true));
        assertThat(connectionOptions.getCloseSocketDelay(), is(new DelayDTO(new Delay(SECONDS, 10))));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // when
        ConnectionOptionsDTO connectionOptions = new ConnectionOptionsDTO();
        connectionOptions.setSuppressContentLengthHeader(true);
        connectionOptions.setContentLengthHeaderOverride(50);
        connectionOptions.setSuppressConnectionHeader(true);
        connectionOptions.setKeepAliveOverride(true);
        connectionOptions.setCloseSocket(true);
        connectionOptions.setCloseSocketDelay(new DelayDTO(new Delay(SECONDS, 10)));

        // then
        assertThat(connectionOptions.getSuppressContentLengthHeader(), is(true));
        assertThat(connectionOptions.getContentLengthHeaderOverride(), is(50));
        assertThat(connectionOptions.getSuppressConnectionHeader(), is(true));
        assertThat(connectionOptions.getKeepAliveOverride(), is(true));
        assertThat(connectionOptions.getCloseSocket(), is(true));
        assertThat(connectionOptions.getCloseSocketDelay(), is(new DelayDTO(new Delay(SECONDS, 10))));
    }

    @Test
    public void shouldHandleNullInput() {
        // when
        ConnectionOptionsDTO connectionOptions = new ConnectionOptionsDTO(null);

        // then
        assertThat(connectionOptions.getSuppressContentLengthHeader(), nullValue());
        assertThat(connectionOptions.getContentLengthHeaderOverride(), nullValue());
        assertThat(connectionOptions.getSuppressConnectionHeader(), nullValue());
        assertThat(connectionOptions.getKeepAliveOverride(), nullValue());
        assertThat(connectionOptions.getCloseSocket(), nullValue());
        assertThat(connectionOptions.getCloseSocketDelay(), nullValue());
    }


}