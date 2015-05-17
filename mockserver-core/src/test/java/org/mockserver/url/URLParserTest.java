package org.mockserver.url;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class URLParserTest {

    @Test
    public void shouldDetectPath() {
        // isn't path
        assertTrue(URLParser.isFullUrl("http://www.mock-server.com/some/path"));
        assertTrue(URLParser.isFullUrl("https://www.mock-server.com/some/path"));
        assertTrue(URLParser.isFullUrl("//www.mock-server.com/some/path"));

        // is path
        assertFalse(URLParser.isFullUrl(null));
        assertFalse(URLParser.isFullUrl("/some/path"));
        assertFalse(URLParser.isFullUrl("some/path"));
    }

    @Test
    public void shouldReturnPath() {
        assertThat(URLParser.returnPath("http://www.mock-server.com/some/path"), is("/some/path"));
        assertThat(URLParser.returnPath("https://www.mock-server.com/some/path"), is("/some/path"));
        assertThat(URLParser.returnPath("https://www.abc123.com/some/path"), is("/some/path"));
        assertThat(URLParser.returnPath("https://www.abc.123.com/some/path"), is("/some/path"));
        assertThat(URLParser.returnPath("http://Administrator:password@192.168.50.70:8091/some/path"), is("/some/path"));
        assertThat(URLParser.returnPath("https://Administrator:password@www.abc.123.com/some/path"), is("/some/path"));
        assertThat(URLParser.returnPath("//www.abc.123.com/some/path"), is("/some/path"));
        assertThat(URLParser.returnPath("//Administrator:password@www.abc.123.com/some/path"), is("/some/path"));
        assertThat(URLParser.returnPath("/some/path"), is("/some/path"));
        assertThat(URLParser.returnPath("/123/456"), is("/123/456"));
    }


}
