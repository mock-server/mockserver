package org.mockserver.cli;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class HealthCheckTest {

    @Test
    public void shouldReturnDefaultPortWhenNoEnvSet() {
        assertThat(HealthCheck.resolvePort(), is(1080));
    }

    @Test
    public void shouldReturnFalseWhenServerNotRunning() {
        assertThat(HealthCheck.check(19999), is(false));
    }

}
