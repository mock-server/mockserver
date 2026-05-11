package org.mockserver.mock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScenarioManager {

    public static final String STARTED = "Started";
    private final ConcurrentHashMap<String, String> scenarioStates = new ConcurrentHashMap<>();

    public String getState(String scenarioName) {
        if (scenarioName == null) {
            return STARTED;
        }
        return scenarioStates.getOrDefault(scenarioName, STARTED);
    }

    public void setState(String scenarioName, String state) {
        if (scenarioName == null || state == null) {
            return;
        }
        scenarioStates.put(scenarioName, state);
    }

    public boolean matchesState(String scenarioName, String requiredState) {
        if (scenarioName == null || requiredState == null) {
            return true;
        }
        return requiredState.equals(getState(scenarioName));
    }

    public boolean matchesAndTransition(String scenarioName, String requiredState, String newState) {
        if (scenarioName == null || requiredState == null) {
            return true;
        }
        final boolean[] matched = {false};
        scenarioStates.compute(scenarioName, (key, currentState) -> {
            String effective = currentState != null ? currentState : STARTED;
            if (requiredState.equals(effective)) {
                matched[0] = true;
                return newState != null ? newState : effective;
            }
            matched[0] = false;
            return currentState;
        });
        return matched[0];
    }

    public void transitionState(String scenarioName, String newState) {
        if (scenarioName != null && newState != null) {
            scenarioStates.put(scenarioName, newState);
        }
    }

    public void clear(String scenarioName) {
        if (scenarioName != null) {
            scenarioStates.remove(scenarioName);
        }
    }

    public void reset() {
        scenarioStates.clear();
    }

    public Map<String, String> getAllStates() {
        return new ConcurrentHashMap<>(scenarioStates);
    }
}
