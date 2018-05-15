package org.mockserver.junit.jupiter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

@ExtendWith(MockServerExtension.class)
class MockServerExtensionTest {
    private ClientAndServer client;

    public MockServerExtensionTest(ClientAndServer client) {
        this.client = client;
    }

    @Test
    public void injectsClientWithStartedServer() {
        assertThat(client, is(not(nullValue())));
        assertThat(client.isRunning(), is(true));
    }

    @Test
    public void usesNonZeroPort() {
        assertThat(client.getLocalPort(), is(not(nullValue())));
    }
}