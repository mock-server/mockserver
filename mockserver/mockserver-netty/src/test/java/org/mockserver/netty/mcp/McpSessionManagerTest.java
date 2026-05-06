package org.mockserver.netty.mcp;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class McpSessionManagerTest {

    @Test
    public void shouldCreateSession() {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());
        McpSession session = manager.createSession();

        assertThat(session, notNullValue());
        assertThat(session.getSessionId(), notNullValue());
        assertThat(manager.size(), is(1));
    }

    @Test
    public void shouldGetSession() {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());
        McpSession session = manager.createSession();

        McpSession retrieved = manager.getSession(session.getSessionId());
        assertThat(retrieved, notNullValue());
        assertThat(retrieved.getSessionId(), is(session.getSessionId()));
    }

    @Test
    public void shouldReturnNullForUnknownSession() {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());

        McpSession retrieved = manager.getSession("nonexistent");
        assertThat(retrieved, nullValue());
    }

    @Test
    public void shouldValidateSession() {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());
        McpSession session = manager.createSession();

        assertThat(manager.isValidSession(session.getSessionId()), is(true));
        assertThat(manager.isValidSession("nonexistent"), is(false));
        assertThat(manager.isValidSession(null), is(false));
    }

    @Test
    public void shouldRemoveSession() {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());
        McpSession session = manager.createSession();

        McpSession removed = manager.removeSession(session.getSessionId());
        assertThat(removed, notNullValue());
        assertThat(manager.isValidSession(session.getSessionId()), is(false));
        assertThat(manager.size(), is(0));
    }

    @Test
    public void shouldReturnNullWhenRemovingNonexistentSession() {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());

        McpSession removed = manager.removeSession("nonexistent");
        assertThat(removed, nullValue());
    }

    @Test
    public void shouldEvictOldestSessionWhenMaxReached() throws InterruptedException {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());

        // Create 100 sessions (the max)
        String firstSessionId = null;
        for (int i = 0; i < 100; i++) {
            McpSession session = manager.createSession();
            if (i == 0) {
                firstSessionId = session.getSessionId();
            }
            // Small delay to ensure different timestamps for LRU eviction
            if (i == 0) {
                Thread.sleep(10);
            }
        }
        assertThat(manager.size(), is(100));

        // Create one more — should evict the oldest (first)
        manager.createSession();
        assertThat(manager.size(), is(100));
        assertThat(manager.isValidSession(firstSessionId), is(false));
    }

    @Test
    public void shouldTouchSessionOnGet() throws InterruptedException {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());
        McpSession session = manager.createSession();
        long initialTime = session.getLastAccessedAt();

        Thread.sleep(10);
        McpSession retrieved = manager.getSession(session.getSessionId());

        assertThat(retrieved.getLastAccessedAt() > initialTime, is(true));
    }

    @Test
    public void shouldExpireSessionAfterTtl() throws Exception {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());
        McpSession session = manager.createSession();

        // Use reflection to set lastAccessedAt to a time beyond the TTL (60 minutes ago + 1 second)
        java.lang.reflect.Field lastAccessedAtField = McpSession.class.getDeclaredField("lastAccessedAt");
        lastAccessedAtField.setAccessible(true);
        lastAccessedAtField.set(session, System.currentTimeMillis() - (60 * 60 * 1000L + 1000));

        // getSession should return null for expired session
        McpSession retrieved = manager.getSession(session.getSessionId());
        assertThat(retrieved, nullValue());

        // isValidSession should also return false
        assertThat(manager.isValidSession(session.getSessionId()), is(false));

        // Session should have been removed
        assertThat(manager.size(), is(0));
    }

    @Test
    public void shouldNotExpireRecentSession() {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());
        McpSession session = manager.createSession();

        // A just-created session should not be expired
        McpSession retrieved = manager.getSession(session.getSessionId());
        assertThat(retrieved, notNullValue());
        assertThat(manager.isValidSession(session.getSessionId()), is(true));
    }

    @Test
    public void shouldCreateSessionSynchronized() throws InterruptedException {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());
        int threadCount = 10;
        int sessionsPerThread = 10;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                for (int i = 0; i < sessionsPerThread; i++) {
                    manager.createSession();
                }
                latch.countDown();
            }).start();
        }

        latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(manager.size(), is(100));
    }

    @Test
    public void shouldShutdownExecutor() {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());
        assertThat(manager.getExecutor().isShutdown(), is(false));

        manager.shutdown();
        assertThat(manager.getExecutor().isShuttingDown(), is(true));
    }

    @Test
    public void shouldProvideExecutor() {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());
        assertThat(manager.getExecutor(), notNullValue());
    }

    @Test
    public void shouldEvictLeastRecentlyUsedSession() throws InterruptedException {
        McpSessionManager manager = new McpSessionManager(new MockServerLogger());

        // Create first two sessions with a gap between them
        McpSession firstSession = manager.createSession();
        Thread.sleep(15);
        McpSession secondSession = manager.createSession();
        Thread.sleep(15);

        // Create 98 more sessions (total 100)
        for (int i = 2; i < 100; i++) {
            manager.createSession();
        }
        assertThat(manager.size(), is(100));

        // Touch the first session so it's no longer the LRU
        Thread.sleep(15);
        manager.getSession(firstSession.getSessionId());

        // Create one more — should evict the second session (least recently used), not the first
        manager.createSession();
        assertThat(manager.size(), is(100));
        assertThat("first session should survive because it was touched", manager.isValidSession(firstSession.getSessionId()), is(true));
        assertThat("second session should be evicted as LRU", manager.isValidSession(secondSession.getSessionId()), is(false));
    }
}
