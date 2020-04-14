package org.mockserver.model;

import java.util.List;

public class Session extends KeysAndValues<SessionEntry, Session> {

    public Session(List<SessionEntry> entries) {
        withEntries(entries);
    }

    public Session(SessionEntry... entries) {
        withEntries(entries);
    }

    @Override
    public SessionEntry build(NottableString name, NottableString value) {
        return new SessionEntry(name, value);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Session clone() {
        return new Session().withEntries(getEntries());
    }
}
