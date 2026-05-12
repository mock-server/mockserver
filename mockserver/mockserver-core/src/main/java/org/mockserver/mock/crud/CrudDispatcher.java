package org.mockserver.mock.crud;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.concurrent.ConcurrentHashMap;

public class CrudDispatcher {

    private final ConcurrentHashMap<String, CrudActionHandler> handlers = new ConcurrentHashMap<>();

    public void register(String basePath, CrudActionHandler handler) {
        String normalized = normalizeBasePath(basePath);
        validateBasePath(normalized);
        handlers.put(normalized, handler);
    }

    public void unregister(String basePath) {
        handlers.remove(normalizeBasePath(basePath));
    }

    public HttpResponse dispatch(HttpRequest request) {
        String path = request.getPath() != null ? request.getPath().getValue() : null;
        if (path == null) {
            return null;
        }
        String method = request.getMethod() != null ? request.getMethod().getValue() : null;
        if (method == null) {
            return null;
        }

        String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

        for (CrudActionHandler handler : handlers.values()) {
            String handlerBasePath = handler.getBasePath();
            if (normalizedPath.equals(handlerBasePath)) {
                switch (method.toUpperCase()) {
                    case "GET":
                        return handler.handleList(request);
                    case "POST":
                        return handler.handleCreate(request);
                    default:
                        return null;
                }
            } else if (normalizedPath.startsWith(handlerBasePath + "/")) {
                String remainder = normalizedPath.substring(handlerBasePath.length() + 1);
                if (!remainder.contains("/")) {
                    switch (method.toUpperCase()) {
                        case "GET":
                            return handler.handleGetById(request);
                        case "PUT":
                            return handler.handleUpdate(request);
                        case "DELETE":
                            return handler.handleDelete(request);
                        default:
                            return null;
                    }
                }
            }
        }
        return null;
    }

    public boolean hasHandler(String basePath) {
        return handlers.containsKey(normalizeBasePath(basePath));
    }

    public void reset() {
        handlers.clear();
    }

    public int size() {
        return handlers.size();
    }

    static void validateBasePath(String basePath) {
        if (basePath == null || basePath.isEmpty()) {
            throw new IllegalArgumentException("basePath must not be empty");
        }
        if (!basePath.startsWith("/")) {
            throw new IllegalArgumentException("basePath must start with /");
        }
        if (basePath.contains("..")) {
            throw new IllegalArgumentException("basePath must not contain path traversal (..)");
        }
        String lower = basePath.toLowerCase();
        if (lower.equals("/mockserver") || lower.startsWith("/mockserver/")) {
            throw new IllegalArgumentException("basePath must not overlap with the /mockserver control plane");
        }
        if ("/".equals(basePath)) {
            throw new IllegalArgumentException("basePath must not be the root path /");
        }
    }

    private static String normalizeBasePath(String basePath) {
        if (basePath == null) {
            return "";
        }
        return basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }
}
