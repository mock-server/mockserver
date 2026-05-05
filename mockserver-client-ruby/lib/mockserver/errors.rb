# frozen_string_literal: true

module MockServer
  # Base error class for all MockServer client errors.
  class Error < StandardError; end

  # Raised when the client cannot connect to the MockServer instance.
  class ConnectionError < Error; end

  # Raised when a verification request fails (HTTP 406).
  class VerificationError < Error; end

  # Raised when a WebSocket callback produces an invalid result.
  class CallbackError < Error; end

  # Raised for WebSocket protocol-level errors (connection, registration, etc.).
  class WebSocketError < Error; end
end
