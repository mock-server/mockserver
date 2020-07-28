package org.mockserver.spring;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockserver.client.MockServerClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@MockServerTest
@SpringBootTest
@RunWith(SpringRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MockServerTestExecutionListenerTest {

    private MockServerClient mockServerClient;

    @Test
    public void _0setsMockServerClient() {
        assertThat(mockServerClient, is(not(nullValue())));
    }

    @Test
    public void _1mockServerIsResetedAfterTestMethod() {
        // TODO
    }
}