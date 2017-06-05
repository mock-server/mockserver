package org.mockserver.logging;

import java.io.File;

import ch.qos.logback.core.rolling.TriggeringPolicyBase;

public class TriggerAlwaysPolicy<E> extends TriggeringPolicyBase<E> {
    @Override
    public boolean isTriggeringEvent(File activeFile, E event) {
        return true;
    }
}
