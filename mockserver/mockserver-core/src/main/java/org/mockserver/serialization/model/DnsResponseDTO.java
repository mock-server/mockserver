package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.mockserver.model.DnsRecord;
import org.mockserver.model.DnsResponse;
import org.mockserver.model.DnsResponseCode;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.ArrayList;
import java.util.List;

public class DnsResponseDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<DnsResponse> {
    private DelayDTO delay;
    private List<DnsRecordDTO> answerRecords;
    private List<DnsRecordDTO> authorityRecords;
    private List<DnsRecordDTO> additionalRecords;
    private DnsResponseCode responseCode;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean primary;

    public DnsResponseDTO(DnsResponse dnsResponse) {
        if (dnsResponse != null) {
            if (dnsResponse.getDelay() != null) {
                delay = new DelayDTO(dnsResponse.getDelay());
            }
            if (dnsResponse.getAnswerRecords() != null) {
                answerRecords = new ArrayList<>();
                dnsResponse.getAnswerRecords().forEach(r -> answerRecords.add(new DnsRecordDTO(r)));
            }
            if (dnsResponse.getAuthorityRecords() != null) {
                authorityRecords = new ArrayList<>();
                dnsResponse.getAuthorityRecords().forEach(r -> authorityRecords.add(new DnsRecordDTO(r)));
            }
            if (dnsResponse.getAdditionalRecords() != null) {
                additionalRecords = new ArrayList<>();
                dnsResponse.getAdditionalRecords().forEach(r -> additionalRecords.add(new DnsRecordDTO(r)));
            }
            responseCode = dnsResponse.getResponseCode();
            primary = dnsResponse.isPrimary();
        }
    }

    public DnsResponseDTO() {
    }

    public DnsResponse buildObject() {
        DnsResponse dnsResponse = new DnsResponse()
            .withDelay(delay != null ? delay.buildObject() : null)
            .withResponseCode(responseCode)
            .withPrimary(primary);
        if (answerRecords != null) {
            List<DnsRecord> records = new ArrayList<>();
            answerRecords.forEach(dto -> records.add(dto.buildObject()));
            dnsResponse.withAnswerRecords(records);
        }
        if (authorityRecords != null) {
            List<DnsRecord> records = new ArrayList<>();
            authorityRecords.forEach(dto -> records.add(dto.buildObject()));
            dnsResponse.withAuthorityRecords(records);
        }
        if (additionalRecords != null) {
            List<DnsRecord> records = new ArrayList<>();
            additionalRecords.forEach(dto -> records.add(dto.buildObject()));
            dnsResponse.withAdditionalRecords(records);
        }
        return dnsResponse;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public DnsResponseDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }

    public List<DnsRecordDTO> getAnswerRecords() {
        return answerRecords;
    }

    public DnsResponseDTO setAnswerRecords(List<DnsRecordDTO> answerRecords) {
        this.answerRecords = answerRecords;
        return this;
    }

    public List<DnsRecordDTO> getAuthorityRecords() {
        return authorityRecords;
    }

    public DnsResponseDTO setAuthorityRecords(List<DnsRecordDTO> authorityRecords) {
        this.authorityRecords = authorityRecords;
        return this;
    }

    public List<DnsRecordDTO> getAdditionalRecords() {
        return additionalRecords;
    }

    public DnsResponseDTO setAdditionalRecords(List<DnsRecordDTO> additionalRecords) {
        this.additionalRecords = additionalRecords;
        return this;
    }

    public DnsResponseCode getResponseCode() {
        return responseCode;
    }

    public DnsResponseDTO setResponseCode(DnsResponseCode responseCode) {
        this.responseCode = responseCode;
        return this;
    }

    public boolean isPrimary() {
        return primary;
    }

    public DnsResponseDTO setPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }
}
