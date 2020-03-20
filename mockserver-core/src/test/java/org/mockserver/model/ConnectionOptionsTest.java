package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ConnectionOptionsTest {

    @Test
    public void shouldReturnValuesSetInWithMethods() {
        // when
        ConnectionOptions connectionOptions = new ConnectionOptions()
            .withSuppressContentLengthHeader(true)
            .withContentLengthHeaderOverride(50)
            .withSuppressConnectionHeader(true)
            .withChunkSize(100)
            .withKeepAliveOverride(true)
            .withCloseSocket(true);

        // then
        assertThat(connectionOptions.getSuppressContentLengthHeader(), is(true));
        assertThat(connectionOptions.getContentLengthHeaderOverride(), is(50));
        assertThat(connectionOptions.getSuppressConnectionHeader(), is(true));
        assertThat(connectionOptions.getChunkSize(), is(100));
        assertThat(connectionOptions.getKeepAliveOverride(), is(true));
        assertThat(connectionOptions.getCloseSocket(), is(true));
    }

    @Test
    public void shouldTestFalseOrNull() {
        assertThat(ConnectionOptions.isFalseOrNull(false), is(true));
        assertThat(ConnectionOptions.isFalseOrNull(null), is(true));
        assertThat(ConnectionOptions.isFalseOrNull(true), is(false));
    }

}