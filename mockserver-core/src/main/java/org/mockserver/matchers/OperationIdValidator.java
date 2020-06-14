package org.mockserver.matchers;

import com.atlassian.oai.validator.interaction.request.CustomRequestValidator;
import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.report.ValidationReport;

import javax.annotation.Nonnull;

public class OperationIdValidator implements CustomRequestValidator {
    public static final String OPERATION_NO_MATCH_KEY = "validation.request.operation.noMatch";
    private final String operationId;
    private final String errorMessage;

    public OperationIdValidator(String operationId) {
        this.operationId = operationId;
        this.errorMessage = "expected match against operation{}but request matched operation{}";
    }

    @Override
    public ValidationReport validate(@Nonnull Request request, @Nonnull ApiOperation apiOperation) {
        ValidationReport validationReport;
        if (apiOperation.getOperation().getOperationId().equals(operationId)) {
            validationReport = ValidationReport.empty();
        } else {
            validationReport = ValidationReport.singleton(ValidationReport.Message.create(OPERATION_NO_MATCH_KEY, this.errorMessage).withAdditionalInfo(operationId, apiOperation.getOperation().getOperationId()).build());
        }
        return validationReport;
    }
}