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
import org.mockserver.model.DnsRecord;
import org.mockserver.model.DnsRecordClass;
import org.mockserver.model.DnsRecordType;
import org.mockserver.model.DnsRequestDefinition;
import org.mockserver.model.DnsResponse;
import org.mockserver.model.DnsResponseCode;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;

import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;

@ChannelHandler.Sharable
public class DnsRequestHandler extends SimpleChannelInboundHandler<DatagramDnsQuery> {

    private final MockServerLogger mockServerLogger;
    private final HttpState httpState;

    public DnsRequestHandler(MockServerLogger mockServerLogger, HttpState httpState) {
        super(true);
        this.mockServerLogger = mockServerLogger;
        this.httpState = httpState;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) {
        String logCorrelationId = UUIDService.getUUID();
        DnsQuestion question = query.recordAt(DnsSection.QUESTION);
        if (question == null) {
            sendErrorResponse(ctx, query, DnsResponseCode.FORMERR);
            return;
        }

        String qName = question.name();
        DnsRecordType qType = DnsRecordType.fromIntValue(question.type().intValue());
        DnsRecordClass qClass = DnsRecordClass.fromIntValue(question.dnsClass());

        if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(RECEIVED_REQUEST)
                    .setLogLevel(Level.INFO)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("received DNS query for name:{} type:{} class:{}")
                    .setArguments(qName, qType, qClass)
            );
        }

        DnsRequestDefinition dnsRequestDefinition = DnsRequestDefinition.dnsRequest()
            .withDnsName(qName)
            .withDnsType(qType)
            .withDnsClass(qClass);
        dnsRequestDefinition.withLogCorrelationId(logCorrelationId);

        Expectation matchedExpectation = httpState.firstMatchingExpectation(dnsRequestDefinition);
        if (matchedExpectation != null && matchedExpectation.getDnsResponse() != null) {
            DnsResponse dnsResponse = matchedExpectation.getDnsResponse();
            sendDnsResponse(ctx, query, dnsResponse, logCorrelationId);
        } else {
            sendErrorResponse(ctx, query, DnsResponseCode.NXDOMAIN);
        }
    }

    private void sendDnsResponse(ChannelHandlerContext ctx, DatagramDnsQuery query, DnsResponse dnsResponse, String logCorrelationId) {
        DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
        response.addRecord(DnsSection.QUESTION, query.recordAt(DnsSection.QUESTION));

        DnsResponseCode responseCode = dnsResponse.getResponseCode();
        if (responseCode != null) {
            response.setCode(io.netty.handler.codec.dns.DnsResponseCode.valueOf(responseCode.intValue()));
        } else {
            response.setCode(io.netty.handler.codec.dns.DnsResponseCode.NOERROR);
        }

        if (dnsResponse.getAnswerRecords() != null) {
            for (DnsRecord record : dnsResponse.getAnswerRecords()) {
                DefaultDnsRawRecord rawRecord = encodeRecord(record);
                if (rawRecord != null) {
                    response.addRecord(DnsSection.ANSWER, rawRecord);
                }
            }
        }
        if (dnsResponse.getAuthorityRecords() != null) {
            for (DnsRecord record : dnsResponse.getAuthorityRecords()) {
                DefaultDnsRawRecord rawRecord = encodeRecord(record);
                if (rawRecord != null) {
                    response.addRecord(DnsSection.AUTHORITY, rawRecord);
                }
            }
        }
        if (dnsResponse.getAdditionalRecords() != null) {
            for (DnsRecord record : dnsResponse.getAdditionalRecords()) {
                DefaultDnsRawRecord rawRecord = encodeRecord(record);
                if (rawRecord != null) {
                    response.addRecord(DnsSection.ADDITIONAL, rawRecord);
                }
            }
        }

        if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.INFO)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("returning DNS response with {} answer records")
                    .setArguments(dnsResponse.getAnswerRecords() != null ? dnsResponse.getAnswerRecords().size() : 0)
            );
        }

        ctx.writeAndFlush(response);
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, DatagramDnsQuery query, DnsResponseCode code) {
        DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
        DnsQuestion question = query.recordAt(DnsSection.QUESTION);
        if (question != null) {
            response.addRecord(DnsSection.QUESTION, question);
        }
        response.setCode(io.netty.handler.codec.dns.DnsResponseCode.valueOf(code.intValue()));
        ctx.writeAndFlush(response);
    }

    private DefaultDnsRawRecord encodeRecord(DnsRecord record) {
        if (record == null || record.getType() == null || record.getValue() == null) {
            return null;
        }
        String name = record.getName() != null ? record.getName() : "";
        int ttl = record.getTtl() != null ? record.getTtl() : 300;
        int dnsClass = record.getDnsClass() != null ? record.getDnsClass().intValue() : DnsRecordClass.IN.intValue();

        switch (record.getType()) {
            case A:
            case AAAA: {
                byte[] addr = NetUtil.createByteArrayFromIpAddressString(record.getValue());
                if (addr == null) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.WARN)
                            .setMessageFormat("invalid IP address for DNS {} record: {}")
                            .setArguments(record.getType(), record.getValue())
                    );
                    return null;
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
                byte[] text = record.getValue().getBytes(StandardCharsets.UTF_8);
                if (text.length > 255) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.WARN)
                            .setMessageFormat("DNS TXT record value exceeds 255 bytes (length: {}), truncating")
                            .setArguments(text.length)
                    );
                    byte[] truncated = new byte[255];
                    System.arraycopy(text, 0, truncated, 0, 255);
                    text = truncated;
                }
                byte[] data = new byte[1 + text.length];
                data[0] = (byte) text.length;
                System.arraycopy(text, 0, data, 1, text.length);
                return new DefaultDnsRawRecord(name, io.netty.handler.codec.dns.DnsRecordType.TXT, dnsClass, ttl, Unpooled.wrappedBuffer(data));
            }
            default:
                return null;
        }
    }

    private byte[] encodeDnsName(String name) {
        if (!name.endsWith(".")) {
            name = name + ".";
        }
        String[] labels = name.split("\\.");
        int totalLength = 1;
        for (String label : labels) {
            if (label.isEmpty()) {
                continue;
            }
            totalLength += 1 + label.length();
        }
        byte[] result = new byte[totalLength];
        int pos = 0;
        for (String label : labels) {
            if (label.isEmpty()) {
                continue;
            }
            result[pos++] = (byte) label.length();
            byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
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
