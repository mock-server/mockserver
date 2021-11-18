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
@MockServerTest("server.url.path-b=http://localhost:${mockServerPort}/b")
public class MockServerPropertyCustomizerTest extends MockServerPropertyCustomizerAbstractTest {

    @Value("${server.url.path-b}")
    private String serverUrlPathB;

    @MockServerPort
    private Integer mockServerPort;

    @Test
    public void mockServerPortIsReplaced() {
        assertThat(serverUrlPathB, is(not(nullValue())));
        assertThat(serverUrlPathB, is("http://localhost:" + mockServerPort + "/b"));
    }

    @Test
    public void mockServerPortIsReplacedWithSuperClass() {
        assertThat(serverUrlPathA, is(not(nullValue())));
        assertThat(serverUrlPathA, is("http://localhost:" + mockServerPort + "/a"));
    }
}
