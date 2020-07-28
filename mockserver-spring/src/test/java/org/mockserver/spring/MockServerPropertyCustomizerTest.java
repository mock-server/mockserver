package org.mockserver.spring;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
@MockServerTest("server.url=http://localhost:${mockServerPort}/")
public class MockServerPropertyCustomizerTest {

    @Value("${server.url}")
    String serverUrl;

    @Value("${mockServerPort}")
    Integer mockServerPort;

    @Test
    public void mockServerPortIsReplaced() {
        assertThat(serverUrl, is(not(nullValue())));
        assertThat(serverUrl, is("http://localhost:" + mockServerPort + "/"));
    }
}