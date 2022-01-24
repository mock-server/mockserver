package org.mockserver.java;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class VersionTest {

    @Test
    public void shouldHandleJava6Format() {
        // given
        Version.javaVersion = "1.6.0_23";

        // then
        assertThat(Version.getVersion(), equalTo(6));
    }

    @Test
    public void shouldHandleJava7Format() {
        // given
        Version.javaVersion = "1.7.0";

        // then
        assertThat(Version.getVersion(), equalTo(7));

        // given
        Version.javaVersion = "1.7.0_80";

        // then
        assertThat(Version.getVersion(), equalTo(7));
    }

    @Test
    public void shouldHandleJava8Format() {
        // given
        Version.javaVersion = "1.8.0_211";

        // then
        assertThat(Version.getVersion(), equalTo(8));
    }

    @Test
    public void shouldHandleJava9Format() {
        // given
        Version.javaVersion = "9.0.1";

        // then
        assertThat(Version.getVersion(), equalTo(9));
    }

    @Test
    public void shouldHandleJava11Format() {
        // given
        Version.javaVersion = "11.0.4";

        // then
        assertThat(Version.getVersion(), equalTo(11));
    }

    @Test
    public void shouldHandleJava12Format() {
        // given
        Version.javaVersion = "12";

        // then
        assertThat(Version.getVersion(), equalTo(12));

        // given
        Version.javaVersion = "12.0.1";

        // then
        assertThat(Version.getVersion(), equalTo(12));
    }

}