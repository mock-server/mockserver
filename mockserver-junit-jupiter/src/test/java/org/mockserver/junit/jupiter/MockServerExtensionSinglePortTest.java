package org.mockserver.junit.jupiter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = 8989)
class MockServerExtensionSinglePortTest {
    private final MockServerClient client;

    public MockServerExtensionSinglePortTest(MockServerClient client) {
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