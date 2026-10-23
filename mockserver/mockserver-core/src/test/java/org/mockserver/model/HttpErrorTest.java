package org.mockserver.model;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpError.error;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.hamcrest.CoreMatchers.not;
/**
 * @author jamesdbloom
 */
public class HttpErrorTest {

    @Test
    @SuppressWarnings("AccessStaticViaInstance")
    public void shouldAlwaysCreateNewObject() {
        assertThat(error(), is(error()));
        assertThat(error(), not(sameInstance(error())));
    }

    @Test
    public void returnsDelay() {
        assertThat(new HttpError().withDelay(TimeUnit.DAYS, 10).getDelay(), is(new Delay(TimeUnit.DAYS, 10)));
    }

    @Test
    public void returnsDropConnection() {
        assertThat(new HttpError().withDropConnection(true).getDropConnection(), is(Boolean.TRUE));
    }

    @Test
    public void returnsResponseBytes() {
        assertArrayEquals("some_bytes".getBytes(UTF_8), new HttpError().withResponseBytes("some_bytes".getBytes(UTF_8)).getResponseBytes());
    }

    @Test
    public void streamErrorIsNullByDefault() {
        assertThat(error().getStreamError(), is(org.hamcrest.CoreMatchers.nullValue()));
    }

    @Test
    public void returnsStreamErrorFromRawCode() {
        assertThat(new HttpError().withStreamError(0x7L).getStreamError(), is(0x7L));
    }

    @Test
    public void returnsStreamErrorFromWellKnownCode() {
        assertThat(new HttpError().withStreamError(HttpError.StreamErrorCode.REFUSED_STREAM).getStreamError(), is(0x7L));
        assertThat(new HttpError().withStreamError(HttpError.StreamErrorCode.H3_REQUEST_CANCELLED).getStreamError(), is(0x10cL));
    }

    @Test
    public void returnsStreamErrorFromCodeName() {
        assertThat(new HttpError().withStreamErrorCodeName("REFUSED_STREAM").getStreamError(), is(0x7L));
        assertThat(new HttpError().withStreamErrorCodeName("h3_request_cancelled").getStreamError(), is(0x10cL));
    }

    @Test
    public void rejectsUnknownStreamErrorCodeName() {
        try {
            new HttpError().withStreamErrorCodeName("NOT_A_REAL_CODE");
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage().contains("NOT_A_REAL_CODE"), is(true));
        }
    }

    @Test
    public void streamErrorAffectsEqualsAndHashCode() {
        assertThat(error().withStreamError(0x7L), is(error().withStreamError(0x7L)));
        assertThat(error().withStreamError(0x7L), not(error().withStreamError(0x8L)));
        assertThat(error().withStreamError(0x7L), not(error()));
        assertThat(error().withStreamError(0x7L).hashCode(), is(error().withStreamError(0x7L).hashCode()));
    }

    @Test
    public void returnsResetConnection() {
        assertThat(error().getResetConnection(), is(org.hamcrest.CoreMatchers.nullValue()));
        assertThat(new HttpError().withResetConnection(true).getResetConnection(), is(true));
    }

    @Test
    public void resetConnectionAffectsEqualsAndHashCode() {
        assertThat(error().withResetConnection(true), is(error().withResetConnection(true)));
        assertThat(error().withResetConnection(true), not(error().withDropConnection(true)));
        assertThat(error().withResetConnection(true), not(error()));
        assertThat(error().withResetConnection(true).hashCode(), is(error().withResetConnection(true).hashCode()));
    }

    @Test
    public void includesStreamErrorInToString() {
        assertThat(error().withStreamError(HttpError.StreamErrorCode.REFUSED_STREAM).toString().contains("\"streamError\" : 7"), is(true));
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        assertThat(error()
                .withDelay(TimeUnit.DAYS, 10)
                .withDropConnection(true)
                .withResponseBytes("some_bytes".getBytes(UTF_8))
                .toString(), is("{" + NEW_LINE +
                "  \"delay\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"DAYS\"," + NEW_LINE +
                "    \"value\" : 10" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"dropConnection\" : true," + NEW_LINE +
                "  \"responseBytes\" : \"c29tZV9ieXRlcw==\"" + NEW_LINE +
                "}"));
    }
}
