package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.SocketAddress.socketAddress;

/**
 * @author jamesdbloom
 */
public class SocketAddressTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        String host = "some_host";
        int port = 9090;
        SocketAddress.Scheme scheme = SocketAddress.Scheme.HTTPS;

        // when
        SocketAddress socketAddress = new SocketAddress()
            .withHost(host)
            .withPort(port)
            .withScheme(scheme);

        // then
        assertThat(socketAddress.getHost(), is(host));
        assertThat(socketAddress.getPort(), is(port));
        assertThat(socketAddress.getScheme(), is(scheme));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        String host = "some_host";
        int port = 9090;
        SocketAddress.Scheme scheme = SocketAddress.Scheme.HTTPS;


        // when
        SocketAddress socketAddress =
            socketAddress()
                .withHost(host)
                .withPort(port)
                .withScheme(scheme);

        // then
        assertThat(socketAddress.getHost(), is(host));
        assertThat(socketAddress.getPort(), is(port));
        assertThat(socketAddress.getScheme(), is(scheme));
    }

}
