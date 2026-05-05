# frozen_string_literal: true

module MockServer
  # Fluent API for building expectations via the +when+ method.
  #
  # Returned by {Client#when} to allow chaining:
  #   client.when(request).respond(response)
  #   client.when(request).forward(forward)
  #   client.when(request).error(error)
  class ForwardChainExpectation
    def initialize(client, expectation)
      @client = client
      @expectation = expectation
    end

    # Set the expectation ID.
    # @param id [String]
    # @return [self]
    def with_id(id)
      @expectation.id = id
      self
    end

    # Set the expectation priority.
    # @param priority [Integer]
    # @return [self]
    def with_priority(priority)
      @expectation.priority = priority
      self
    end

    # Set the response action. Accepts an HttpResponse, HttpTemplate, or
    # a Proc/lambda callback.
    # @param response_or_callback [HttpResponse, HttpTemplate, Proc]
    # @return [Array<Expectation>]
    def respond(response_or_callback)
      if response_or_callback.respond_to?(:call)
        client_id = @client.send(:register_websocket_callback, 'response', response_or_callback)
        @expectation.http_response_object_callback = HttpObjectCallback.new(client_id: client_id)
      elsif response_or_callback.is_a?(HttpResponse)
        @expectation.http_response = response_or_callback
      elsif response_or_callback.is_a?(HttpTemplate)
        @expectation.http_response_template = response_or_callback
      else
        raise TypeError,
              "Expected HttpResponse, HttpTemplate, or callable, got #{response_or_callback.class.name}"
      end
      @client.upsert(@expectation)
    end

    # Set the response action with a delay.
    # @param response [HttpResponse]
    # @param delay [Delay]
    # @return [Array<Expectation>]
    def respond_with_delay(response, delay)
      response.delay = delay
      @expectation.http_response = response
      @client.upsert(@expectation)
    end

    # Set the forward action. Accepts an HttpForward, HttpOverrideForwardedRequest,
    # HttpTemplate, or a Proc/lambda callback.
    # @param forward_or_callback [HttpForward, HttpOverrideForwardedRequest, HttpTemplate, Proc]
    # @param response_callback [Proc, nil] optional response transform callback
    # @return [Array<Expectation>]
    def forward(forward_or_callback, response_callback = nil)
      if forward_or_callback.respond_to?(:call)
        client_id = @client.send(
          :register_websocket_callback,
          'forward', forward_or_callback, response_callback
        )
        obj_callback = HttpObjectCallback.new(client_id: client_id)
        obj_callback.response_callback = true if response_callback
        @expectation.http_forward_object_callback = obj_callback
      elsif forward_or_callback.is_a?(HttpForward)
        @expectation.http_forward = forward_or_callback
      elsif forward_or_callback.is_a?(HttpOverrideForwardedRequest)
        @expectation.http_override_forwarded_request = forward_or_callback
      elsif forward_or_callback.is_a?(HttpTemplate)
        @expectation.http_forward_template = forward_or_callback
      else
        raise TypeError,
              "Expected HttpForward, HttpOverrideForwardedRequest, HttpTemplate, or callable, " \
              "got #{forward_or_callback.class.name}"
      end
      @client.upsert(@expectation)
    end

    # Set the forward action with a delay.
    # @param forward [HttpForward]
    # @param delay [Delay]
    # @return [Array<Expectation>]
    def forward_with_delay(forward, delay)
      forward.delay = delay
      @expectation.http_forward = forward
      @client.upsert(@expectation)
    end

    # Set the error action.
    # @param error [HttpError]
    # @return [Array<Expectation>]
    def error(error)
      @expectation.http_error = error
      @client.upsert(@expectation)
    end
  end
end
