package org.mockserver.serialization.model;

import org.mockserver.model.DnsRecord;
import org.mockserver.model.DnsRecordClass;
import org.mockserver.model.DnsRecordType;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

public class DnsRecordDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<DnsRecord> {
    private String name;
    private DnsRecordType type;
    private DnsRecordClass dnsClass;
    private Integer ttl;
    private String value;
    private Integer priority;
    private Integer weight;
    private Integer port;

    public DnsRecordDTO(DnsRecord dnsRecord) {
        if (dnsRecord != null) {
            name = dnsRecord.getName();
            type = dnsRecord.getType();
            dnsClass = dnsRecord.getDnsClass();
            ttl = dnsRecord.getTtl();
            value = dnsRecord.getValue();
            priority = dnsRecord.getPriority();
            weight = dnsRecord.getWeight();
            port = dnsRecord.getPort();
        }
    }

    public DnsRecordDTO() {
    }

    public DnsRecord buildObject() {
        return DnsRecord.dnsRecord()
            .withName(name)
            .withType(type)
            .withDnsClass(dnsClass)
            .withTtl(ttl)
            .withValue(value)
            .withPriority(priority)
            .withWeight(weight)
            .withPort(port);
    }

    public String getName() {
        return name;
    }

    public DnsRecordDTO setName(String name) {
        this.name = name;
        return this;
    }

    public DnsRecordType getType() {
        return type;
    }

    public DnsRecordDTO setType(DnsRecordType type) {
        this.type = type;
        return this;
    }

    public DnsRecordClass getDnsClass() {
        return dnsClass;
    }

    public DnsRecordDTO setDnsClass(DnsRecordClass dnsClass) {
        this.dnsClass = dnsClass;
        return this;
    }

    public Integer getTtl() {
        return ttl;
    }

    public DnsRecordDTO setTtl(Integer ttl) {
        this.ttl = ttl;
        return this;
    }

    public String getValue() {
        return value;
    }

    public DnsRecordDTO setValue(String value) {
        this.value = value;
        return this;
    }

    public Integer getPriority() {
        return priority;
    }

    public DnsRecordDTO setPriority(Integer priority) {
        this.priority = priority;
        return this;
    }

    public Integer getWeight() {
        return weight;
    }

    public DnsRecordDTO setWeight(Integer weight) {
        this.weight = weight;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public DnsRecordDTO setPort(Integer port) {
        this.port = port;
        return this;
    }
}
