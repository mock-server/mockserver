package org.mockserver.serialization.deserializers.collections;

import org.mockserver.model.Session;

public class SessionDeserializer extends KeysAndValuesDeserializer<Session> {

    private static final long serialVersionUID = 1L;

    public SessionDeserializer() {
        super(Session.class);
    }
    
    @Override
    protected Session createObject() {
        return new Session();
    }

}
