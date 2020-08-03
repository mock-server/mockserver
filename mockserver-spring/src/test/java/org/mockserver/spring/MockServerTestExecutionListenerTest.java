package org.mockserver.spring;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.client.MockServerClient;
import org.springframework.test.context.junit4.SpringRunner;

@MockServerTest
@RunWith(SpringRunner.class)
public class MockServerTestExecutionListenerTest {

    private MockServerClient mockServerClient;

    @Test
    public void setsMockServerClient() {
        assertThat(mockServerClient, is(not(nullValue())));
    }
}