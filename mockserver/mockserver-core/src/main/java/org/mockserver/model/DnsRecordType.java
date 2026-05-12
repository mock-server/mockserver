package org.mockserver.model;

public enum DnsRecordType {
    A(1),
    AAAA(28),
    CNAME(5),
    MX(15),
    SRV(33),
    TXT(16),
    PTR(12);

    private final int intValue;

    DnsRecordType(int intValue) {
        this.intValue = intValue;
    }

    public int intValue() {
        return intValue;
    }

    public static DnsRecordType fromIntValue(int intValue) {
        for (DnsRecordType type : values()) {
            if (type.intValue == intValue) {
                return type;
            }
        }
        return null;
    }
}
