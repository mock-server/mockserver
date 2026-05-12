package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DnsResponse extends Action<DnsResponse> {
    private int hashCode;
    private List<DnsRecord> answerRecords;
    private List<DnsRecord> authorityRecords;
    private List<DnsRecord> additionalRecords;
    private DnsResponseCode responseCode;

    public static DnsResponse dnsResponse() {
        return new DnsResponse();
    }

    public List<DnsRecord> getAnswerRecords() {
        return answerRecords;
    }

    public DnsResponse withAnswerRecords(List<DnsRecord> answerRecords) {
        this.answerRecords = answerRecords;
        this.hashCode = 0;
        return this;
    }

    public DnsResponse withAnswerRecords(DnsRecord... answerRecords) {
        this.answerRecords = Arrays.asList(answerRecords);
        this.hashCode = 0;
        return this;
    }

    public DnsResponse withAnswerRecord(DnsRecord answerRecord) {
        if (this.answerRecords == null) {
            this.answerRecords = new ArrayList<>();
        }
        this.answerRecords.add(answerRecord);
        this.hashCode = 0;
        return this;
    }

    public List<DnsRecord> getAuthorityRecords() {
        return authorityRecords;
    }

    public DnsResponse withAuthorityRecords(List<DnsRecord> authorityRecords) {
        this.authorityRecords = authorityRecords;
        this.hashCode = 0;
        return this;
    }

    public DnsResponse withAuthorityRecords(DnsRecord... authorityRecords) {
        this.authorityRecords = Arrays.asList(authorityRecords);
        this.hashCode = 0;
        return this;
    }

    public List<DnsRecord> getAdditionalRecords() {
        return additionalRecords;
    }

    public DnsResponse withAdditionalRecords(List<DnsRecord> additionalRecords) {
        this.additionalRecords = additionalRecords;
        this.hashCode = 0;
        return this;
    }

    public DnsResponse withAdditionalRecords(DnsRecord... additionalRecords) {
        this.additionalRecords = Arrays.asList(additionalRecords);
        this.hashCode = 0;
        return this;
    }

    public DnsResponseCode getResponseCode() {
        return responseCode;
    }

    public DnsResponse withResponseCode(DnsResponseCode responseCode) {
        this.responseCode = responseCode;
        this.hashCode = 0;
        return this;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.DNS_RESPONSE;
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
        DnsResponse that = (DnsResponse) o;
        return Objects.equals(answerRecords, that.answerRecords) &&
            Objects.equals(authorityRecords, that.authorityRecords) &&
            Objects.equals(additionalRecords, that.additionalRecords) &&
            responseCode == that.responseCode;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), answerRecords, authorityRecords, additionalRecords, responseCode);
        }
        return hashCode;
    }
}
