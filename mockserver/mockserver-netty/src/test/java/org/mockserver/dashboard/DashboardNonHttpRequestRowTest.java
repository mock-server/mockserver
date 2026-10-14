package org.mockserver.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.dashboard.model.DashboardBodyCap;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.dashboard.serializers.DashboardLogEntryDTOGroupSerializer;
import org.mockserver.dashboard.serializers.DashboardLogEntryDTOSerializer;
import org.mockserver.dashboard.serializers.DescriptionProcessor;
import org.mockserver.dashboard.serializers.DescriptionSerializer;
import org.mockserver.dashboard.serializers.ThrowableSerializer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.model.DnsRecordClass;
import org.mockserver.model.DnsRecordType;
import org.mockserver.model.RequestDefinition;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.DnsRequestDefinition.dnsRequest;

/**
 * DNS queries and binary messages are recorded as received requests, and the dashboard lists them as rows: a DNS
 * query by its name and type, a binary message by its length and first bytes, its bytes cut and counted against the
 * update's size ceiling as an HTTP body is, and masked when redactSecretsInLog is on.
 */
public class DashboardNonHttpRequestRowTest {

    private static final int CAP = DashboardBodyCap.MAX_BODY_CHARACTERS;
    private static final ObjectMapper MAPPER = ObjectMapperFactory.createObjectMapper(
        new DashboardLogEntryDTOSerializer(),
        new DashboardLogEntryDTOGroupSerializer(),
        new DescriptionSerializer(),
        new ThrowableSerializer()
    );

    private static byte[] bytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    private static LogEntry received(RequestDefinition request) {
        return new LogEntry().setType(RECEIVED_REQUEST).setCorrelationId("exchange-" + System.nanoTime())
            .setHttpRequest(request).setMessageFormat("received request");
    }

    private static JsonNode render(Configuration configuration, long maxCharacters, LogEntry... newestFirst) throws Exception {
        List<DashboardLogEntryDTO> reverse = new ArrayList<>();
        for (LogEntry logEntry : newestFirst) {
            reverse.add(new DashboardLogEntryDTO(logEntry, configuration));
        }
        List<Object> logMessages = new LinkedList<>();
        List<Map<String, Object>> recordedRequests = new LinkedList<>();
        List<Map<String, Object>> proxiedRequests = new LinkedList<>();
        DashboardWebSocketHandler.populateLogSections(
            reverse.stream(), true, 100, new DashboardWebSocketHandler.FrameBudget(maxCharacters),
            logMessages, recordedRequests, proxiedRequests,
            new DescriptionProcessor(configuration), new DescriptionProcessor(configuration), new DescriptionProcessor(configuration));
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("logMessages", logMessages);
        sections.put("recordedRequests", recordedRequests);
        return MAPPER.readTree(MAPPER.writeValueAsString(sections));
    }

    private static JsonNode render(LogEntry... newestFirst) throws Exception {
        return render(configuration(), DashboardWebSocketHandler.MAX_UPDATE_CHARACTERS, newestFirst);
    }

    @Test
    public void shouldListADnsQueryByItsNameAndType() throws Exception {
        JsonNode rows = render(received(dnsRequest("api.example.com", DnsRecordType.A).withDnsClass(DnsRecordClass.IN))).get("recordedRequests");

        assertThat(rows.size(), is(1));
        JsonNode request = rows.get(0).get("value").get("httpRequest");
        assertThat(request.get("dnsName").asText(), is("api.example.com"));
        assertThat(request.get("dnsType").asText(), is("A"));
        assertThat(request.get("dnsClass").asText(), is("IN"));
        assertThat(rows.get(0).get("description").get("first").asText(), is("DNS"));
        assertThat(rows.get(0).get("description").get("second").asText(), is("api.example.com A"));
    }

    @Test
    public void shouldListABinaryMessageByItsLengthAndFirstBytes() throws Exception {
        byte[] message = bytes(20);

        JsonNode rows = render(received(binaryRequest(message))).get("recordedRequests");

        assertThat(rows.size(), is(1));
        assertThat(rows.get(0).get("value").get("httpRequest").get("binaryData").asText(), is(Base64.getEncoder().encodeToString(message)));
        assertThat(rows.get(0).has("truncatedBodies"), is(false));
        assertThat(rows.get(0).get("description").get("first").asText(), is("BINARY"));
        assertThat(rows.get(0).get("description").get("second").asText(), is("20 bytes 000102030405060708090a0b0c0d0e0f..."));
    }

    @Test
    public void shouldCutALargeBinaryMessageAndSayWhereToLoadItWhole() throws Exception {
        byte[] message = bytes(200_000);
        LogEntry entry = received(binaryRequest(message));

        JsonNode row = render(entry).get("recordedRequests").get(0);

        String shown = row.get("value").get("httpRequest").get("binaryData").asText();
        assertThat(shown.length(), is(CAP));
        assertThat(Base64.getDecoder().decode(shown), is(Arrays.copyOf(message, CAP / 4 * 3)));
        JsonNode marker = row.get("truncatedBodies").get("httpRequest");
        assertThat(marker.get("logEntryId").asText(), is(entry.id()));
        assertThat(marker.get("part").asText(), is("request"));
        assertThat(marker.get("originalLength").asLong(), is((long) Base64.getEncoder().encodeToString(message).length()));
        assertThat(marker.get("loadable").asBoolean(), is(true));
        assertThat("the length received, not the length shown", row.get("description").get("second").asText(), is("200000 bytes 000102030405060708090a0b0c0d0e0f..."));
    }

    @Test
    public void shouldCutALargeBinaryMessageQuotedInALogMessage() throws Exception {
        byte[] message = bytes(200_000);
        LogEntry entry = new LogEntry().setType(EXPECTATION_MATCHED).setHttpRequest(binaryRequest(message))
            .setMessageFormat("request:{}matched").setArguments(binaryRequest(message));

        JsonNode parts = render(entry).get("logMessages").get(0).get("value").get("messageParts");

        JsonNode argument = parts.get(1);
        assertThat(argument.get("value").get("binaryData").asText().length(), is(CAP));
        assertThat(argument.get("truncatedBody"), notNullValue());
    }

    @Test
    public void shouldChargeBinaryRowsToTheUpdateSizeCeiling() throws Exception {
        // one size the dashboard cuts, and one it sends whole
        for (int length : new int[]{200_000, 40_000}) {
            List<LogEntry> entries = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                entries.add(received(binaryRequest(bytes(length))));
            }

            JsonNode rows = render(configuration(), 5L * (CAP + DashboardBodyCap.MESSAGE_OVERHEAD_CHARACTERS), entries.toArray(new LogEntry[0])).get("recordedRequests");

            assertThat("rows of " + length + " bytes", rows.size(), greaterThan(0));
            assertThat("rows of " + length + " bytes", rows.size(), lessThan(8));
        }
    }

    @Test
    public void shouldMaskABinaryMessageAndLeaveOutItsBytesWhenRedactionIsOn() throws Exception {
        byte[] message = bytes(20);

        JsonNode row = render(configuration().redactSecretsInLog(true), DashboardWebSocketHandler.MAX_UPDATE_CHARACTERS, received(binaryRequest(message))).get("recordedRequests").get(0);

        String shown = row.get("value").get("httpRequest").get("binaryData").asText();
        assertThat(shown, not(is(Base64.getEncoder().encodeToString(message))));
        assertThat(row.get("description").get("first").asText(), is("BINARY"));
        assertThat(row.get("description").get("second").asText(), is("bytes redacted"));
    }
}
