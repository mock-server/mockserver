package org.mockserver.netty.mcp;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class McpSessionTest {

    @Test
    public void shouldCreateSession() {
        McpSession session = new McpSession("test-session-id");

        assertThat(session.getSessionId(), is("test-session-id"));
        assertThat(session.isInitialized(), is(false));
        assertThat(session.getCreatedAt() > 0, is(true));
    }

    @Test
    public void shouldMarkInitialized() {
        McpSession session = new McpSession("test-session-id");
        assertThat(session.isInitialized(), is(false));

        session.markInitialized();
        assertThat(session.isInitialized(), is(true));
    }

    @Test
    public void shouldPreserveSessionId() {
        McpSession session1 = new McpSession("id-1");
        McpSession session2 = new McpSession("id-2");

        assertThat(session1.getSessionId(), is("id-1"));
        assertThat(session2.getSessionId(), is("id-2"));
    }

    @Test
    public void shouldHaveCreationTimestamp() {
        long before = System.currentTimeMillis();
        McpSession session = new McpSession("test");
        long after = System.currentTimeMillis();

        assertThat(session.getCreatedAt() >= before, is(true));
        assertThat(session.getCreatedAt() <= after, is(true));
    }

    @Test
    public void shouldInitializeLastAccessedAtToCreatedAt() {
        McpSession session = new McpSession("test");

        assertThat(session.getLastAccessedAt(), is(session.getCreatedAt()));
    }

    @Test
    public void shouldUpdateLastAccessedAtOnTouch() throws InterruptedException {
        McpSession session = new McpSession("test");
        long initialAccessTime = session.getLastAccessedAt();

        Thread.sleep(10);
        session.touch();

        assertThat(session.getLastAccessedAt() > initialAccessTime, is(true));
    }
}
