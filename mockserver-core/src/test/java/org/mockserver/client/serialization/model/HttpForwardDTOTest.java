package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.HttpForward;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class HttpForwardDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        String host = "some_host";
        int port = 9090;
        HttpForward.Scheme scheme = HttpForward.Scheme.HTTPS;

        HttpForward httpForward = new HttpForward()
                .withHost(host)
                .withPort(port)
                .withScheme(scheme);

        // when
        HttpForwardDTO httpForwardDTO = new HttpForwardDTO(httpForward);

        // then
        assertThat(httpForwardDTO.getHost(), is(host));
        assertThat(httpForwardDTO.getPort(), is(port));
        assertThat(httpForwardDTO.getScheme(), is(scheme));
    }

    @Test
    public void shouldBuildObject() {
        // given
        String host = "some_host";
        int port = 9090;
        HttpForward.Scheme scheme = HttpForward.Scheme.HTTPS;

        HttpForward httpForward = new HttpForward()
                .withHost(host)
                .withPort(port)
                .withScheme(scheme);

        // when
        HttpForward builtHttpForward = new HttpForwardDTO(httpForward).buildObject();

        // then
        assertThat(builtHttpForward.getHost(), is(host));
        assertThat(builtHttpForward.getPort(), is(port));
        assertThat(builtHttpForward.getScheme(), is(scheme));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        String host = "some_host";
        int port = 9090;
        HttpForward.Scheme scheme = HttpForward.Scheme.HTTPS;

        HttpForward httpForward = new HttpForward();

        // when
        HttpForwardDTO httpForwardDTO = new HttpForwardDTO(httpForward);
        httpForwardDTO.setHost(host);
        httpForwardDTO.setPort(port);
        httpForwardDTO.setScheme(scheme);

        // then
        assertThat(httpForwardDTO.getHost(), is(host));
        assertThat(httpForwardDTO.getPort(), is(port));
        assertThat(httpForwardDTO.getScheme(), is(scheme));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpForwardDTO httpForwardDTO = new HttpForwardDTO(null);

        // then
        assertThat(httpForwardDTO.getHost(), is(nullValue()));
        assertThat(httpForwardDTO.getPort(), is(nullValue()));
        assertThat(httpForwardDTO.getScheme(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpForwardDTO httpForwardDTO = new HttpForwardDTO(new HttpForward());

        // then
        assertThat(httpForwardDTO.getHost(), is(nullValue()));
        assertThat(httpForwardDTO.getPort(), is(80));
        assertThat(httpForwardDTO.getScheme(), is(HttpForward.Scheme.HTTP));
    }
}
