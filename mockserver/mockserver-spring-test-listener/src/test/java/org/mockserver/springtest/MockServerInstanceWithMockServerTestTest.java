package org.mockserver.springtest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.client.MockServerClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(SpringRunner.class)
@MockServerTest
@MockServerInstance(name = "other")
public class MockServerInstanceWithMockServerTestTest {

    private MockServerClient mockServerClient;

    @InjectMockServer("other")
    private MockServerClient otherClient;

    @MockServerPort
    private Integer mockServerPort;

    @Value("${mockserver.other.port}")
    private int otherPort;

    @Test
    public void shouldInjectTheDefaultAndTheNamedInstanceSeparately() {
        assertThat(mockServerClient, is(sameInstance(MockServerPropertyCustomizer.getOrCreateClientAndServer())));
        assertThat(mockServerClient.getPort(), is(mockServerPort));
        assertThat(otherClient.getPort(), is(otherPort));
        assertThat(otherPort, is(not(mockServerPort)));
    }
}
