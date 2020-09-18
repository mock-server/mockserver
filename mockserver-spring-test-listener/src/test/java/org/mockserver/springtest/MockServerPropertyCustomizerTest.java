package org.mockserver.springtest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@MockServerTest("server.url=http://localhost:${mockServerPort}/")
public class MockServerPropertyCustomizerTest {

    @Value("${server.url}")
    private String serverUrl;

    @MockServerPort
    private Integer mockServerPort;

    @Test
    public void mockServerPortIsReplaced() {
        assertThat(serverUrl, is(not(nullValue())));
        assertThat(serverUrl, is("http://localhost:" + mockServerPort + "/"));
    }
}