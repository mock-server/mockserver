package org.mockserver.mock;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpObject;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.MockServerHttpResponseToFullHttpResponse;
import org.mockserver.mappers.MockServerHttpResponseToHttpServletResponseEncoder;
import org.mockserver.model.Format;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.model.RetrieveType;
import org.mockserver.model.StringBody;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.version.Version;
import org.slf4j.event.Level;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

/**
 * Pins the bytes and content type of every retrieve type and format, as the Netty and the servlet
 * frontends write them, to digests recorded from the implementation that built each response as one
 * String. The fixture mixes multi-byte, supplementary and unpaired surrogate characters with bodies
 * long enough to cross every internal buffer boundary. The fixed log timestamp, formatted in the
 * JVM's zone, and the MockServer version are replaced by placeholders before hashing, as are the
 * random ids in OpenAPI documents, the time each Bruno zip entry was written and the Go module
 * path's major suffix, which each major release rewrites.
 * <p>
 * To re-record (only when an output change is intended), run with
 * {@code -Dmockserver.recordRetrieveGolden=<path of retrieve-golden.txt in src/test/resources>}.
 */
public class HttpStateRetrieveGoldenTest {

    private static final String GOLDEN = "/org/mockserver/mock/retrieve-golden.txt";
    private static final long EPOCH = 1_700_000_000_123L;
    private static final String MIXED = "plain ascii, Latin-1 é ñ ß, CJK 中文字, emoji 😀🚀, quote \" backslash \\ tab \t newline \n";

    private HttpState httpState;
    private ScheduledExecutorService schedulerExecutor;

    @Before
    public void setUp() {
        // WARN keeps the retrieves' own INFO entries, which carry the time and a random id, out of the log
        Configuration configuration = configuration().logLevel(Level.WARN);
        Scheduler scheduler = mock(Scheduler.class);
        schedulerExecutor = Executors.newScheduledThreadPool(2);
        when(scheduler.getExecutorService()).thenReturn(schedulerExecutor);
        httpState = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler);
    }

    @After
    public void tearDown() {
        if (httpState != null) {
            httpState.stop();
        }
        schedulerExecutor.shutdownNow();
    }

    static String longText(int characters) {
        StringBuilder text = new StringBuilder(characters + MIXED.length());
        while (text.length() < characters) {
            text.append(MIXED);
        }
        return text.toString();
    }

    static void populate(HttpState httpState) {
        String unpaired = "unpaired high \uD83D then low \uDE00 at end \uD83D";
        HttpRequest jsonRequest = request("/orders/中文")
            .withMethod("POST")
            .withHeader(header("x-unpaired", unpaired))
            .withQueryStringParameter("q", MIXED)
            .withBody(json("{\"name\":\"" + "é😀" + "\",\"text\":\"" + longText(30_000).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + "\"}", MediaType.JSON_UTF_8));
        HttpRequest textRequest = request("/text")
            .withMethod("PUT")
            .withBody(new StringBody(longText(70_000), MediaType.PLAIN_TEXT_UTF_8));
        HttpRequest binaryRequest = request("/binary")
            .withMethod("POST")
            .withBody(binary(allByteValues(5_000), MediaType.APPLICATION_OCTET_STREAM));
        int i = 0;
        for (HttpRequest received : new HttpRequest[]{jsonRequest, textRequest, binaryRequest}) {
            httpState.log(new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setEpochTime(EPOCH)
                .setCorrelationId("received-" + i++)
                .setHttpRequest(received)
                .setMessageFormat("received request:{}")
                .setArguments(received));
        }
        HttpResponse forwardedResponse = response(longText(20_000))
            .withHeader(header("x-unpaired", unpaired))
            .withHeader(header("content-type", "text/plain; charset=utf-8"));
        HttpRequest forwardedRequest = request("/forwarded/ü").withMethod("GET").withHeader("x-mixed", MIXED);
        httpState.log(new LogEntry()
            .setType(FORWARDED_REQUEST)
            .setEpochTime(EPOCH)
            .setCorrelationId("forwarded-0")
            .setHttpRequest(forwardedRequest)
            .setHttpResponse(forwardedResponse)
            .setExpectation(new Expectation(forwardedRequest, org.mockserver.matchers.Times.once(), org.mockserver.matchers.TimeToLive.unlimited(), 0).withId("recorded-0").thenRespond(forwardedResponse)));
        httpState.add(new Expectation(request("/active/é").withHeader("x-mixed", MIXED))
            .withId("active-0")
            .thenRespond(response(longText(12_000)).withHeader(header("x-unpaired", unpaired))));
        httpState.add(new Expectation(request("/active/json"))
            .withId("active-1")
            .thenRespond(response().withBody(json("{\"emoji\":\"😀\",\"cjk\":\"中文\"}", MediaType.JSON_UTF_8))));
    }

    private static byte[] allByteValues(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    static HttpRequest retrieveRequest(RetrieveType type, Format format) {
        return request("/mockserver/retrieve")
            .withMethod("PUT")
            .withQueryStringParameter("type", type.name())
            .withQueryStringParameter("format", format.name());
    }

    static List<Object[]> retrieves() {
        List<Object[]> retrieves = new ArrayList<>();
        for (RetrieveType type : RetrieveType.values()) {
            if (type == RetrieveType.METRICS) {
                continue;
            }
            for (Format format : Format.values()) {
                retrieves.add(new Object[]{type, format});
            }
        }
        return retrieves;
    }

    @Test(timeout = 120_000)
    public void shouldWriteTheRecordedBytesAndContentTypeForEveryRetrieve() throws Exception {
        // given
        populate(httpState);

        // when
        Map<String, String> actual = new LinkedHashMap<>();
        for (Object[] retrieve : retrieves()) {
            RetrieveType type = (RetrieveType) retrieve[0];
            Format format = (Format) retrieve[1];
            HttpResponse response = httpState.retrieve(retrieveRequest(type, format));
            Wire netty = nettyWire(response, format);
            Wire servlet = servletWire(response, format);
            String key = type + " " + format;
            actual.put(key, response.getStatusCode() + " " + netty.describe());
            // the servlet frontend must write exactly what Netty writes
            assertThat(key + " servlet", servlet.describe(), is(netty.describe()));
        }

        // then
        String record = System.getProperty("mockserver.recordRetrieveGolden");
        if (record != null && !record.isEmpty()) {
            StringBuilder golden = new StringBuilder();
            actual.forEach((key, value) -> golden.append(key).append(" ").append(value).append("\n"));
            Files.write(Paths.get(record), golden.toString().getBytes(StandardCharsets.UTF_8));
            fail("recorded " + actual.size() + " digests to " + record + "; run again without mockserver.recordRetrieveGolden");
        }
        Map<String, String> expected = readGolden();
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, String> entry : actual.entrySet()) {
            if (!entry.getValue().equals(expected.get(entry.getKey()))) {
                mismatches.add(entry.getKey() + ": expected " + expected.get(entry.getKey()) + " but was " + entry.getValue());
            }
        }
        assertThat(String.join("\n", mismatches), mismatches, is(empty()));
        assertThat(actual.size(), is(expected.size()));
    }

    private static Map<String, String> readGolden() throws IOException {
        Map<String, String> golden = new LinkedHashMap<>();
        try (InputStream in = HttpStateRetrieveGoldenTest.class.getResourceAsStream(GOLDEN)) {
            assertThat("golden file " + GOLDEN, in, notNullValue());
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String[] parts = line.split(" ", 3);
                golden.put(parts[0] + " " + parts[1], parts[2]);
            }
        }
        return golden;
    }

    private Wire nettyWire(HttpResponse response, Format format) {
        List<DefaultHttpObject> objects = new MockServerHttpResponseToFullHttpResponse(new MockServerLogger()).mapMockServerResponseToNettyResponse(response);
        try {
            assertThat(objects.size(), is(1));
            FullHttpResponse full = (FullHttpResponse) objects.get(0);
            ByteBuf content = full.content();
            byte[] bytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bytes);
            return new Wire(full.headers().get(CONTENT_TYPE), bytes, format);
        } finally {
            objects.forEach(ReferenceCountUtil::release);
        }
    }

    private Wire servletWire(HttpResponse response, Format format) {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        new MockServerHttpResponseToHttpServletResponseEncoder(new MockServerLogger()).mapMockServerResponseToHttpServletResponse(response, servletResponse);
        return new Wire(servletResponse.getHeader(CONTENT_TYPE.toString()), servletResponse.getContentAsByteArray(), format);
    }

    static final class Wire {
        final String contentType;
        final byte[] bytes;
        final Format format;

        Wire(String contentType, byte[] bytes, Format format) {
            this.contentType = contentType;
            this.bytes = bytes;
            this.format = format;
        }

        String describe() {
            byte[] normalised = "application/zip".equals(contentType) ? zipWithoutTimes(bytes) : normalise(bytes);
            if (format == Format.OPENAPI) {
                // the operation ids of expectations derived from logged requests are random
                normalised = new String(normalised, StandardCharsets.ISO_8859_1)
                    .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<uuid>")
                    .getBytes(StandardCharsets.ISO_8859_1);
            }
            return String.valueOf(contentType).replace(' ', '_') + " " + normalised.length + " " + sha256(normalised);
        }
    }

    static byte[] normalise(byte[] bytes) {
        // ISO-8859-1 maps each byte to one char and back, so only the ASCII placeholders' bytes change
        String text = new String(bytes, StandardCharsets.ISO_8859_1)
            .replace(LogEntry.LOG_DATE_FORMAT.format(EPOCH), "<timestamp>")
            .replaceAll("mockserver-client-go/v[0-9]+", "mockserver-client-go/vN");
        String version = Version.getVersion();
        if (version != null && !version.isEmpty()) {
            text = text.replace("\"" + version + "\"", "\"<version>\"");
        }
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * A copy of a zip with the time and date of every entry, in its local and its central directory
     * header, set to zero: a zip records when each entry was written.
     */
    static byte[] zipWithoutTimes(byte[] zip) {
        byte[] copy = zip.clone();
        ByteBuffer buffer = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN);
        int endOfCentralDirectory = copy.length - 22;
        assertThat("end of central directory signature", buffer.getInt(endOfCentralDirectory), is(0x06054b50));
        int entries = buffer.getShort(endOfCentralDirectory + 10) & 0xffff;
        int header = buffer.getInt(endOfCentralDirectory + 16);
        for (int i = 0; i < entries; i++) {
            assertThat("central directory header signature", buffer.getInt(header), is(0x02014b50));
            buffer.putInt(header + 12, 0);
            int local = buffer.getInt(header + 42);
            assertThat("local file header signature", buffer.getInt(local), is(0x04034b50));
            buffer.putInt(local + 10, 0);
            header += 46 + (buffer.getShort(header + 28) & 0xffff) + (buffer.getShort(header + 30) & 0xffff) + (buffer.getShort(header + 32) & 0xffff);
        }
        return copy;
    }

    static String sha256(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
