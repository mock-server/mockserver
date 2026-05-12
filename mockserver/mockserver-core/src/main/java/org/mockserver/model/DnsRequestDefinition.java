package org.mockserver.model;

import java.util.Objects;

public class DnsRequestDefinition extends RequestDefinition {
    private int hashCode;
    private String dnsName;
    private DnsRecordType dnsType;
    private DnsRecordClass dnsClass;

    public static DnsRequestDefinition dnsRequest() {
        return new DnsRequestDefinition();
    }

    public static DnsRequestDefinition dnsRequest(String dnsName) {
        return new DnsRequestDefinition().withDnsName(dnsName);
    }

    public static DnsRequestDefinition dnsRequest(String dnsName, DnsRecordType dnsType) {
        return new DnsRequestDefinition().withDnsName(dnsName).withDnsType(dnsType);
    }

    public String getDnsName() {
        return dnsName;
    }

    public DnsRequestDefinition withDnsName(String dnsName) {
        this.dnsName = dnsName;
        this.hashCode = 0;
        return this;
    }

    public DnsRecordType getDnsType() {
        return dnsType;
    }

    public DnsRequestDefinition withDnsType(DnsRecordType dnsType) {
        this.dnsType = dnsType;
        this.hashCode = 0;
        return this;
    }

    public DnsRecordClass getDnsClass() {
        return dnsClass;
    }

    public DnsRequestDefinition withDnsClass(DnsRecordClass dnsClass) {
        this.dnsClass = dnsClass;
        this.hashCode = 0;
        return this;
    }

    @Override
    public DnsRequestDefinition shallowClone() {
        return not(dnsRequest(), not)
            .withDnsName(dnsName)
            .withDnsType(dnsType)
            .withDnsClass(dnsClass);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        DnsRequestDefinition that = (DnsRequestDefinition) o;
        return Objects.equals(dnsName, that.dnsName) &&
            dnsType == that.dnsType &&
            dnsClass == that.dnsClass;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), dnsName, dnsType, dnsClass);
        }
        return hashCode;
    }
}
