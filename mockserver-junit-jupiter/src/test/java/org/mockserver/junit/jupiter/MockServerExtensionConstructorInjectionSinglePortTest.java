package org.mockserver.junit.jupiter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.test.TestLoggerExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

@ExtendWith({
    MockServerExtension.class,
    TestLoggerExtension.class,
})
@MockServerSettings(ports = 8989)
class MockServerExtensionConstructorInjectionSinglePortTest {
    private final MockServerClient client;

    public MockServerExtensionConstructorInjectionSinglePortTest(MockServerClient client) {
        this.client = client;
    }

    @Test
    void injectsClientWithStartedServer() {
        assertThat(client, is(not(nullValue())));
        assertThat(client.hasStarted(), is(true));
    }

    @Test
    void usesRequestedPort() {
        assertThat(client.remoteAddress().getPort(), is(equalTo(8989)));
    }
}