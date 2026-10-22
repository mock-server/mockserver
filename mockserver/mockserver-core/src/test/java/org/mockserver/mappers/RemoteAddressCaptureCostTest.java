package org.mockserver.mappers;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Protocol;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.testing.AllocationMeasurement.allocatedBytes;
import static org.mockserver.testing.AllocationMeasurement.assumeHttpRequestIsNotInstrumented;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * Until an expectation uses clientIp, recording a request's remote address must allocate exactly what it did before
 * the matcher existed: the text, once per connection, and nothing per request. After that, only a holder per connection.
 */
public class RemoteAddressCaptureCostTest {

    private static final int OPERATIONS = 20_000;
    // the least of several measurements: background work on a loaded JVM can only add bytes, not remove them
    private static final int ATTEMPTS = 3;
    private static volatile Object sink;
    private final FullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/path");
    private final InetSocketAddress local;
    private final InetSocketAddress[] connections = new InetSocketAddress[OPERATIONS];
    private final InetSocketAddress oneConnection;

    public RemoteAddressCaptureCostTest() throws Exception {
        local = new InetSocketAddress(InetAddress.getByAddress(new byte[]{5, 6, 7, 8}), 8080);
        InetAddress peer = InetAddress.getByAddress(new byte[]{10, 1, 2, 3});
        for (int i = 0; i < OPERATIONS; i++) {
            connections[i] = new InetSocketAddress(peer, 40000);
        }
        oneConnection = connections[0];
    }

    @Test
    public void shouldAddNoAllocationToConnectionsOrRequestsWithoutAClientIpMatcher() {
        FullHttpRequestToMockServerHttpRequest off = mapper(false);
        FullHttpRequestToMockServerHttpRequest on = mapper(true);
        for (int warmUp = 0; warmUp < 3; warmUp++) {
            perRequest(off, false, true);
            perRequest(off, true, true);
            perRequest(off, false, false);
            perRequest(on, false, true);
            perRequest(on, true, true);
        }
        long sameConnectionOff = perRequest(off, false, true);
        long noAddressOff = perRequest(off, false, false);
        long sameConnectionOn = perRequest(on, false, true);
        long newConnectionOff = perRequest(off, true, true);
        long newConnectionOn = perRequest(on, true, true);

        // the two arms of each comparison run the same mapper code, so what the JIT does to the address text cancels
        assumeHttpRequestIsNotInstrumented();
        assertThat("same connection " + sameConnectionOff + " vs no address " + noAddressOff, Math.abs(sameConnectionOff - noAddressOff), lessThan(8L));
        assertThat("same connection, latch off " + sameConnectionOff + " vs on " + sameConnectionOn, Math.abs(sameConnectionOn - sameConnectionOff), lessThan(8L));
        // per connection, the latch adds exactly the holder, so without it nothing beyond the address text is allocated
        assertThat("new connection, latch on " + newConnectionOn + " vs off " + newConnectionOff, newConnectionOn - newConnectionOff, greaterThanOrEqualTo(16L));
        assertThat("new connection, latch on " + newConnectionOn + " vs off " + newConnectionOff, newConnectionOn - newConnectionOff, lessThan(40L));
    }

    private static FullHttpRequestToMockServerHttpRequest mapper(boolean clientIpInUse) {
        FullHttpRequestToMockServerHttpRequest mapper = new FullHttpRequestToMockServerHttpRequest(configuration(), new MockServerLogger(), false, null, 1080);
        mapper.clientIpInUseForTesting(() -> clientIpInUse);
        return mapper;
    }

    private long perRequest(FullHttpRequestToMockServerHttpRequest mapper, boolean newConnectionEachRequest, boolean withAddress) {
        long least = Long.MAX_VALUE;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long before = allocatedBytes();
            for (int i = 0; i < OPERATIONS; i++) {
                InetSocketAddress remote = !withAddress ? null : newConnectionEachRequest ? connections[i] : oneConnection;
                sink = mapper.mapFullHttpRequestToMockServerRequest(nettyRequest, null, local, remote, Protocol.HTTP_1_1);
            }
            least = Math.min(least, (allocatedBytes() - before) / OPERATIONS);
        }
        return least;
    }

}
