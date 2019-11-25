package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.SocketAddress;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class SocketAddressDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        String host = "some_host";
        int port = 9090;
        SocketAddress.Scheme scheme = SocketAddress.Scheme.HTTPS;

        SocketAddress socketAddress = new SocketAddress()
            .withHost(host)
            .withPort(port)
            .withScheme(scheme);

        // when
        SocketAddressDTO socketAddressDTO = new SocketAddressDTO(socketAddress);

        // then
        assertThat(socketAddressDTO.getHost(), is(host));
        assertThat(socketAddressDTO.getPort(), is(port));
        assertThat(socketAddressDTO.getScheme(), is(scheme));
    }

    @Test
    public void shouldBuildObject() {
        // given
        String host = "some_host";
        int port = 9090;
        SocketAddress.Scheme scheme = SocketAddress.Scheme.HTTPS;

        SocketAddress socketAddress = new SocketAddress()
            .withHost(host)
            .withPort(port)
            .withScheme(scheme);

        // when
        SocketAddress builtSocketAddress = new SocketAddressDTO(socketAddress).buildObject();

        // then
        assertThat(builtSocketAddress.getHost(), is(host));
        assertThat(builtSocketAddress.getPort(), is(port));
        assertThat(builtSocketAddress.getScheme(), is(scheme));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        String host = "some_host";
        int port = 9090;
        SocketAddress.Scheme scheme = SocketAddress.Scheme.HTTPS;

        SocketAddress socketAddress = new SocketAddress();

        // when
        SocketAddressDTO socketAddressDTO = new SocketAddressDTO(socketAddress);
        socketAddressDTO.setHost(host);
        socketAddressDTO.setPort(port);
        socketAddressDTO.setScheme(scheme);

        // then
        assertThat(socketAddressDTO.getHost(), is(host));
        assertThat(socketAddressDTO.getPort(), is(port));
        assertThat(socketAddressDTO.getScheme(), is(scheme));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        SocketAddressDTO socketAddressDTO = new SocketAddressDTO(null);

        // then
        assertThat(socketAddressDTO.getHost(), is(nullValue()));
        assertThat(socketAddressDTO.getPort(), is(nullValue()));
        assertThat(socketAddressDTO.getScheme(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        SocketAddressDTO socketAddressDTO = new SocketAddressDTO(new SocketAddress());

        // then
        assertThat(socketAddressDTO.getHost(), is(nullValue()));
        assertThat(socketAddressDTO.getPort(), is(80));
        assertThat(socketAddressDTO.getScheme(), is(SocketAddress.Scheme.HTTP));
    }
}
