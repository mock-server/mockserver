package org.mockserver.dashboard.serializers;

public interface Description {

    int MAX_LENGTH = 115;

    int length();

    Object toObject();

}
