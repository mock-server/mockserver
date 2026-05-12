package org.mockserver.model;

public enum DnsResponseCode {
    NOERROR(0),
    FORMERR(1),
    SERVFAIL(2),
    NXDOMAIN(3),
    NOTIMP(4),
    REFUSED(5);

    private final int intValue;

    DnsResponseCode(int intValue) {
        this.intValue = intValue;
    }

    public int intValue() {
        return intValue;
    }

    public static DnsResponseCode fromIntValue(int intValue) {
        for (DnsResponseCode code : values()) {
            if (code.intValue == intValue) {
                return code;
            }
        }
        return null;
    }
}
