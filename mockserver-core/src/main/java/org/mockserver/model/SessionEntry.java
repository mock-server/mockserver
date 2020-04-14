package org.mockserver.model;


public class SessionEntry extends KeyAndValue {

    public SessionEntry(String name, String value) {
        super(name, value);
    }

    public SessionEntry(NottableString name, NottableString value) {
        super(name, value);
    }

    public static SessionEntry sessionEntry(String name, String value) {
        return new SessionEntry(name, value);
    }

    public static SessionEntry sessionEntry(NottableString name, NottableString value) {
        return new SessionEntry(name, value);
    }
}
