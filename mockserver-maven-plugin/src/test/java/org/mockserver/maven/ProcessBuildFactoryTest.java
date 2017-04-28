package org.mockserver.maven;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.core.Is.isA;
import static org.junit.Assert.*;

public class ProcessBuildFactoryTest {

    @Test
    public void shouldCreateProcessBuild(){
        assertThat(new ProcessBuildFactory().create(Arrays.asList("some", "random", "process")), isA(ProcessBuilder.class));
    }

}