package org.mockserver.netty.dns;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.dns.DnsIntentRegistry;
import org.mockserver.model.DnsRecord;
import org.mockserver.model.DnsRequestDefinition;
import org.mockserver.model.DnsResponse;
import org.mockserver.scheduler.Scheduler;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;

public class DnsRequestHandlerTest {

    private Scheduler scheduler;
    private HttpState httpState;
    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        DnsIntentRegistry.getInstance().clear();
        Configuration configuration = configuration();
        MockServerLogger logger = new MockServerLogger(configuration, DnsRequestHandlerTest.class);
        scheduler = new Scheduler(configuration, logger);
        httpState = new HttpState(configuration, logger, scheduler);
        DnsRequestHandler handler = new DnsRequestHandler(logger, httpState);
        channel = new EmbeddedChannel(new DatagramDnsResponseEncoder(), handler);
    }

    @After
    public void tearDown() {
        DnsIntentRegistry.getInstance().clear();
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
        httpState.stop();
        scheduler.shutdown();
    }

    @Test
    public void shouldRecordARecordInDnsIntentRegistry() throws Exception {
        // given — an expectation that returns an A record
        httpState.add(new Expectation(
            DnsRequestDefinition.dnsRequest()
                .withDnsName("api.example.com.")
                .withDnsType(org.mockserver.model.DnsRecordType.A)
        ).thenRespondWithDns(
            new DnsResponse()
                .withAnswerRecords(DnsRecord.aRecord("api.example.com.", "10.0.0.1"))
        ));

        DatagramDnsQuery query = new DatagramDnsQuery(
            new InetSocketAddress("127.0.0.1", 12345),
            new InetSocketAddress("127.0.0.1", 53),
            1
        );
        query.addRecord(DnsSection.QUESTION,
            new DefaultDnsQuestion("api.example.com.", DnsRecordType.A));

        // when
        channel.writeInbound(query);

        // then — the intent registry should contain the mapping
        InetAddress expectedIp = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        assertThat(DnsIntentRegistry.getInstance().recover(expectedIp), is("api.example.com"));
        assertThat(DnsIntentRegistry.getInstance().size(), is(1));
    }

    @Test
    public void shouldRecordAAAARecordInDnsIntentRegistry() throws Exception {
        // given — an expectation that returns an AAAA record
        httpState.add(new Expectation(
            DnsRequestDefinition.dnsRequest()
                .withDnsName("ipv6.example.com.")
                .withDnsType(org.mockserver.model.DnsRecordType.AAAA)
        ).thenRespondWithDns(
            new DnsResponse()
                .withAnswerRecords(DnsRecord.aaaaRecord("ipv6.example.com.", "2001:db8::1"))
        ));

        DatagramDnsQuery query = new DatagramDnsQuery(
            new InetSocketAddress("127.0.0.1", 12345),
            new InetSocketAddress("127.0.0.1", 53),
            2
        );
        query.addRecord(DnsSection.QUESTION,
            new DefaultDnsQuestion("ipv6.example.com.", DnsRecordType.AAAA));

        // when
        channel.writeInbound(query);

        // then
        InetAddress expectedIp = InetAddress.getByAddress(new byte[]{
            0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 1
        });
        assertThat(DnsIntentRegistry.getInstance().recover(expectedIp), is("ipv6.example.com"));
    }

    @Test
    public void shouldNotRecordForNxdomain() {
        // given — no matching expectation (NXDOMAIN)
        DatagramDnsQuery query = new DatagramDnsQuery(
            new InetSocketAddress("127.0.0.1", 12345),
            new InetSocketAddress("127.0.0.1", 53),
            3
        );
        query.addRecord(DnsSection.QUESTION,
            new DefaultDnsQuestion("unknown.example.com.", DnsRecordType.A));

        // when
        channel.writeInbound(query);

        // then — no entries in the registry
        assertThat(DnsIntentRegistry.getInstance().size(), is(0));
    }

    @Test
    public void shouldNotRecordNonAddressRecords() {
        // given — an expectation returning a CNAME record (not A/AAAA)
        httpState.add(new Expectation(
            DnsRequestDefinition.dnsRequest()
                .withDnsName("alias.example.com.")
                .withDnsType(org.mockserver.model.DnsRecordType.CNAME)
        ).thenRespondWithDns(
            new DnsResponse()
                .withAnswerRecords(DnsRecord.cnameRecord("alias.example.com.", "real.example.com."))
        ));

        DatagramDnsQuery query = new DatagramDnsQuery(
            new InetSocketAddress("127.0.0.1", 12345),
            new InetSocketAddress("127.0.0.1", 53),
            4
        );
        query.addRecord(DnsSection.QUESTION,
            new DefaultDnsQuestion("alias.example.com.", DnsRecordType.CNAME));

        // when
        channel.writeInbound(query);

        // then — CNAME records should not be recorded
        assertThat(DnsIntentRegistry.getInstance().size(), is(0));
    }

    @Test
    public void shouldRecordMultipleARecords() throws Exception {
        // given — an expectation with multiple A records
        httpState.add(new Expectation(
            DnsRequestDefinition.dnsRequest()
                .withDnsName("multi.example.com.")
                .withDnsType(org.mockserver.model.DnsRecordType.A)
        ).thenRespondWithDns(
            new DnsResponse()
                .withAnswerRecords(
                    DnsRecord.aRecord("multi.example.com.", "10.0.0.1"),
                    DnsRecord.aRecord("multi.example.com.", "10.0.0.2")
                )
        ));

        DatagramDnsQuery query = new DatagramDnsQuery(
            new InetSocketAddress("127.0.0.1", 12345),
            new InetSocketAddress("127.0.0.1", 53),
            5
        );
        query.addRecord(DnsSection.QUESTION,
            new DefaultDnsQuestion("multi.example.com.", DnsRecordType.A));

        // when
        channel.writeInbound(query);

        // then — both IPs mapped to the same hostname
        InetAddress ip1 = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        InetAddress ip2 = InetAddress.getByAddress(new byte[]{10, 0, 0, 2});
        assertThat(DnsIntentRegistry.getInstance().recover(ip1), is("multi.example.com"));
        assertThat(DnsIntentRegistry.getInstance().recover(ip2), is("multi.example.com"));
        assertThat(DnsIntentRegistry.getInstance().size(), is(2));
    }

    @Test
    public void logsTheQueryWhenMatchingItFails() {
        MockServerLogger logger = mock(MockServerLogger.class);
        HttpState failingState = mock(HttpState.class);
        when(failingState.firstMatchingExpectation(any(DnsRequestDefinition.class))).thenThrow(new IllegalStateException("matcher failed"));
        EmbeddedChannel failing = new EmbeddedChannel(new DatagramDnsResponseEncoder(), new DnsRequestHandler(logger, failingState));
        DatagramDnsQuery query = new DatagramDnsQuery(new InetSocketAddress("127.0.0.1", 12345), new InetSocketAddress("127.0.0.1", 53), 1);
        query.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion("api.example.com.", DnsRecordType.A));
        try {
            failing.writeInbound(query);

            ArgumentCaptor<LogEntry> logged = ArgumentCaptor.forClass(LogEntry.class);
            verify(logger, atLeastOnce()).logEvent(logged.capture());
            assertThat("matching failed", logged.getAllValues().stream().anyMatch(entry -> entry.getThrowable() instanceof IllegalStateException), is(true));
            assertThat(logged.getAllValues().stream().anyMatch(entry -> entry.getType() == RECEIVED_REQUEST
                && entry.getHttpRequest() instanceof DnsRequestDefinition
                && "api.example.com.".equals(((DnsRequestDefinition) entry.getHttpRequest()).getDnsName())), is(true));
        } finally {
            failing.finishAndReleaseAll();
        }
    }
}
