package org.mockserver.templates.engine.helpers;

import org.mockserver.mock.CrossProtocolEventBus;
import org.mockserver.mock.ScenarioManager;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Template helper that exposes scenario state (the WireMock "scenarios" pattern)
 * to response templates, so a value captured or derived in one request can drive
 * a later response.
 * <p>
 * Exposed in all three template engines (Velocity, JavaScript, Mustache) under
 * the {@code scenario} key (see {@link org.mockserver.templates.engine.TemplateFunctions#BUILT_IN_HELPERS}).
 * <p>
 * Template API:
 * <ul>
 *   <li>{@code $scenario.get("name")} &mdash; current state of scenario {@code name}
 *       ({@code "Started"} if never set, {@code ""} for a {@code null} name)</li>
 *   <li>{@code $scenario.set("name", "state")} &mdash; updates the state and returns
 *       {@code ""} so it can be called inline in Velocity (e.g. {@code $scenario.set('flow','step2')})</li>
 *   <li>{@code $scenario.matches("name", "state")} &mdash; {@code true} if the current
 *       state equals {@code state}</li>
 * </ul>
 * <p>
 * The helper is backed by the <b>live</b> {@link ScenarioManager} wired into
 * {@link CrossProtocolEventBus#getInstance()} by
 * {@link org.mockserver.mock.HttpState} &mdash; the same instance that matchers
 * use. So a {@code set} from a template is immediately visible to a subsequent
 * matcher {@code matchesState} check and vice-versa. The instance is resolved
 * lazily on each call (rather than captured at construction) because
 * {@link TemplateFunctions#BUILT_IN_HELPERS} is a static singleton created before
 * any server is initialised; lazy resolution lets a single helper instance track
 * whichever server is currently running.
 * <p>
 * <b>Thread-safety:</b> this helper is stateless and shared across all template
 * executions. {@link ScenarioManager} is itself concurrency-safe (its state store
 * is backed by a {@code ConcurrentHashMap} with compare-and-set transitions), so
 * no additional synchronisation is required here.
 * <p>
 * <b>Limitation:</b> when no server has been initialised (e.g. the template engine
 * is exercised in isolation with no {@link org.mockserver.mock.HttpState}), there is
 * no live {@link ScenarioManager}; in that case {@code get}/{@code matches} return
 * the unset defaults and {@code set} is a no-op rather than throwing.
 */
public class ScenarioTemplateHelper {

    // set while the template dry run renders on this thread, so a set(...) is reported, not applied
    private static final ThreadLocal<BiConsumer<String, String>> SUPPRESSED_SETS = new ThreadLocal<>();

    /**
     * Runs {@code render} with every {@code set(name, state)} made on this thread passed to
     * {@code suppressedSets} instead of changing the live scenario state.
     */
    public static <T> T withoutStateChanges(BiConsumer<String, String> suppressedSets, Supplier<T> render) {
        BiConsumer<String, String> previous = SUPPRESSED_SETS.get();
        SUPPRESSED_SETS.set(suppressedSets);
        try {
            return render.get();
        } finally {
            if (previous == null) {
                SUPPRESSED_SETS.remove();
            } else {
                SUPPRESSED_SETS.set(previous);
            }
        }
    }

    /**
     * Resolves the live {@link ScenarioManager}, or {@code null} if no server
     * has been initialised yet.
     */
    private ScenarioManager scenarioManager() {
        return CrossProtocolEventBus.getInstance().getScenarioManager();
    }

    /**
     * Returns the current state of the named scenario, or {@link ScenarioManager#STARTED}
     * ("Started") if the scenario has never been set. Returns {@code ""} for a
     * {@code null} name or when no live {@link ScenarioManager} is available.
     */
    public String get(String name) {
        if (name == null) {
            return "";
        }
        ScenarioManager manager = scenarioManager();
        if (manager == null) {
            return ScenarioManager.STARTED;
        }
        String state = manager.getState(name);
        return state != null ? state : "";
    }

    /**
     * Updates the state of the named scenario. Returns {@code ""} so it can be
     * invoked inline within a Velocity template (e.g. {@code $scenario.set('flow','step2')})
     * without emitting any output. No-op when no live {@link ScenarioManager} is
     * available, or when {@code name}/{@code state} is {@code null}.
     */
    public String set(String name, String state) {
        if (name != null && state != null) {
            BiConsumer<String, String> suppressedSets = SUPPRESSED_SETS.get();
            if (suppressedSets != null) {
                suppressedSets.accept(name, state);
                return "";
            }
            ScenarioManager manager = scenarioManager();
            if (manager != null) {
                manager.setState(name, state);
            }
        }
        return "";
    }

    /**
     * Returns {@code true} if the current state of the named scenario equals
     * {@code state}. Mirrors matcher semantics: an unset scenario is implicitly
     * in the {@link ScenarioManager#STARTED} state. Returns {@code false} when no
     * live {@link ScenarioManager} is available (except where {@code state}
     * matches the implicit "Started" default).
     */
    public boolean matches(String name, String state) {
        if (name == null || state == null) {
            return false;
        }
        ScenarioManager manager = scenarioManager();
        if (manager == null) {
            return ScenarioManager.STARTED.equals(state);
        }
        return manager.matchesState(name, state);
    }

    @Override
    public String toString() {
        return "ScenarioTemplateHelper";
    }
}
