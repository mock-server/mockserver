package org.mockserver.model;

import java.util.Objects;

public class DnsRecord extends ObjectWithJsonToString {
    private int hashCode;
    private String name;
    private DnsRecordType type;
    private DnsRecordClass dnsClass;
    private Integer ttl;
    private String value;
    private Integer priority;
    private Integer weight;
    private Integer port;

    public static DnsRecord dnsRecord() {
        return new DnsRecord();
    }

    public static DnsRecord aRecord(String name, String ip) {
        return new DnsRecord().withName(name).withType(DnsRecordType.A).withValue(ip);
    }

    public static DnsRecord aaaaRecord(String name, String ip) {
        return new DnsRecord().withName(name).withType(DnsRecordType.AAAA).withValue(ip);
    }

    public static DnsRecord cnameRecord(String name, String cname) {
        return new DnsRecord().withName(name).withType(DnsRecordType.CNAME).withValue(cname);
    }

    public static DnsRecord mxRecord(String name, int priority, String exchange) {
        return new DnsRecord().withName(name).withType(DnsRecordType.MX).withPriority(priority).withValue(exchange);
    }

    public static DnsRecord srvRecord(String name, int priority, int weight, int port, String target) {
        return new DnsRecord().withName(name).withType(DnsRecordType.SRV).withPriority(priority).withWeight(weight).withPort(port).withValue(target);
    }

    public static DnsRecord txtRecord(String name, String text) {
        return new DnsRecord().withName(name).withType(DnsRecordType.TXT).withValue(text);
    }

    public static DnsRecord ptrRecord(String name, String pointer) {
        return new DnsRecord().withName(name).withType(DnsRecordType.PTR).withValue(pointer);
    }

    public String getName() {
        return name;
    }

    public DnsRecord withName(String name) {
        this.name = name;
        this.hashCode = 0;
        return this;
    }

    public DnsRecordType getType() {
        return type;
    }

    public DnsRecord withType(DnsRecordType type) {
        this.type = type;
        this.hashCode = 0;
        return this;
    }

    public DnsRecordClass getDnsClass() {
        return dnsClass;
    }

    public DnsRecord withDnsClass(DnsRecordClass dnsClass) {
        this.dnsClass = dnsClass;
        this.hashCode = 0;
        return this;
    }

    public Integer getTtl() {
        return ttl;
    }

    public DnsRecord withTtl(Integer ttl) {
        this.ttl = ttl;
        this.hashCode = 0;
        return this;
    }

    public String getValue() {
        return value;
    }

    public DnsRecord withValue(String value) {
        this.value = value;
        this.hashCode = 0;
        return this;
    }

    public Integer getPriority() {
        return priority;
    }

    public DnsRecord withPriority(Integer priority) {
        this.priority = priority;
        this.hashCode = 0;
        return this;
    }

    public Integer getWeight() {
        return weight;
    }

    public DnsRecord withWeight(Integer weight) {
        this.weight = weight;
        this.hashCode = 0;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public DnsRecord withPort(Integer port) {
        this.port = port;
        this.hashCode = 0;
        return this;
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
        DnsRecord that = (DnsRecord) o;
        return Objects.equals(name, that.name) &&
            type == that.type &&
            dnsClass == that.dnsClass &&
            Objects.equals(ttl, that.ttl) &&
            Objects.equals(value, that.value) &&
            Objects.equals(priority, that.priority) &&
            Objects.equals(weight, that.weight) &&
            Objects.equals(port, that.port);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(name, type, dnsClass, ttl, value, priority, weight, port);
        }
        return hashCode;
    }
}
