package org.mockserver.log.model;

public class LogEntryMessages {

    public static final String RECEIVED_REQUEST_MESSAGE_FORMAT = "received request:{}";
    public static final String UPDATED_EXPECTATION_MESSAGE_FORMAT = "updated expectation:{}";
    public static final String CREATED_EXPECTATION_MESSAGE_FORMAT = "creating expectation:{}";
    public static final String REMOVED_EXPECTATION_MESSAGE_FORMAT = "removed expectation:{}";
    public static final String NO_MATCH_RESPONSE_NO_EXPECTATION_MESSAGE_FORMAT = "no expectation for:{}returning response:{}";
    public static final String NO_MATCH_RESPONSE_ERROR_MESSAGE_FORMAT = "error:{}handling request:{}returning response:{}";
    public static final String VERIFICATION_REQUESTS_MESSAGE_FORMAT = "verifying requests that match:{}";
    public static final String VERIFICATION_REQUEST_SEQUENCES_MESSAGE_FORMAT = "verifying sequence that match:{}";
    public static final String TEMPLATE_GENERATED_MESSAGE_FORMAT = "generated output:{}from template:{}for request:{}";

}