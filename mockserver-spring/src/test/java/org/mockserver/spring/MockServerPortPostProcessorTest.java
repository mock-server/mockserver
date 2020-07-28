package org.mockserver.spring;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class MockServerPortPostProcessorTest {

    @Value("${mockServerPort}")
    Integer mockServerPort;

    @Test
    public void providesMockServerPortAsProperty() {
        assertThat(mockServerPort, is(not(nullValue())));
    }
}