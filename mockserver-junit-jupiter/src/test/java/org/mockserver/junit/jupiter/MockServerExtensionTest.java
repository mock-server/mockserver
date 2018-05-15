package org.mockserver.junit.jupiter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

@ExtendWith(MockServerExtension.class)
class MockServerExtensionTest {
    private MockServerClient client;

    public MockServerExtensionTest(MockServerClient client) {
        this.client = client;
    }

    @Test
    public void injectsClientWithStartedServer() {
        assertThat(client, is(not(nullValue())));
        assertThat(client.isRunning(), is(true));
    }
}