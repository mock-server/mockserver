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

    # Set a declarative HTTP chaos/fault injection profile.
    # @param chaos [HttpChaosProfile]
    # @return [self]
    def with_chaos(chaos)
      @expectation.chaos = chaos
      self
    end

    # Set the expectation metadata, used to retrieve or clear it by metadata.
    # @param metadata [Hash] String (or Symbol) keys to any JSON value
    # @return [self]
    def with_metadata(metadata)
      @expectation.metadata = metadata
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

    def respond_with_sse(sse_response)
      @expectation.http_sse_response = sse_response
      @client.upsert(@expectation)
    end

    def respond_with_websocket(websocket_response)
      @expectation.http_websocket_response = websocket_response
      @client.upsert(@expectation)
    end

    # Set a gRPC stream response action.
    # @param grpc_stream_response [GrpcStreamResponse]
    # @return [Array<Expectation>]
    def respond_with_grpc_stream(grpc_stream_response)
      unless grpc_stream_response.is_a?(GrpcStreamResponse)
        raise TypeError,
              "Expected GrpcStreamResponse, got #{grpc_stream_response.class.name}"
      end
      @expectation.grpc_stream_response = grpc_stream_response
      @client.upsert(@expectation)
    end

    # Set a gRPC bidi streaming response action.
    # @param grpc_bidi_response [GrpcBidiResponse]
    # @return [Array<Expectation>]
    def respond_with_grpc_bidi(grpc_bidi_response)
      unless grpc_bidi_response.is_a?(GrpcBidiResponse)
        raise TypeError,
              "Expected GrpcBidiResponse, got #{grpc_bidi_response.class.name}"
      end
      @expectation.grpc_bidi_response = grpc_bidi_response
      @client.upsert(@expectation)
    end

    # Set a binary response action.
    # @param binary_response [BinaryResponse]
    # @return [Array<Expectation>]
    def respond_with_binary(binary_response)
      unless binary_response.is_a?(BinaryResponse)
        raise TypeError,
              "Expected BinaryResponse, got #{binary_response.class.name}"
      end
      @expectation.binary_response = binary_response
      @client.upsert(@expectation)
    end

    # Set a DNS response action.
    # @param dns_response [DnsResponse]
    # @return [Array<Expectation>]
    def respond_with_dns(dns_response)
      unless dns_response.is_a?(DnsResponse)
        raise TypeError,
              "Expected DnsResponse, got #{dns_response.class.name}"
      end
      @expectation.dns_response = dns_response
      @client.upsert(@expectation)
    end

    # Set a forward template action.
    # @param template [HttpTemplate]
    # @return [Array<Expectation>]
    def forward_with_template(template)
      unless template.is_a?(HttpTemplate)
        raise TypeError,
              "Expected HttpTemplate, got #{template.class.name}"
      end
      @expectation.http_forward_template = template
      @client.upsert(@expectation)
    end

    # Set a response class-callback action: the server invokes a server-side
    # class implementing the response callback interface. Accepts either a
    # fully-qualified class-name String (e.g. "com.example.MyResponseCallback")
    # or a pre-built {HttpClassCallback} (carrying an optional +delay+ /
    # +primary+).
    # @param class_callback [String, HttpClassCallback]
    # @return [Array<Expectation>]
    def respond_with_class_callback(class_callback)
      @expectation.http_response_class_callback = class_callback
      @client.upsert(@expectation)
    end

    # Set a forward class-callback action: the server invokes a server-side
    # class implementing the forward callback interface. Accepts either a
    # fully-qualified class-name String or a pre-built {HttpClassCallback}.
    # @param class_callback [String, HttpClassCallback]
    # @return [Array<Expectation>]
    def forward_with_class_callback(class_callback)
      @expectation.http_forward_class_callback = class_callback
      @client.upsert(@expectation)
    end

    # Set an ordered multi-action pipeline of steps.
    #
    # Exactly one step must have +responder: true+; that step produces the
    # HTTP response. All other steps are side-effects executed in order.
    # @param steps [Array<ExpectationStep>]
    # @return [Array<Expectation>]
    def with_steps(steps)
      unless steps.is_a?(Array) && steps.all? { |s| s.is_a?(ExpectationStep) }
        raise TypeError, 'Expected an Array of ExpectationStep objects'
      end

      @expectation.steps = steps
      @client.upsert(@expectation)
    end
  end
end
