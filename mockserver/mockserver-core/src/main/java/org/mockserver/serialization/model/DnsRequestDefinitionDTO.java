package org.mockserver.serialization.model;

import org.mockserver.model.DnsRecordClass;
import org.mockserver.model.DnsRecordType;
import org.mockserver.model.DnsRequestDefinition;

public class DnsRequestDefinitionDTO extends RequestDefinitionDTO {

    private String dnsName;
    private DnsRecordType dnsType;
    private DnsRecordClass dnsClass;

    public DnsRequestDefinitionDTO(DnsRequestDefinition dnsRequestDefinition) {
        super(dnsRequestDefinition != null ? dnsRequestDefinition.getNot() : null);
        if (dnsRequestDefinition != null) {
            dnsName = dnsRequestDefinition.getDnsName();
            dnsType = dnsRequestDefinition.getDnsType();
            dnsClass = dnsRequestDefinition.getDnsClass();
        }
    }

    public DnsRequestDefinitionDTO() {
        super(false);
    }

    public DnsRequestDefinition buildObject() {
        return (DnsRequestDefinition) new DnsRequestDefinition()
            .withDnsName(dnsName)
            .withDnsType(dnsType)
            .withDnsClass(dnsClass)
            .withNot(getNot());
    }

    public String getDnsName() {
        return dnsName;
    }

    public DnsRequestDefinitionDTO setDnsName(String dnsName) {
        this.dnsName = dnsName;
        return this;
    }

    public DnsRecordType getDnsType() {
        return dnsType;
    }

    public DnsRequestDefinitionDTO setDnsType(DnsRecordType dnsType) {
        this.dnsType = dnsType;
        return this;
    }

    public DnsRecordClass getDnsClass() {
        return dnsClass;
    }

    public DnsRequestDefinitionDTO setDnsClass(DnsRecordClass dnsClass) {
        this.dnsClass = dnsClass;
        return this;
    }
}
