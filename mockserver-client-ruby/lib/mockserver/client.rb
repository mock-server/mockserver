# frozen_string_literal: true

require 'net/http'
require 'uri'
require 'json'
require 'openssl'

module MockServer
  # Synchronous MockServer client.
  #
  # Provides the full MockServer REST API plus a fluent builder DSL and
  # WebSocket-based object callback support.
  #
  # @example Basic usage
  #   client = MockServer::Client.new('localhost', 1080)
  #   client.when(
  #     HttpRequest.request(path: '/hello')
  #   ).respond(
  #     HttpResponse.response(body: 'world')
  #   )
  #   client.close
  #
  # @example Block form (auto-close)
  #   MockServer::Client.new('localhost', 1080) do |c|
  #     c.when(HttpRequest.request(path: '/hello'))
  #      .respond(HttpResponse.response(body: 'world'))
  #   end
  class Client
    HTTP_TIMEOUT = 60 # seconds, matching Python client

    # @param host [String]
    # @param port [Integer]
    # @param context_path [String]
    # @param secure [Boolean]
    # @param ca_cert_path [String, nil]
    # @param tls_verify [Boolean]
    def initialize(host, port, context_path: '', secure: false,
                   ca_cert_path: nil, tls_verify: true)
      @host = host
      @port = port
      @context_path = context_path
      @secure = secure
      @ca_cert_path = ca_cert_path
      @tls_verify = tls_verify
      @websocket_clients = []
      @websocket_mutex = Mutex.new

      scheme = secure ? 'https' : 'http'
      ctx_path = ''
      if context_path && !context_path.empty?
        ctx_path = context_path.start_with?('/') ? context_path : "/#{context_path}"
      end
      @base_url = "#{scheme}://#{host}:#{port}#{ctx_path}"

      if block_given?
        begin
          yield self
        ensure
          close
        end
      end
    end

    # -------------------------------------------------------------------
    # REST API methods
    # -------------------------------------------------------------------

    # Create or update expectations.
    # @param expectations [Array<Expectation>]
    # @return [Array<Expectation>]
    def upsert(*expectations)
      body = JSON.generate(expectations.map(&:to_h))
      status, response_body = request('PUT', '/mockserver/expectation', body)
      if status == 400
        raise Error, "Invalid expectation: #{response_body}"
      end

      if status >= 400
        raise Error, "Failed to upsert expectations (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |e| Expectation.from_hash(e) } if parsed.is_a?(Array)
      end
      expectations.to_a
    end

    # Create an OpenAPI expectation.
    # @param expectation [OpenAPIExpectation]
    # @return [nil]
    def open_api_expectation(expectation)
      body = JSON.generate(expectation.to_h)
      status, response_body = request('PUT', '/mockserver/openapi', body)
      if status >= 400
        raise Error, "Failed to create OpenAPI expectation (status=#{status}): #{response_body}"
      end

      nil
    end

    # Clear expectations and/or logs.
    # @param request [HttpRequest, nil]
    # @param type [String, nil] "EXPECTATIONS", "LOG", or "ALL"
    # @return [nil]
    def clear(request = nil, type: nil)
      query_params = {}
      query_params['type'] = type if type
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/clear', body, query_params.empty? ? nil : query_params
      )
      if status >= 400
        raise Error, "Failed to clear (status=#{status}): #{response_body}"
      end

      nil
    end

    # Clear by expectation ID.
    # @param expectation_id [String]
    # @param type [String, nil]
    # @return [nil]
    def clear_by_id(expectation_id, type: nil)
      query_params = {}
      query_params['type'] = type if type
      body = JSON.generate({ 'id' => expectation_id })
      status, response_body = do_request(
        'PUT', '/mockserver/clear', body, query_params.empty? ? nil : query_params
      )
      if status >= 400
        raise Error, "Failed to clear by id (status=#{status}): #{response_body}"
      end

      nil
    end

    # Reset all expectations and logs.
    # @return [nil]
    def reset
      status, response_body = request('PUT', '/mockserver/reset')
      if status >= 400
        raise Error, "Failed to reset (status=#{status}): #{response_body}"
      end

      nil
    ensure
      close
    end

    # Verify that a request was received.
    # @param request [HttpRequest]
    # @param times [VerificationTimes, nil]
    # @return [nil]
    # @raise [VerificationError] if verification fails (HTTP 406)
    def verify(request, times: nil)
      verification = Verification.new(http_request: request, times: times)
      body = JSON.generate(verification.to_h)
      status, response_body = do_request('PUT', '/mockserver/verify', body)
      if status == 406
        raise VerificationError, response_body
      end

      if status >= 400
        raise Error, "Failed to verify (status=#{status}): #{response_body}"
      end

      nil
    end

    # Verify that requests were received in sequence.
    # @param requests [Array<HttpRequest>]
    # @return [nil]
    # @raise [VerificationError] if verification fails (HTTP 406)
    def verify_sequence(*requests)
      verification = VerificationSequence.new(http_requests: requests.to_a)
      body = JSON.generate(verification.to_h)
      status, response_body = request('PUT', '/mockserver/verifySequence', body)
      if status == 406
        raise VerificationError, response_body
      end

      if status >= 400
        raise Error, "Failed to verify sequence (status=#{status}): #{response_body}"
      end

      nil
    end

    # Verify zero interactions.
    # @return [nil]
    def verify_zero_interactions
      verify(HttpRequest.new, times: VerificationTimes.new(at_most: 0))
    end

    # Retrieve recorded requests.
    # @param request [HttpRequest, nil]
    # @return [Array<HttpRequest>]
    def retrieve_recorded_requests(request: nil)
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'REQUESTS', 'format' => 'JSON' }
      )
      if status >= 400
        raise Error, "Failed to retrieve recorded requests (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |r| HttpRequest.from_hash(r) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve active expectations.
    # @param request [HttpRequest, nil]
    # @return [Array<Expectation>]
    def retrieve_active_expectations(request: nil)
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'ACTIVE_EXPECTATIONS', 'format' => 'JSON' }
      )
      if status >= 400
        raise Error, "Failed to retrieve active expectations (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |e| Expectation.from_hash(e) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve recorded expectations.
    # @param request [HttpRequest, nil]
    # @return [Array<Expectation>]
    def retrieve_recorded_expectations(request: nil)
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'RECORDED_EXPECTATIONS', 'format' => 'JSON' }
      )
      if status >= 400
        raise Error, "Failed to retrieve recorded expectations (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |e| Expectation.from_hash(e) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve recorded requests and responses.
    # @param request [HttpRequest, nil]
    # @return [Array<HttpRequestAndHttpResponse>]
    def retrieve_recorded_requests_and_responses(request: nil)
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'REQUEST_RESPONSES', 'format' => 'JSON' }
      )
      if status >= 400
        raise Error, "Failed to retrieve request/responses (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |rr| HttpRequestAndHttpResponse.from_hash(rr) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve log messages.
    # @param request [HttpRequest, nil]
    # @return [Array<String>]
    def retrieve_log_messages(request: nil)
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'LOGS' }
      )
      if status >= 400
        raise Error, "Failed to retrieve log messages (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        begin
          parsed = JSON.parse(response_body)
          return parsed if parsed.is_a?(Array)
        rescue JSON::ParserError
          return response_body.split("------------------------------------\n")
        end
      end
      []
    end

    # Bind additional ports.
    # @param ports [Array<Integer>]
    # @return [Array<Integer>]
    def bind(*ports)
      body = JSON.generate(Ports.new(ports: ports.flatten).to_h)
      status, response_body = request('PUT', '/mockserver/bind', body)
      if status >= 400
        raise Error, "Failed to bind ports (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return Ports.from_hash(parsed).ports
      end
      []
    end

    # Stop the MockServer instance.
    # @return [nil]
    def stop
      request('PUT', '/mockserver/stop')
      nil
    rescue ConnectionError
      nil
    ensure
      close
    end

    # Check if MockServer has started.
    # @param attempts [Integer]
    # @param timeout [Float] seconds between attempts
    # @return [Boolean]
    def has_started?(attempts: 10, timeout: 0.5)
      attempts.times do |i|
        begin
          status, = request('PUT', '/mockserver/status')
          return true if status == 200
        rescue ConnectionError
          # not yet started
        end
        sleep(timeout) if i < attempts - 1
      end
      false
    end

    alias has_started has_started?

    # -------------------------------------------------------------------
    # Fluent API
    # -------------------------------------------------------------------

    # Begin building an expectation via the fluent API.
    # @param request [HttpRequest]
    # @param times [Times, nil]
    # @param time_to_live [TimeToLive, nil]
    # @param priority [Integer, nil]
    # @return [ForwardChainExpectation]
    def when(request, times: nil, time_to_live: nil, priority: nil)
      expectation = Expectation.new(
        http_request: request,
        times: times,
        time_to_live: time_to_live,
        priority: priority
      )
      ForwardChainExpectation.new(self, expectation)
    end

    # -------------------------------------------------------------------
    # Callback methods
    # -------------------------------------------------------------------

    # Register a response callback via WebSocket.
    # @param request [HttpRequest]
    # @param callback [Proc]
    # @param times [Times, nil]
    # @param time_to_live [TimeToLive, nil]
    # @return [Array<Expectation>]
    def mock_with_callback(request, callback, times: nil, time_to_live: nil)
      client_id = register_websocket_callback('response', callback)
      expectation = Expectation.new(
        http_request: request,
        http_response_object_callback: HttpObjectCallback.new(client_id: client_id),
        times: times,
        time_to_live: time_to_live
      )
      upsert(expectation)
    end

    # Register a forward callback via WebSocket.
    # @param request [HttpRequest]
    # @param forward_callback [Proc]
    # @param response_callback [Proc, nil]
    # @param times [Times, nil]
    # @param time_to_live [TimeToLive, nil]
    # @return [Array<Expectation>]
    def mock_with_forward_callback(request, forward_callback, response_callback = nil,
                                    times: nil, time_to_live: nil)
      client_id = register_websocket_callback('forward', forward_callback, response_callback)
      obj_callback = HttpObjectCallback.new(client_id: client_id)
      obj_callback.response_callback = true if response_callback
      expectation = Expectation.new(
        http_request: request,
        http_forward_object_callback: obj_callback,
        times: times,
        time_to_live: time_to_live
      )
      upsert(expectation)
    end

    # Close all WebSocket connections.
    # @return [nil]
    def close
      @websocket_mutex.synchronize do
        @websocket_clients.each(&:close)
        @websocket_clients.clear
      end
      nil
    end

    private

    # @api private
    def register_websocket_callback(callback_type, callback_fn, forward_response_fn = nil)
      ws_client = WebSocketClient.new
      ws_client.connect(
        @host, @port,
        context_path: @context_path,
        secure: @secure,
        ca_cert_path: @ca_cert_path,
        tls_verify: @tls_verify
      )

      case callback_type
      when 'response'
        ws_client.register_response_callback(callback_fn)
      when 'forward'
        ws_client.register_forward_callback(callback_fn, forward_response_fn)
      end

      ws_client.listen
      @websocket_mutex.synchronize { @websocket_clients << ws_client }
      ws_client.client_id
    end

    # Perform an HTTP request with optional query parameters.
    # @api private
    def do_request(method, path, body = nil, query_params = nil)
      url = "#{@base_url}#{path}"
      if query_params && !query_params.empty?
        url = "#{url}?#{URI.encode_www_form(query_params)}"
      end

      uri = URI.parse(url)
      http = build_http(uri)

      req = build_request(method, uri, body)
      execute_request(http, req)
    end

    # Perform an HTTP request (no query params).
    # @api private
    def request(method, path, body = nil)
      do_request(method, path, body, nil)
    end

    # @api private
    def build_http(uri)
      http = Net::HTTP.new(uri.host, uri.port)
      http.read_timeout = HTTP_TIMEOUT
      http.open_timeout = HTTP_TIMEOUT

      if @secure
        http.use_ssl = true
        if @ca_cert_path
          http.ca_file = @ca_cert_path
          http.verify_mode = OpenSSL::SSL::VERIFY_PEER
        elsif !@tls_verify
          http.verify_mode = OpenSSL::SSL::VERIFY_NONE
        else
          http.verify_mode = OpenSSL::SSL::VERIFY_PEER
        end
      end

      http
    end

    # @api private
    def build_request(method, uri, body)
      request_path = uri.request_uri
      case method.upcase
      when 'PUT'
        req = Net::HTTP::Put.new(request_path)
      when 'GET'
        req = Net::HTTP::Get.new(request_path)
      when 'POST'
        req = Net::HTTP::Post.new(request_path)
      when 'DELETE'
        req = Net::HTTP::Delete.new(request_path)
      else
        req = Net::HTTP::Put.new(request_path)
      end
      req['Content-Type'] = 'application/json; charset=utf-8'
      req.body = body if body
      req
    end

    # @api private
    def execute_request(http, req)
      response = http.request(req)
      [response.code.to_i, response.body || '']
    rescue Net::OpenTimeout, Net::ReadTimeout => e
      raise ConnectionError, "Request to MockServer at #{@base_url} timed out: #{e.message}"
    rescue OpenSSL::SSL::SSLError => e
      raise ConnectionError, "TLS error connecting to MockServer at #{@base_url}: #{e.message}"
    rescue Errno::ECONNREFUSED, Errno::ECONNRESET, SocketError, IOError => e
      raise ConnectionError, "Failed to connect to MockServer at #{@base_url}: #{e.message}"
    end
  end
end
