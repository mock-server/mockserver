package org.mockserver.serialization.model;

import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpForwardValidateAction;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

public class HttpForwardValidateActionDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpForwardValidateAction> {
    private String specUrlOrPayload;
    private String host;
    private Integer port;
    private HttpForward.Scheme scheme;
    private Boolean validateRequest;
    private Boolean validateResponse;
    private HttpForwardValidateAction.ValidationMode validationMode;
    private DelayDTO delay;

    public HttpForwardValidateActionDTO(HttpForwardValidateAction httpForwardValidateAction) {
        if (httpForwardValidateAction != null) {
            specUrlOrPayload = httpForwardValidateAction.getSpecUrlOrPayload();
            host = httpForwardValidateAction.getHost();
            port = httpForwardValidateAction.getPort();
            scheme = httpForwardValidateAction.getScheme();
            validateRequest = httpForwardValidateAction.getValidateRequest();
            validateResponse = httpForwardValidateAction.getValidateResponse();
            validationMode = httpForwardValidateAction.getValidationMode();
            if (httpForwardValidateAction.getDelay() != null) {
                delay = new DelayDTO(httpForwardValidateAction.getDelay());
            }
        }
    }

    public HttpForwardValidateActionDTO() {
    }

    public HttpForwardValidateAction buildObject() {
        return new HttpForwardValidateAction()
            .withSpecUrlOrPayload(specUrlOrPayload)
            .withHost(host)
            .withPort(port != null ? port : 80)
            .withScheme(scheme != null ? scheme : HttpForward.Scheme.HTTP)
            .withValidateRequest(validateRequest != null ? validateRequest : true)
            .withValidateResponse(validateResponse != null ? validateResponse : true)
            .withValidationMode(validationMode != null ? validationMode : HttpForwardValidateAction.ValidationMode.STRICT)
            .withDelay(delay != null ? delay.buildObject() : null);
    }

    public String getSpecUrlOrPayload() {
        return specUrlOrPayload;
    }

    public HttpForwardValidateActionDTO setSpecUrlOrPayload(String specUrlOrPayload) {
        this.specUrlOrPayload = specUrlOrPayload;
        return this;
    }

    public String getHost() {
        return host;
    }

    public HttpForwardValidateActionDTO setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public HttpForwardValidateActionDTO setPort(Integer port) {
        this.port = port;
        return this;
    }

    public HttpForward.Scheme getScheme() {
        return scheme;
    }

    public HttpForwardValidateActionDTO setScheme(HttpForward.Scheme scheme) {
        this.scheme = scheme;
        return this;
    }

    public Boolean getValidateRequest() {
        return validateRequest;
    }

    public HttpForwardValidateActionDTO setValidateRequest(Boolean validateRequest) {
        this.validateRequest = validateRequest;
        return this;
    }

    public Boolean getValidateResponse() {
        return validateResponse;
    }

    public HttpForwardValidateActionDTO setValidateResponse(Boolean validateResponse) {
        this.validateResponse = validateResponse;
        return this;
    }

    public HttpForwardValidateAction.ValidationMode getValidationMode() {
        return validationMode;
    }

    public HttpForwardValidateActionDTO setValidationMode(HttpForwardValidateAction.ValidationMode validationMode) {
        this.validationMode = validationMode;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public HttpForwardValidateActionDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }
}
