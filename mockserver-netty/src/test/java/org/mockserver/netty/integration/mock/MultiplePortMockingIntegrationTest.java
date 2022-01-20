package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.MediaType;
import org.mockserver.serialization.PortBindingSerializer;
import org.mockserver.socket.PortFactory;
import org.mockserver.testing.integration.mock.AbstractBasicMockingIntegrationTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.PortBinding.portBinding;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class MultiplePortMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static Integer[] severHttpPort;
    private final Random random = new Random();

    @BeforeClass
    public static void startServer() {
        mockServerClient = startClientAndServer(0, PortFactory.findFreePort(), 0, PortFactory.findFreePort());
        List<Integer> boundPorts = ((ClientAndServer) mockServerClient).getLocalPorts();
        severHttpPort = boundPorts.toArray(new Integer[0]);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Override
    public int getServerPort() {
        return severHttpPort[random.nextInt(severHttpPort.length)];
    }

    @Test
    public void shouldReturnStatus() {
        // given
        PortBindingSerializer portBindingSerializer = new PortBindingSerializer(new MockServerLogger());

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(severHttpPort)
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withPath(calculatePath("mockserver/status"))
                    .withMethod("PUT"),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(severHttpPort)
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("mockserver/status"))
                    .withMethod("PUT"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnStatusOnCustomPath() {
        String originalStatusPath = ConfigurationProperties.livenessHttpGetPath();
        try {
            // given
            ConfigurationProperties.livenessHttpGetPath("/livenessProbe");
            PortBindingSerializer portBindingSerializer = new PortBindingSerializer(new MockServerLogger());

            // - in http
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                    .withBody(json(portBindingSerializer.serialize(
                        portBinding(severHttpPort)
                    ), MediaType.JSON_UTF_8)),
                makeRequest(
                    request()
                        .withPath(calculatePath("livenessProbe"))
                        .withMethod("GET"),
                    headersToIgnore)
            );
            // - in https
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                    .withBody(json(portBindingSerializer.serialize(
                        portBinding(severHttpPort)
                    ), MediaType.JSON_UTF_8)),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("livenessProbe"))
                        .withMethod("GET"),
                    headersToIgnore)
            );
        } finally {
            ConfigurationProperties.livenessHttpGetPath(originalStatusPath);
        }
    }

    @Test
    public void shouldBindToNewSocket() {
        // given
        int firstNewPort = PortFactory.findFreePort();
        int secondNewPort = PortFactory.findFreePort();
        PortBindingSerializer portBindingSerializer = new PortBindingSerializer(new MockServerLogger());

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(firstNewPort)
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withPath(calculatePath("mockserver/bind"))
                    .withMethod("PUT")
                    .withBody("{" + NEW_LINE +
                        "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
        ArrayList<Integer> ports = new ArrayList<>(Arrays.asList(severHttpPort));
        ports.add(firstNewPort);
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(ports)
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withPath(calculatePath("mockserver/status"))
                    .withMethod("PUT"),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(secondNewPort)
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("mockserver/bind"))
                    .withMethod("PUT")
                    .withBody("{" + NEW_LINE +
                        "  \"ports\" : [ " + secondNewPort + " ]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
        ports.add(secondNewPort);
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(ports)
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("mockserver/status"))
                    .withMethod("PUT")
                    .withBody("{" + NEW_LINE +
                        "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }
}
