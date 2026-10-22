package org.mockserver.extension;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One extension a server has loaded: the instance, where its class came from, which hooks it implements and
 * which of those are disabled, with the reason.
 */
public final class LoadedExtension {

    private final Extension extension;
    private final String name;
    private final int priority;
    private final String className;
    private final String source;
    private final Set<ExtensionHook> hooks;
    private final Map<ExtensionHook, String> disabledHooks = new ConcurrentHashMap<>();

    LoadedExtension(Extension extension, String name, int priority, String source) {
        this.extension = extension;
        this.name = name;
        this.priority = priority;
        this.className = extension.getClass().getName();
        this.source = source;
        EnumSet<ExtensionHook> implemented = EnumSet.noneOf(ExtensionHook.class);
        for (ExtensionHook hook : ExtensionHook.values()) {
            if (hook.isImplementedBy(extension)) {
                implemented.add(hook);
            }
        }
        this.hooks = Collections.unmodifiableSet(implemented);
    }

    public Extension extension() {
        return extension;
    }

    public String name() {
        return name;
    }

    public int priority() {
        return priority;
    }

    public String className() {
        return className;
    }

    /**
     * @return the JAR or directory the extension's class was loaded from, or {@code unknown}
     */
    public String source() {
        return source;
    }

    public Set<ExtensionHook> hooks() {
        return hooks;
    }

    /**
     * @return true when the extension implements the hook and the hook has not been disabled
     */
    public boolean isHookEnabled(ExtensionHook hook) {
        return hooks.contains(hook) && !disabledHooks.containsKey(hook);
    }

    /**
     * @return the reason the hook was disabled, or {@code null} when it is not disabled
     */
    public String disabledReason(ExtensionHook hook) {
        return disabledHooks.get(hook);
    }

    /**
     * @return each disabled hook with the reason, in hook order
     */
    public Map<ExtensionHook, String> disabledHooks() {
        return Collections.unmodifiableMap(new TreeMap<>(disabledHooks));
    }

    boolean disable(ExtensionHook hook, String reason) {
        return hooks.contains(hook) && disabledHooks.putIfAbsent(hook, reason) == null;
    }

    @Override
    public String toString() {
        return name + " (" + className + ")";
    }
}
