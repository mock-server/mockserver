package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;

import java.net.InetAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.NottableOptionalString.optional;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

public class ClientIpMatcherTest {

    private static final MockServerLogger LOGGER = new MockServerLogger(ClientIpMatcherTest.class);

    @Test
    public void shouldMatchExactIpv4Address() throws Exception {
        assertThat(matches("192.168.1.10", "192.168.1.10"), is(true));
        assertThat(matches("192.168.1.10", "192.168.1.11"), is(false));
        assertThat(matches("192.168.1.10", "10.168.1.10"), is(false));
    }

    @Test
    public void shouldMatchIpv4CidrRangeAtItsBoundaries() throws Exception {
        assertThat(matches("10.0.0.0/8", "10.0.0.0"), is(true));
        assertThat(matches("10.0.0.0/8", "10.255.255.255"), is(true));
        assertThat(matches("10.0.0.0/8", "11.0.0.0"), is(false));
        assertThat(matches("10.0.0.0/8", "9.255.255.255"), is(false));
        // a prefix that does not fall on a byte boundary
        assertThat(matches("172.16.0.0/12", "172.16.0.1"), is(true));
        assertThat(matches("172.16.0.0/12", "172.31.255.255"), is(true));
        assertThat(matches("172.16.0.0/12", "172.32.0.0"), is(false));
        assertThat(matches("172.16.0.0/12", "172.15.255.255"), is(false));
        assertThat(matches("192.168.1.2/31", "192.168.1.3"), is(true));
        assertThat(matches("192.168.1.2/31", "192.168.1.4"), is(false));
        assertThat(matches("192.168.1.2/32", "192.168.1.2"), is(true));
        assertThat(matches("192.168.1.2/32", "192.168.1.3"), is(false));
        assertThat(matches("0.0.0.0/0", "203.0.113.7"), is(true));
    }

    @Test
    public void shouldIgnoreHostBitsInTheRange() throws Exception {
        assertThat(matches("10.1.2.3/8", "10.200.0.1"), is(true));
        assertThat(matches("10.1.2.3/8", "11.1.2.3"), is(false));
    }

    @Test
    public void shouldMatchIpv6AddressesAndRanges() throws Exception {
        assertThat(matches("2001:db8::1", "2001:db8::1"), is(true));
        assertThat(matches("2001:db8::1", "2001:db8::2"), is(false));
        assertThat(matches("2001:db8::/32", "2001:db8:ffff::1"), is(true));
        assertThat(matches("2001:db8::/32", "2001:db9::1"), is(false));
        assertThat(matches("fe80::/10", "febf::1"), is(true));
        assertThat(matches("fe80::/10", "fec0::1"), is(false));
        assertThat(matches("::1/128", "::1"), is(true));
        assertThat(matches("[::1]", "::1"), is(true));
        assertThat(matches("2001:db8::1/127", "2001:db8::0"), is(true));
        assertThat(matches("2001:db8::1/127", "2001:db8::2"), is(false));
    }

    @Test
    public void shouldNotMatchAcrossAddressFamiliesExceptForIpv4MappedAddresses() throws Exception {
        assertThat(matches("127.0.0.1", "::1"), is(false));
        assertThat(matches("::1", "127.0.0.1"), is(false));
        assertThat(matches("2001:db8::/32", "10.0.0.1"), is(false));
        // an IPv4 range matches an IPv4-mapped IPv6 peer, and an IPv4-mapped range matches an IPv4 peer
        assertThat(matches("10.0.0.0/8", "::ffff:10.1.2.3"), is(true));
        assertThat(matches("::ffff:10.0.0.0/104", "10.1.2.3"), is(true));
        assertThat(matches("::ffff:10.0.0.0/104", "11.1.2.3"), is(false));
        assertThat(matches("::ffff:0:0/96", "198.51.100.1"), is(true));
        // a wider IPv6 range that contains the IPv4-mapped space
        assertThat(matches("::/0", "198.51.100.1"), is(true));
        assertThat(matches("::/80", "198.51.100.1"), is(true));
        assertThat(matches("2001::/16", "198.51.100.1"), is(false));
    }

    @Test
    public void shouldNegate() throws Exception {
        assertThat(matcher(not("10.0.0.0/8")).matches(null, received("10.1.1.1")), is(false));
        assertThat(matcher(not("10.0.0.0/8")).matches(null, received("192.168.1.1")), is(true));
        assertThat(matcher(string("!10.0.0.0/8")).matches(null, received("192.168.1.1")), is(true));
    }

    @Test
    public void shouldNeverMatchARequestWithNoClientAddressEvenWhenNegated() {
        assertThat(matcher(string("10.0.0.0/8")).matches(null, request()), is(false));
        assertThat(matcher(not("10.0.0.0/8")).matches(null, request()), is(false));
        assertThat(matcher(not("10.0.0.0/8")).matches(null, request().withRemoteAddress("some-host:8080")), is(false));
    }

    @Test
    public void shouldRecordDifferenceWhenNotMatched() throws Exception {
        MatchDifference context = new MatchDifference(true, request());
        context.currentField(MatchDifference.Field.CLIENT_IP);
        assertThat(matcher(string("10.0.0.0/8")).matches(context, received("192.168.1.1").withRemoteAddress("192.168.1.1:4000")), is(false));
        String differences = String.valueOf(context.getDifferences(MatchDifference.Field.CLIENT_IP));
        assertThat(differences, containsString("client ip match failed expected:"));
        assertThat(differences, containsString("10.0.0.0/8"));
        assertThat(differences, containsString("192.168.1.1:4000"));
    }

    @Test
    public void shouldMatchUsingTheRemoteAddressTextWhenTheTransportGaveNoBytes() {
        assertThat(matcher(string("127.0.0.0/8")).matches(null, request().withRemoteAddress("127.0.0.1:52000")), is(true));
        assertThat(matcher(string("::1")).matches(null, request().withRemoteAddress("[0:0:0:0:0:0:0:1]:52000")), is(true));
        assertThat(matcher(string("10.0.0.1")).matches(null, request().withRemoteAddress("localhost/10.0.0.1:52000")), is(true));
        assertThat(matcher(string("10.0.0.1")).matches(null, request().withRemoteAddress("10.0.0.1")), is(true));
        assertThat(matcher(string("::1")).matches(null, request().withRemoteAddress("0:0:0:0:0:0:0:1:8080")), is(true));
        assertThat(matcher(string("::1")).matches(null, request().withRemoteAddress("::1")), is(true));
        assertThat(matcher(string("10.0.0.1")).matches(null, request().withRemoteAddress("10.0.0.2:52000")), is(false));
    }

    @Test
    public void shouldRememberTheRemoteAddressBytesParsedFromText() {
        HttpRequest request = request().withRemoteAddress("127.0.0.1:52000");
        matcher(string("127.0.0.0/8")).matches(null, request);
        byte[] parsed = request.getRemoteAddressBytes();
        assertThat(parsed.length, is(4));
        assertThat(request.getRemoteAddress(), is("127.0.0.1:52000"));
        matcher(string("127.0.0.0/8")).matches(null, request);
        assertThat(request.getRemoteAddressBytes() == parsed, is(true));
    }

    @Test
    public void shouldParseRemoteAddressTextWithoutResolvingHostNames() {
        assertThat(ClientIpMatcher.parseRemoteAddress("localhost:8080").length, is(0));
        assertThat(ClientIpMatcher.parseRemoteAddress("").length, is(0));
        assertThat(ClientIpMatcher.parseRemoteAddress(null).length, is(0));
        assertThat(ClientIpMatcher.parseRemoteAddress("[::ffff:10.0.0.1]:80").length, is(4));
    }

    @Test
    public void shouldRejectValuesThatAreNotAnIpAddressOrRange() {
        for (String invalid : new String[]{"", " ", "localhost", "example.com", "10.0.0", "10.0.0.256", "10.0.0.0/33", "2001:db8::/129",
            "10.0.0.0/", "10.0.0.0/-1", "10.0.0.0/+8", "10.0.0.0/8/8", "10.0.0.0/a", "/8", "10.0.0.0/0008"}) {
            IllegalArgumentException exception = assertThrows(invalid, IllegalArgumentException.class, () -> ClientIpMatcher.compile(LOGGER, string(invalid)));
            assertThat(exception.getMessage(), containsString("clientIp"));
        }
        assertThrows(IllegalArgumentException.class, () -> ClientIpMatcher.compile(LOGGER, schemaString("{\"type\":\"string\"}")));
        assertThrows(IllegalArgumentException.class, () -> ClientIpMatcher.compile(LOGGER, optional("10.0.0.1")));
        assertThrows(IllegalArgumentException.class, () -> request().withClientIp("not-an-ip"));
        assertThrows(IllegalArgumentException.class, () -> request().withClientIp("10.0.0.0/40"));
    }

    @Test
    public void shouldAcceptSurroundingWhitespaceAndZoneIds() throws Exception {
        assertThat(matches(" 10.0.0.1 ", "10.0.0.1"), is(true));
        assertThat(matches("fe80::1%eth0", "fe80::1"), is(true));
    }

    private static boolean matches(String clientIp, String peer) throws Exception {
        return matcher(string(clientIp)).matches(null, received(peer));
    }

    private static ClientIpMatcher matcher(NottableString clientIp) {
        return ClientIpMatcher.compile(LOGGER, clientIp);
    }

    // a request as the Netty transport delivers it: address bytes from the socket, never parsed from text
    private static HttpRequest received(String peer) throws Exception {
        return request().withReceivedRemoteAddress(new HttpRequest.RemoteAddress(null, InetAddress.getByName(peer)));
    }
}
