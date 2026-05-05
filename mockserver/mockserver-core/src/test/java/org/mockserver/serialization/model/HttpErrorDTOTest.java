package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;

import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class HttpErrorDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        Delay delay = new Delay(TimeUnit.HOURS, 1);
        Boolean dropConnection = Boolean.TRUE;
        byte[] responseBytes = "some_bytes".getBytes(UTF_8);

        HttpError httpError = new HttpError()
                .withDelay(delay)
                .withDropConnection(dropConnection)
                .withResponseBytes(responseBytes);

        // when
        HttpErrorDTO httpErrorDTO = new HttpErrorDTO(httpError);

        // then
        assertThat(httpErrorDTO.getDelay(), is(new DelayDTO(delay)));
        assertThat(httpErrorDTO.getDropConnection(), is(dropConnection));
        assertThat(httpErrorDTO.getResponseBytes(), is(responseBytes));
    }

    @Test
    public void shouldBuildObject() {
        // given
        Delay delay = new Delay(TimeUnit.HOURS, 1);
        Boolean dropConnection = Boolean.TRUE;
        byte[] responseBytes = "some_bytes".getBytes(UTF_8);

        HttpError httpError = new HttpError()
                .withDelay(delay)
                .withDropConnection(dropConnection)
                .withResponseBytes(responseBytes);

        // when
        HttpError builtHttpError = new HttpErrorDTO(httpError).buildObject();

        // then
        assertThat(builtHttpError.getDelay(), is(delay));
        assertThat(builtHttpError.getDropConnection(), is(dropConnection));
        assertThat(builtHttpError.getResponseBytes(), is(responseBytes));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        DelayDTO delay = new DelayDTO(new Delay(TimeUnit.HOURS, 1));
        Boolean dropConnection = Boolean.TRUE;
        byte[] responseBytes = "some_bytes".getBytes(UTF_8);

        HttpError httpError = new HttpError();

        // when
        HttpErrorDTO httpErrorDTO = new HttpErrorDTO(httpError);
        httpErrorDTO.setDelay(delay);
        httpErrorDTO.setDropConnection(dropConnection);
        httpErrorDTO.setResponseBytes(responseBytes);

        // then
        assertThat(httpErrorDTO.getDelay(), is(delay));
        assertThat(httpErrorDTO.getDropConnection(), is(dropConnection));
        assertThat(httpErrorDTO.getResponseBytes(), is(responseBytes));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpErrorDTO httpErrorDTO = new HttpErrorDTO(null);

        // then
        assertThat(httpErrorDTO.getDelay(), is(nullValue()));
        assertThat(httpErrorDTO.getDropConnection(), is(nullValue()));
        assertThat(httpErrorDTO.getResponseBytes(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpErrorDTO httpErrorDTO = new HttpErrorDTO(new HttpError());

        // then
        assertThat(httpErrorDTO.getDelay(), is(nullValue()));
        assertThat(httpErrorDTO.getDropConnection(), is(nullValue()));
        assertThat(httpErrorDTO.getResponseBytes(), is(nullValue()));
    }
}
