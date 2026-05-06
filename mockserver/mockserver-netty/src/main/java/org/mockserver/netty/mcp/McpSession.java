package org.mockserver.netty.mcp;

public class McpSession {

    private final String sessionId;
    private final long createdAt;
    private volatile boolean initialized;
    private volatile long lastAccessedAt;

    public McpSession(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
        this.initialized = false;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        this.initialized = true;
    }

    public long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void touch() {
        this.lastAccessedAt = System.currentTimeMillis();
    }
}
