package org.mockserver.netty.dns;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.NetUtil;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.dns.DnsIntentRegistry;
import org.mockserver.model.DnsRecord;
import org.mockserver.model.DnsRecordClass;
import org.mockserver.model.DnsRecordType;
import org.mockserver.model.DnsRequestDefinition;
import org.mockserver.model.DnsResponse;
import org.mockserver.model.DnsResponseCode;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;

@ChannelHandler.Sharable
public class DnsRequestHandler extends SimpleChannelInboundHandler<DatagramDnsQuery> {

    /**
     * RFC 1035 §2.3.4 — a single label is at most 63 octets. The length octet's two high bits are
     * reserved as the compression-pointer marker (RFC 1035 §4.1.4), so any length ≥ 0xC0 would be
     * reinterpreted by a resolver as a pointer and misparse the remainder of the message.
     */
    private static final int MAX_LABEL_LENGTH = 63;
    /** RFC 1035 §2.3.4 — a complete domain name is at most 255 octets in wire form. */
    private static final int MAX_NAME_LENGTH = 255;
    /** RFC 1035 §3.3 — a {@code <character-string>} carries a single length octet. */
    private static final int MAX_CHARACTER_STRING_LENGTH = 255;
    /** RFC 1035 §4.1.3 — RDLENGTH is a 16-bit field. */
    private static final int MAX_RDATA_LENGTH = 0xFFFF;
    /** Fixed DNS message header size in octets (RFC 1035 §4.1.1). */
    private static final int DNS_HEADER_LENGTH = 12;
    /** RFC 1035 §4.2.1 — maximum UDP payload absent EDNS(0). */
    private static final int DEFAULT_MAX_UDP_PAYLOAD_SIZE = 512;
    /** Per-record fixed overhead after the owner name: TYPE(2) + CLASS(2) + TTL(4) + RDLENGTH(2). */
    private static final int RECORD_FIXED_OVERHEAD = 10;
    /** Per-question fixed overhead after the name: QTYPE(2) + QCLASS(2). */
    private static final int QUESTION_FIXED_OVERHEAD = 4;

    private final MockServerLogger mockServerLogger;
    private final HttpState httpState;

    public DnsRequestHandler(MockServerLogger mockServerLogger, HttpState httpState) {
        super(true);
        this.mockServerLogger = mockServerLogger;
        this.httpState = httpState;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) {
        String logCorrelationId = UUIDService.getNonSecureUUID();
        DnsQuestion question = query.recordAt(DnsSection.QUESTION);
        if (question == null) {
            sendErrorResponse(ctx, query, DnsResponseCode.FORMERR);
            return;
        }

        String qName = question.name();
        DnsRecordType qType = DnsRecordType.fromIntValue(question.type().intValue());
        DnsRecordClass qClass = DnsRecordClass.fromIntValue(question.dnsClass());

        DnsRequestDefinition dnsRequestDefinition = DnsRequestDefinition.dnsRequest()
            .withDnsName(qName)
            .withDnsType(qType)
            .withDnsClass(qClass);
        dnsRequestDefinition.withLogCorrelationId(logCorrelationId);

        // logged at every level with its definition, as an HTTP request is, so verify and retrieve can find it;
        // logged once matched, so it names the expectation that answered it for verification by expectation id
        LogEntry receivedRequest = new LogEntry()
            .setType(RECEIVED_REQUEST)
            .setLogLevel(Level.INFO)
            .setCorrelationId(logCorrelationId)
            .setHttpRequest(dnsRequestDefinition)
            .setMessageFormat("received DNS query for name:{} type:{} class:{}")
            .setArguments(qName, qType, qClass);

        Expectation matchedExpectation = null;
        try {
            matchedExpectation = httpState.firstMatchingExpectation(dnsRequestDefinition);
        } finally {
            boolean answered = matchedExpectation != null && matchedExpectation.getDnsResponse() != null;
            mockServerLogger.logEvent(receivedRequest.setExpectationId(answered ? matchedExpectation.getId() : null));
        }
        if (matchedExpectation != null && matchedExpectation.getDnsResponse() != null) {
            DnsResponse dnsResponse = matchedExpectation.getDnsResponse();
            sendDnsResponse(ctx, query, dnsResponse, logCorrelationId);
        } else {
            sendErrorResponse(ctx, query, DnsResponseCode.NXDOMAIN);
        }
    }

    private void sendDnsResponse(ChannelHandlerContext ctx, DatagramDnsQuery query, DnsResponse dnsResponse, String logCorrelationId) {
        DnsQuestion question = query.recordAt(DnsSection.QUESTION);
        String qName = question != null ? question.name() : "";

        // Encode every configured record up-front. A record that cannot be represented on the wire
        // (over-long label, wrong-width address, oversized RDATA) is a configuration error: emit
        // SERVFAIL rather than a NOERROR response carrying corrupt bytes.
        // The output lists are allocated by the caller so that a failure part-way through still
        // leaves every already-encoded buffer reachable for release.
        List<DefaultDnsRawRecord> answers = new ArrayList<>();
        List<DefaultDnsRawRecord> authorities = new ArrayList<>();
        List<DefaultDnsRawRecord> additionals = new ArrayList<>();
        try {
            encodeRecords(dnsResponse.getAnswerRecords(), qName, answers);
            encodeRecords(dnsResponse.getAuthorityRecords(), qName, authorities);
            encodeRecords(dnsResponse.getAdditionalRecords(), qName, additionals);
        } catch (IllegalArgumentException e) {
            answers.forEach(DefaultDnsRawRecord::release);
            authorities.forEach(DefaultDnsRawRecord::release);
            additionals.forEach(DefaultDnsRawRecord::release);
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("cannot encode configured DNS response, returning SERVFAIL -> {}")
                    .setArguments(e.getMessage())
            );
            sendErrorResponse(ctx, query, DnsResponseCode.SERVFAIL);
            return;
        }

        DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
        if (question != null) {
            response.addRecord(DnsSection.QUESTION, question);
        }
        setResponseFlags(response, query);

        DnsResponseCode responseCode = dnsResponse.getResponseCode();
        if (responseCode != null) {
            response.setCode(io.netty.handler.codec.dns.DnsResponseCode.valueOf(responseCode.intValue()));
        } else {
            response.setCode(io.netty.handler.codec.dns.DnsResponseCode.NOERROR);
        }

        // RFC 1035 §4.1.1 / §4.2.1 — a UDP response that does not fit the client's payload limit
        // must be sent truncated with TC set so the resolver retries. Records are added only while
        // the whole message still fits; if any record has to be dropped, TC is set.
        int budget = maxUdpPayloadSize(query);
        int used = DNS_HEADER_LENGTH + (question != null ? encodedNameLength(question.name()) + QUESTION_FIXED_OVERHEAD : 0);
        boolean truncated = false;

        for (DnsSection section : new DnsSection[]{DnsSection.ANSWER, DnsSection.AUTHORITY, DnsSection.ADDITIONAL}) {
            List<DefaultDnsRawRecord> records = section == DnsSection.ANSWER ? answers
                : section == DnsSection.AUTHORITY ? authorities : additionals;
            for (DefaultDnsRawRecord rawRecord : records) {
                int recordLength = encodedNameLength(rawRecord.name()) + RECORD_FIXED_OVERHEAD + rawRecord.content().readableBytes();
                if (used + recordLength > budget) {
                    truncated = true;
                    rawRecord.release();
                    continue;
                }
                used += recordLength;
                response.addRecord(section, rawRecord);
            }
        }
        response.setTruncated(truncated);

        // Record A/AAAA answer IPs in the DNS intent registry so the transparent-proxy
        // resolver can recover the intended hostname for by-IP connections.
        recordDnsIntentMappings(query, dnsResponse);

        if (truncated && mockServerLogger.isEnabledForInstance(Level.WARN)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.WARN)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("DNS response exceeds the {} byte UDP payload limit, returning a truncated response with the TC bit set")
                    .setArguments(budget)
            );
        }

        if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.INFO)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("returning DNS response with {} answer records")
                    .setArguments(response.count(DnsSection.ANSWER))
            );
        }

        ctx.writeAndFlush(response);
    }

    /**
     * Echoes RD from the query and advertises RA/AA. {@code systemd-resolved} and other stub
     * resolvers treat RA=0 as "this server offers no recursion" and move on to the next configured
     * server, so a mock that never sets RA is skipped entirely.
     */
    private static void setResponseFlags(DatagramDnsResponse response, DatagramDnsQuery query) {
        response.setRecursionDesired(query.isRecursionDesired());
        response.setRecursionAvailable(true);
        response.setAuthoritativeAnswer(true);
    }

    /**
     * Honours an EDNS(0) OPT record's advertised payload size (RFC 6891 §6.1.2 — carried in the
     * OPT record's CLASS field), falling back to the bare-DNS 512-octet limit. MockServer never
     * emits more than the client said it can accept, so not echoing an OPT record is safe.
     */
    private static int maxUdpPayloadSize(DatagramDnsQuery query) {
        for (int i = 0; i < query.count(DnsSection.ADDITIONAL); i++) {
            io.netty.handler.codec.dns.DnsRecord record = query.recordAt(DnsSection.ADDITIONAL, i);
            if (record != null && record.type() == io.netty.handler.codec.dns.DnsRecordType.OPT) {
                return Math.max(DEFAULT_MAX_UDP_PAYLOAD_SIZE, Math.min(record.dnsClass(), MAX_RDATA_LENGTH));
            }
        }
        return DEFAULT_MAX_UDP_PAYLOAD_SIZE;
    }

    private void encodeRecords(List<DnsRecord> records, String qName, List<DefaultDnsRawRecord> encoded) {
        if (records == null) {
            return;
        }
        for (DnsRecord record : records) {
            DefaultDnsRawRecord rawRecord = encodeRecord(record, qName);
            if (rawRecord != null) {
                encoded.add(rawRecord);
            }
        }
    }

    /**
     * Measures the wire length of a name <em>without</em> validating it. Validation belongs to
     * {@link #encodeDnsName} and happens while encoding each record, inside the try/catch that
     * turns a failure into SERVFAIL. This is called from the response-size accounting, which runs
     * outside that catch — so it must never throw, or the client would receive no response at all.
     */
    private static int encodedNameLength(String name) {
        if (name == null || name.isEmpty()) {
            return 1;
        }
        String terminated = name.endsWith(".") ? name : name + ".";
        int length = 1;
        for (String label : terminated.split("\\.")) {
            if (!label.isEmpty()) {
                length += 1 + label.getBytes(StandardCharsets.UTF_8).length;
            }
        }
        return length;
    }

    private void recordDnsIntentMappings(DatagramDnsQuery query, DnsResponse dnsResponse) {
        try {
            if (dnsResponse.getAnswerRecords() == null) {
                return;
            }
            DnsQuestion question = query.recordAt(DnsSection.QUESTION);
            if (question == null) {
                return;
            }
            String qName = question.name();
            for (DnsRecord record : dnsResponse.getAnswerRecords()) {
                if (record == null || record.getValue() == null) {
                    continue;
                }
                if (record.getType() == DnsRecordType.A || record.getType() == DnsRecordType.AAAA) {
                    byte[] addrBytes = NetUtil.createByteArrayFromIpAddressString(record.getValue());
                    if (addrBytes != null) {
                        InetAddress inetAddress = InetAddress.getByAddress(addrBytes);
                        DnsIntentRegistry.getInstance().record(inetAddress, qName);
                    }
                }
            }
        } catch (Exception e) {
            if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.DEBUG)
                        .setMessageFormat("failed to record DNS intent mapping: {}")
                        .setArguments(e.getMessage())
                );
            }
        }
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, DatagramDnsQuery query, DnsResponseCode code) {
        DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
        DnsQuestion question = query.recordAt(DnsSection.QUESTION);
        if (question != null) {
            response.addRecord(DnsSection.QUESTION, question);
        }
        setResponseFlags(response, query);
        response.setCode(io.netty.handler.codec.dns.DnsResponseCode.valueOf(code.intValue()));
        ctx.writeAndFlush(response);
    }

    private DefaultDnsRawRecord encodeRecord(DnsRecord record, String qName) {
        if (record == null || record.getType() == null || record.getValue() == null) {
            return null;
        }
        // An owner name of "" encodes as the root zone, which yields a NOERROR/ANCOUNT=1 response
        // carrying no usable answer. Default to the queried name instead.
        String name = record.getName() != null && !record.getName().isEmpty() ? record.getName() : qName;
        // Netty's DefaultDnsRawRecord constructor below validates individual LABEL lengths (it
        // throws "The label in the input is too long"), but it does NOT validate the TOTAL name
        // length — a name of legal 63-octet labels totalling more than 255 octets is accepted.
        // Validate here, inside the caller's try, so that half becomes a SERVFAIL like any other
        // unencodable record instead of throwing later from the response-size accounting, which
        // runs outside the catch and would leave the client with no response at all.
        encodeDnsName(name);
        int ttl = record.getTtl() != null ? record.getTtl() : 300;
        int dnsClass = record.getDnsClass() != null ? record.getDnsClass().intValue() : DnsRecordClass.IN.intValue();

        switch (record.getType()) {
            case A:
            case AAAA: {
                byte[] addr = NetUtil.createByteArrayFromIpAddressString(record.getValue());
                if (addr == null) {
                    throw new IllegalArgumentException("invalid IP address for DNS " + record.getType() + " record: " + record.getValue());
                }
                // RFC 1035 §3.4.1 / RFC 3596 §2.2 — A RDATA is exactly 4 octets, AAAA exactly 16.
                // NetUtil returns whichever width the literal happens to be, so an A record
                // configured with an IPv6 value would otherwise emit TYPE=A with RDLENGTH=16.
                int expectedLength = record.getType() == DnsRecordType.A ? 4 : 16;
                if (addr.length != expectedLength) {
                    throw new IllegalArgumentException("DNS " + record.getType() + " record requires a "
                        + (expectedLength == 4 ? "IPv4" : "IPv6") + " address but was configured with: " + record.getValue());
                }
                io.netty.handler.codec.dns.DnsRecordType nettyType = record.getType() == DnsRecordType.A
                    ? io.netty.handler.codec.dns.DnsRecordType.A
                    : io.netty.handler.codec.dns.DnsRecordType.AAAA;
                return new DefaultDnsRawRecord(name, nettyType, dnsClass, ttl, Unpooled.wrappedBuffer(addr));
            }
            case CNAME:
                return new DefaultDnsRawRecord(name, io.netty.handler.codec.dns.DnsRecordType.CNAME, dnsClass, ttl, Unpooled.wrappedBuffer(encodeDnsName(record.getValue())));
            case PTR:
                return new DefaultDnsRawRecord(name, io.netty.handler.codec.dns.DnsRecordType.PTR, dnsClass, ttl, Unpooled.wrappedBuffer(encodeDnsName(record.getValue())));
            case MX: {
                int priority = record.getPriority() != null ? record.getPriority() : 10;
                byte[] dnsName = encodeDnsName(record.getValue());
                byte[] data = new byte[2 + dnsName.length];
                data[0] = (byte) ((priority >> 8) & 0xFF);
                data[1] = (byte) (priority & 0xFF);
                System.arraycopy(dnsName, 0, data, 2, dnsName.length);
                return new DefaultDnsRawRecord(name, io.netty.handler.codec.dns.DnsRecordType.MX, dnsClass, ttl, Unpooled.wrappedBuffer(data));
            }
            case SRV: {
                int priority = record.getPriority() != null ? record.getPriority() : 0;
                int weight = record.getWeight() != null ? record.getWeight() : 0;
                int port = record.getPort() != null ? record.getPort() : 0;
                byte[] target = encodeDnsName(record.getValue());
                byte[] data = new byte[6 + target.length];
                data[0] = (byte) ((priority >> 8) & 0xFF);
                data[1] = (byte) (priority & 0xFF);
                data[2] = (byte) ((weight >> 8) & 0xFF);
                data[3] = (byte) (weight & 0xFF);
                data[4] = (byte) ((port >> 8) & 0xFF);
                data[5] = (byte) (port & 0xFF);
                System.arraycopy(target, 0, data, 6, target.length);
                return new DefaultDnsRawRecord(name, io.netty.handler.codec.dns.DnsRecordType.SRV, dnsClass, ttl, Unpooled.wrappedBuffer(data));
            }
            case TXT: {
                return new DefaultDnsRawRecord(name, io.netty.handler.codec.dns.DnsRecordType.TXT, dnsClass, ttl,
                    Unpooled.wrappedBuffer(encodeCharacterStrings(record.getValue())));
            }
            default:
                return null;
        }
    }

    /**
     * Encodes a TXT record value as a sequence of {@code <character-string>}s (RFC 1035 §3.3.14).
     * Values longer than 255 octets are <em>split</em> across multiple character-strings — which
     * resolvers concatenate — rather than truncated. Truncation silently corrupted the two
     * commonest real TXT payloads, DKIM public keys and long SPF records.
     */
    private static byte[] encodeCharacterStrings(String value) {
        byte[] text = value.getBytes(StandardCharsets.UTF_8);
        int chunkCount = Math.max(1, (text.length + MAX_CHARACTER_STRING_LENGTH - 1) / MAX_CHARACTER_STRING_LENGTH);
        int totalLength = text.length + chunkCount;
        if (totalLength > MAX_RDATA_LENGTH) {
            throw new IllegalArgumentException("DNS TXT record value encodes to " + totalLength
                + " octets which exceeds the maximum RDATA length of " + MAX_RDATA_LENGTH);
        }
        byte[] result = new byte[totalLength];
        int src = 0;
        int dst = 0;
        while (src < text.length) {
            int chunkLength = Math.min(MAX_CHARACTER_STRING_LENGTH, text.length - src);
            result[dst++] = (byte) chunkLength;
            System.arraycopy(text, src, result, dst, chunkLength);
            dst += chunkLength;
            src += chunkLength;
        }
        // An empty value is still one (zero-length) character-string; result is already {0}.
        return result;
    }

    /**
     * Encodes a domain name in uncompressed wire form, validating the RFC 1035 §2.3.4 length
     * limits. Without the per-label check a label of 192 octets or more writes a length octet of
     * {@code 0xC0} or greater — whose two high bits mark a compression pointer (RFC 1035 §4.1.4)
     * — causing the resolver to reinterpret the following 14 bits as a message offset and
     * misparse the entire packet.
     * <p>
     * Both checks are needed, for different reasons. Netty validates <em>label</em> length on a
     * record's own owner name but never validates <em>total</em> name length, so the 255-octet
     * check is the only one covering that case. And names embedded in RDATA (CNAME, PTR, MX and
     * SRV targets) bypass Netty's validation altogether, so both checks are load-bearing there.
     */
    private static byte[] encodeDnsName(String name) {
        if (name == null) {
            name = "";
        }
        if (!name.endsWith(".")) {
            name = name + ".";
        }
        String[] labels = name.split("\\.");
        int totalLength = 1;
        byte[][] encodedLabels = new byte[labels.length][];
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].isEmpty()) {
                continue;
            }
            byte[] labelBytes = labels[i].getBytes(StandardCharsets.UTF_8);
            if (labelBytes.length > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("DNS label \"" + labels[i] + "\" is " + labelBytes.length
                    + " octets which exceeds the maximum label length of " + MAX_LABEL_LENGTH);
            }
            encodedLabels[i] = labelBytes;
            totalLength += 1 + labelBytes.length;
        }
        if (totalLength > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("DNS name \"" + name + "\" encodes to " + totalLength
                + " octets which exceeds the maximum name length of " + MAX_NAME_LENGTH);
        }
        byte[] result = new byte[totalLength];
        int pos = 0;
        for (byte[] labelBytes : encodedLabels) {
            if (labelBytes == null) {
                continue;
            }
            result[pos++] = (byte) labelBytes.length;
            System.arraycopy(labelBytes, 0, result, pos, labelBytes.length);
            pos += labelBytes.length;
        }
        result[pos] = 0;
        return result;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setMessageFormat("exception caught by DNS handler -> {}")
                .setArguments(cause.getMessage())
                .setThrowable(cause)
        );
    }
}
