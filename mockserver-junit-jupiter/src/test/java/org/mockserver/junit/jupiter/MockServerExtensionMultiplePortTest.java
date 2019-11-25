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
@MockServerSettings(ports = {8787, 8888})
class MockServerExtensionMultiplePortTest {
    private MockServerClient client;

    public MockServerExtensionMultiplePortTest(MockServerClient client) {
        this.client = client;
    }

    @Test
    void injectsClientWithStartedServer() {
        assertThat(client, is(not(nullValue())));
        assertThat(client.isRunning(), is(true));
    }

    @Test
    void usesRequestedPorts() {
        assertThat(client.remoteAddress().getPort(), is(equalTo(8787)));
    }
}