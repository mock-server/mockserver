package org.mockserver.model;

public enum DnsRecordClass {
    IN(1),
    CH(3),
    HS(4),
    ANY(255);

    private final int intValue;

    DnsRecordClass(int intValue) {
        this.intValue = intValue;
    }

    public int intValue() {
        return intValue;
    }

    public static DnsRecordClass fromIntValue(int intValue) {
        for (DnsRecordClass cls : values()) {
            if (cls.intValue == intValue) {
                return cls;
            }
        }
        return null;
    }
}
