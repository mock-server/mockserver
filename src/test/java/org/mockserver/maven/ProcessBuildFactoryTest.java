package org.mockserver.maven;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.isA;

public class ProcessBuildFactoryTest {

    @Test
    public void shouldCreateProcessBuild(){
        assertThat(new ProcessBuildFactory().create(Arrays.asList("some", "random", "process")), isA(ProcessBuilder.class));
    }

}