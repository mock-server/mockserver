package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

public class HttpForwardValidateAction extends Action<HttpForwardValidateAction> {
    private int hashCode;
    private String specUrlOrPayload;
    private String host;
    private Integer port = 80;
    private HttpForward.Scheme scheme = HttpForward.Scheme.HTTP;
    private Boolean validateRequest = true;
    private Boolean validateResponse = true;
    private ValidationMode validationMode = ValidationMode.STRICT;

    public static HttpForwardValidateAction forwardValidate() {
        return new HttpForwardValidateAction();
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.FORWARD_VALIDATE;
    }

    public String getSpecUrlOrPayload() {
        return specUrlOrPayload;
    }

    public HttpForwardValidateAction withSpecUrlOrPayload(String specUrlOrPayload) {
        this.specUrlOrPayload = specUrlOrPayload;
        this.hashCode = 0;
        return this;
    }

    public String getHost() {
        return host;
    }

    public HttpForwardValidateAction withHost(String host) {
        this.host = host;
        this.hashCode = 0;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public HttpForwardValidateAction withPort(Integer port) {
        this.port = port;
        this.hashCode = 0;
        return this;
    }

    public HttpForward.Scheme getScheme() {
        return scheme;
    }

    public HttpForwardValidateAction withScheme(HttpForward.Scheme scheme) {
        this.scheme = scheme;
        this.hashCode = 0;
        return this;
    }

    public Boolean getValidateRequest() {
        return validateRequest;
    }

    public HttpForwardValidateAction withValidateRequest(Boolean validateRequest) {
        this.validateRequest = validateRequest;
        this.hashCode = 0;
        return this;
    }

    public Boolean getValidateResponse() {
        return validateResponse;
    }

    public HttpForwardValidateAction withValidateResponse(Boolean validateResponse) {
        this.validateResponse = validateResponse;
        this.hashCode = 0;
        return this;
    }

    public ValidationMode getValidationMode() {
        return validationMode;
    }

    public HttpForwardValidateAction withValidationMode(ValidationMode validationMode) {
        this.validationMode = validationMode;
        this.hashCode = 0;
        return this;
    }

    public enum ValidationMode {
        STRICT,
        LOG_ONLY
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
        HttpForwardValidateAction that = (HttpForwardValidateAction) o;
        return Objects.equals(specUrlOrPayload, that.specUrlOrPayload) &&
            Objects.equals(host, that.host) &&
            Objects.equals(port, that.port) &&
            scheme == that.scheme &&
            Objects.equals(validateRequest, that.validateRequest) &&
            Objects.equals(validateResponse, that.validateResponse) &&
            validationMode == that.validationMode;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), specUrlOrPayload, host, port, scheme, validateRequest, validateResponse, validationMode);
        }
        return hashCode;
    }
}
