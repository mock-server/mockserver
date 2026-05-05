package org.mockserver.java;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class VersionTest {

    @Test
    public void shouldHandleJava6Format() {
        // given
        JDKVersion.javaVersion = "1.6.0_23";

        // then
        assertThat(JDKVersion.getVersion(), equalTo(6));
    }

    @Test
    public void shouldHandleJava7Format() {
        // given
        JDKVersion.javaVersion = "1.7.0";

        // then
        assertThat(JDKVersion.getVersion(), equalTo(7));

        // given
        JDKVersion.javaVersion = "1.7.0_80";

        // then
        assertThat(JDKVersion.getVersion(), equalTo(7));
    }

    @Test
    public void shouldHandleJava8Format() {
        // given
        JDKVersion.javaVersion = "1.8.0_211";

        // then
        assertThat(JDKVersion.getVersion(), equalTo(8));
    }

    @Test
    public void shouldHandleJava9Format() {
        // given
        JDKVersion.javaVersion = "9.0.1";

        // then
        assertThat(JDKVersion.getVersion(), equalTo(9));
    }

    @Test
    public void shouldHandleJava11Format() {
        // given
        JDKVersion.javaVersion = "11.0.4";

        // then
        assertThat(JDKVersion.getVersion(), equalTo(11));
    }

    @Test
    public void shouldHandleJava12Format() {
        // given
        JDKVersion.javaVersion = "12";

        // then
        assertThat(JDKVersion.getVersion(), equalTo(12));

        // given
        JDKVersion.javaVersion = "12.0.1";

        // then
        assertThat(JDKVersion.getVersion(), equalTo(12));
    }

}