package org.mockserver.grpc;

public class GrpcException extends RuntimeException {

    public GrpcException(String message) {
        super(message);
    }

    public GrpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
