package org.mockserver.grpc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GrpcStatusMapper {

    public static final String GRPC_STATUS_HEADER = "grpc-status";
    public static final String GRPC_MESSAGE_HEADER = "grpc-message";
    public static final String GRPC_STATUS_NAME_HEADER = "grpc-status-name";
    public static final String GRPC_CONTENT_TYPE = "application/grpc";

    public enum GrpcStatusCode {
        OK(0, 200),
        CANCELLED(1, 499),
        UNKNOWN(2, 500),
        INVALID_ARGUMENT(3, 400),
        DEADLINE_EXCEEDED(4, 504),
        NOT_FOUND(5, 404),
        ALREADY_EXISTS(6, 409),
        PERMISSION_DENIED(7, 403),
        RESOURCE_EXHAUSTED(8, 429),
        FAILED_PRECONDITION(9, 400),
        ABORTED(10, 409),
        OUT_OF_RANGE(11, 400),
        UNIMPLEMENTED(12, 501),
        INTERNAL(13, 500),
        UNAVAILABLE(14, 503),
        DATA_LOSS(15, 500),
        UNAUTHENTICATED(16, 401);

        private final int code;
        private final int httpStatus;

        GrpcStatusCode(int code, int httpStatus) {
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public int getCode() {
            return code;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }

    private static final Map<Integer, GrpcStatusCode> BY_CODE;
    private static final Map<String, GrpcStatusCode> BY_NAME;
    private static final Map<Integer, GrpcStatusCode> HTTP_TO_GRPC;

    static {
        Map<Integer, GrpcStatusCode> byCode = new LinkedHashMap<>();
        Map<String, GrpcStatusCode> byName = new LinkedHashMap<>();
        Map<Integer, GrpcStatusCode> httpToGrpc = new LinkedHashMap<>();
        for (GrpcStatusCode status : GrpcStatusCode.values()) {
            byCode.put(status.code, status);
            byName.put(status.name(), status);
            httpToGrpc.putIfAbsent(status.httpStatus, status);
        }
        BY_CODE = Collections.unmodifiableMap(byCode);
        BY_NAME = Collections.unmodifiableMap(byName);
        HTTP_TO_GRPC = Collections.unmodifiableMap(httpToGrpc);
    }

    public static GrpcStatusCode fromCode(int code) {
        return BY_CODE.getOrDefault(code, GrpcStatusCode.UNKNOWN);
    }

    public static GrpcStatusCode fromName(String name) {
        if (name == null) {
            return GrpcStatusCode.UNKNOWN;
        }
        return BY_NAME.getOrDefault(name.toUpperCase(), GrpcStatusCode.UNKNOWN);
    }

    public static GrpcStatusCode fromHttpStatus(int httpStatus) {
        return HTTP_TO_GRPC.getOrDefault(httpStatus, GrpcStatusCode.UNKNOWN);
    }

    public static boolean isGrpcContentType(String contentType) {
        return contentType != null && contentType.startsWith(GRPC_CONTENT_TYPE);
    }
}
