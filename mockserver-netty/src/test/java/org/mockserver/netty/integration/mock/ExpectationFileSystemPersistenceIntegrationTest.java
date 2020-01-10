package org.mockserver.netty.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.netty.MockServer;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.HttpRequestSerializer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ExpectationFileSystemPersistenceIntegrationTest {

    private static NettyHttpClient httpClient;
    private static EventLoopGroup clientEventLoopGroup;
    private static final MockServerLogger mockServerLogger = new MockServerLogger();

    @BeforeClass
    public static void createClientAndEventLoopGroup() {
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(ExpectationFileSystemPersistenceIntegrationTest.class.getSimpleName() + "-eventLoop"));
        httpClient = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null);
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void shouldPersistExpectationsToJsonOnAdd() throws Exception {
        // given
        String persistedExpectationsPath = ConfigurationProperties.persistedExpectationsPath();
        ConfigurationProperties.persistExpectations(true);
        MockServer mockServer = null;

        try {
            File persistedExpectations = File.createTempFile("persistedExpectations", ".json");
            ConfigurationProperties.persistedExpectationsPath(persistedExpectations.getAbsolutePath());

            // when
            mockServer = new MockServer();
            Expectation[] expectations = {
                new Expectation(
                    request()
                        .withPath("/simpleFirst")
                )
                    .withId("one")
                    .thenRespond(
                    response()
                        .withBody("some first response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleSecond")
                )
                    .withId("two")
                    .thenRespond(
                    response()
                        .withBody("some second response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleThird")
                )
                    .withId("three")
                    .thenRespond(
                    response()
                        .withBody("some third response")
                )
            };
            httpClient
                .sendRequest(
                    request()
                        .withMethod("PUT")
                        .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
                        .withPath("/mockserver/expectation")
                        .withBody(new ExpectationSerializer(mockServerLogger).serialize(expectations))
                )
                .get(10, TimeUnit.SECONDS);

            MILLISECONDS.sleep(1500);

            // then
            String expectedFileContents = "[ {" + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecond\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            assertThat(persistedExpectations.getAbsolutePath() + " does not match expected content", new String(Files.readAllBytes(persistedExpectations.toPath()), StandardCharsets.UTF_8), is(expectedFileContents));
        } finally {
            ConfigurationProperties.persistedExpectationsPath(persistedExpectationsPath);
            ConfigurationProperties.persistExpectations(false);
            stopQuietly(mockServer);
        }
    }

    @Test
    public void shouldPersistExpectationsToJsonOnRemove() throws Exception {
        // given
        String persistedExpectationsPath = ConfigurationProperties.persistedExpectationsPath();
        ConfigurationProperties.persistExpectations(true);
        MockServer mockServer = null;

        try {
            File persistedExpectations = File.createTempFile("persistedExpectations", "json");
            ConfigurationProperties.persistedExpectationsPath(persistedExpectations.getAbsolutePath());

            // when
            mockServer = new MockServer();
            Expectation[] expectations = {
                new Expectation(
                    request()
                        .withPath("/simpleFirst")
                )
                    .withId("one")
                    .thenRespond(
                    response()
                        .withBody("some first response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleSecond")
                )
                    .withId("two")
                    .thenRespond(
                    response()
                        .withBody("some second response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleThird")
                )
                    .withId("three")
                    .thenRespond(
                    response()
                        .withBody("some third response")
                )
            };
            httpClient
                .sendRequest(
                    request()
                        .withMethod("PUT")
                        .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
                        .withPath("/mockserver/expectation")
                        .withBody(new ExpectationSerializer(mockServerLogger).serialize(expectations))
                )
                .get(10, TimeUnit.SECONDS);
            httpClient
                .sendRequest(
                    request()
                        .withMethod("PUT")
                        .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
                        .withPath("/mockserver/clear")
                        .withBody(new HttpRequestSerializer(mockServerLogger).serialize(request().withPath("/simpleSecond")))
                )
                .get(10, TimeUnit.SECONDS);

            MILLISECONDS.sleep(1500);

            // then
            String expectedFileContents = "[ {" + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            assertThat(persistedExpectations.getAbsolutePath() + " does not match expected content", new String(Files.readAllBytes(persistedExpectations.toPath()), StandardCharsets.UTF_8), is(expectedFileContents));
        } finally {
            ConfigurationProperties.persistedExpectationsPath(persistedExpectationsPath);
            ConfigurationProperties.persistExpectations(false);
            stopQuietly(mockServer);
        }
    }

    @Test
    public void shouldPersistExpectationsToJsonOnUpdate() throws Exception {
        // given
        String persistedExpectationsPath = ConfigurationProperties.persistedExpectationsPath();
        ConfigurationProperties.persistExpectations(true);
        MockServer mockServer = null;

        try {
            File persistedExpectations = File.createTempFile("persistedExpectations", "json");
            ConfigurationProperties.persistedExpectationsPath(persistedExpectations.getAbsolutePath());

            // when
            mockServer = new MockServer();
            Expectation[] expectations = {
                new Expectation(
                    request()
                        .withPath("/simpleFirst")
                )
                    .withId("one")
                    .thenRespond(
                    response()
                        .withBody("some first response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleSecond")
                )
                    .withId("two")
                    .thenRespond(
                    response()
                        .withBody("some second response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleThird")
                )
                    .withId("three")
                    .thenRespond(
                    response()
                        .withBody("some third response")
                )
            };
            httpClient
                .sendRequest(
                    request()
                        .withMethod("PUT")
                        .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
                        .withPath("/mockserver/expectation")
                        .withBody(new ExpectationSerializer(mockServerLogger).serialize(expectations))
                )
                .get(10, TimeUnit.SECONDS);
            httpClient
                .sendRequest(
                    request()
                        .withMethod("PUT")
                        .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
                        .withPath("/mockserver/expectation")
                        .withBody(new ExpectationSerializer(mockServerLogger).serialize(new Expectation(
                            request()
                                .withPath("/simpleSecondUpdated")
                        )
                            .withId("two")
                            .thenRespond(
                                response()
                                    .withBody("some second updated response")
                            )))
                )
                .get(10, TimeUnit.SECONDS);
            MILLISECONDS.sleep(1500);

            // then
            String expectedFileContents = "[ {" + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecondUpdated\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second updated response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            assertThat(persistedExpectations.getAbsolutePath() + " does not match expected content", new String(Files.readAllBytes(persistedExpectations.toPath()), StandardCharsets.UTF_8), is(expectedFileContents));
        } finally {
            ConfigurationProperties.persistedExpectationsPath(persistedExpectationsPath);
            ConfigurationProperties.persistExpectations(false);
            stopQuietly(mockServer);
        }
    }

}