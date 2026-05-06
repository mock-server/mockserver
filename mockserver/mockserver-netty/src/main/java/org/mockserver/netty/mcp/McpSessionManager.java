package org.mockserver.netty.mcp;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class McpSessionManager {

    private static final int MAX_SESSIONS = 100;
    private static final long SESSION_TTL_MILLIS = 60 * 60 * 1000L; // 60 minutes
    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();
    private final MockServerLogger mockServerLogger;

    /**
     * Dedicated thread pool for MCP request processing to avoid blocking Netty event loop threads.
     * Tool calls (e.g., verify_request, raw_verify) may call Future.get() which blocks — running
     * these on a separate executor prevents event loop starvation.
     * Owned by the session manager (singleton per server) and shut down with {@link #shutdown()}.
     */
    private final EventExecutorGroup executor;

    public McpSessionManager(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.executor = new DefaultEventExecutorGroup(2,
            new Scheduler.SchedulerThreadFactory("MockServer-mcp-worker"));
    }

    /**
     * Returns the executor used for MCP request processing.
     */
    public EventExecutorGroup getExecutor() {
        return executor;
    }

    /**
     * Shuts down the MCP executor gracefully. Called during server shutdown.
     */
    public void shutdown() {
        executor.shutdownGracefully(0, 5, TimeUnit.SECONDS);
    }

    public synchronized McpSession createSession() {
        if (sessions.size() >= MAX_SESSIONS) {
            evictOldest();
        }
        String sessionId = UUIDService.getUUID();
        McpSession session = new McpSession(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    public McpSession getSession(String sessionId) {
        McpSession session = sessions.get(sessionId);
        if (session != null) {
            if (System.currentTimeMillis() - session.getLastAccessedAt() > SESSION_TTL_MILLIS) {
                sessions.remove(sessionId);
                return null;
            }
            session.touch();
        }
        return session;
    }

    public boolean isValidSession(String sessionId) {
        return sessionId != null && getSession(sessionId) != null;
    }

    public McpSession removeSession(String sessionId) {
        return sessions.remove(sessionId);
    }

    public int size() {
        return sessions.size();
    }

    private void evictOldest() {
        String oldestId = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<String, McpSession> entry : sessions.entrySet()) {
            if (entry.getValue().getLastAccessedAt() < oldestTime) {
                oldestTime = entry.getValue().getLastAccessedAt();
                oldestId = entry.getKey();
            }
        }
        if (oldestId != null) {
            sessions.remove(oldestId);
            if (mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("MCP session limit reached ({}), evicted least recently used session {}")
                        .setArguments(MAX_SESSIONS, oldestId)
                );
            }
        }
    }
}
