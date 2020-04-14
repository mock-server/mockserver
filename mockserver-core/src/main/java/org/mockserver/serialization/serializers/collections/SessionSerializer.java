package org.mockserver.serialization.serializers.collections;


import org.mockserver.model.Session;

public class SessionSerializer extends KeysAndValuesSerializer<Session> {

    private static final long serialVersionUID = 1L;

    public SessionSerializer() {
        super(Session.class);
    }
   

}
