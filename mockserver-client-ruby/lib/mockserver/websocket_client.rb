# frozen_string_literal: true

require 'json'
require 'securerandom'
require 'logger'
require 'openssl'
require 'timeout'

module MockServer
  WEB_SOCKET_CORRELATION_ID_HEADER_NAME = 'WebSocketCorrelationId'
  BREAKPOINT_ID_HEADER_NAME = 'X-MockServer-BreakpointId'
  CLIENT_REGISTRATION_ID_HEADER = 'X-CLIENT-REGISTRATION-ID'

  WEBSOCKET_PATH = '/_mockserver_callback_websocket'

  TYPE_HTTP_REQUEST              = 'org.mockserver.model.HttpRequest'
  TYPE_HTTP_RESPONSE             = 'org.mockserver.model.HttpResponse'
  TYPE_HTTP_REQUEST_AND_RESPONSE = 'org.mockserver.model.HttpRequestAndHttpResponse'
  TYPE_CLIENT_ID_DTO             = 'org.mockserver.serialization.model.WebSocketClientIdDTO'
  TYPE_ERROR_DTO                 = 'org.mockserver.serialization.model.WebSocketErrorDTO'
  TYPE_PAUSED_STREAM_FRAME_DTO   = 'org.mockserver.serialization.model.PausedStreamFrameDTO'
  TYPE_STREAM_FRAME_DECISION_DTO = 'org.mockserver.serialization.model.StreamFrameDecisionDTO'

  MAX_RECONNECT_ATTEMPTS = 3
  REGISTRATION_TIMEOUT   = 10

  def self.extract_correlation_id(request)
    return nil if request.headers.nil?

    request.headers.each do |header|
      if header.name == WEB_SOCKET_CORRELATION_ID_HEADER_NAME
        return header.values.first if header.values && !header.values.empty?
      end
    end
    nil
  end

  def self.extract_breakpoint_id(request)
    return nil if request.headers.nil?

    request.headers.each do |header|
      if header.name == BREAKPOINT_ID_HEADER_NAME
        return header.values.first if header.values && !header.values.empty?
      end
    end
    nil
  end

  def self.add_correlation_id_header(message, correlation_id)
    message.headers ||= []
    message.headers.each do |header|
      if header.name == WEB_SOCKET_CORRELATION_ID_HEADER_NAME
        header.values = [correlation_id]
        return message
      end
    end
    message.headers << KeyToMultiValue.new(
      name: WEB_SOCKET_CORRELATION_ID_HEADER_NAME,
      values: [correlation_id]
    )
    message
  end

  def self.build_ws_message(type_name, value_dict)
    JSON.generate({
      'type'  => type_name,
      'value' => JSON.generate(value_dict)
    })
  end

  def self.build_error_message(error_msg, correlation_id)
    JSON.generate({
      'type'  => TYPE_ERROR_DTO,
      'value' => JSON.generate({
        'message' => error_msg,
        'webSocketCorrelationId' => correlation_id
      })
    })
  end

  # @api private
  def self.clean_context_path(context_path)
    return '' if context_path.nil? || context_path.empty?
    return "/#{context_path}" unless context_path.start_with?('/')

    context_path
  end

  class WebSocketClient
    attr_reader :client_id

    def initialize
      @ws = nil
      @client_id = nil
      @response_callback = nil
      @forward_callback = nil
      @forward_response_callback = nil
      @stopped = false
      @connected = false
      @listen_thread = nil
      @host = ''
      @port = 0
      @context_path = ''
      @secure = false
      @ca_cert_path = nil
      @tls_verify = true
      @logger = Logger.new($stdout)
      @logger.progname = 'MockServer::WebSocketClient'
      @logger.level = Logger::WARN
      @registration_queue = nil
      # Per-breakpoint-id handlers for matcher-driven breakpoints
      @breakpoint_request_handlers = {}
      @breakpoint_response_handlers = {}
      @breakpoint_stream_frame_handlers = {}
    end

    def connected?
      @connected && !@stopped
    end

    # +authorization+, when given, is sent as the +Authorization+ header on every
    # (re)connect, so the upgrade passes control-plane authentication.
    def connect(host, port, context_path: '', secure: false,
                ca_cert_path: nil, client_id: nil, tls_verify: true, authorization: nil)
      @authorization = authorization
      @host = host
      @port = port
      @context_path = context_path
      @secure = secure
      @ca_cert_path = ca_cert_path
      @tls_verify = tls_verify

      registration_id = client_id || SecureRandom.uuid

      do_connect(registration_id)
      @client_id
    end

    def register_response_callback(callback_fn)
      @response_callback = callback_fn
    end

    def register_forward_callback(forward_fn, response_fn = nil)
      @forward_callback = forward_fn
      @forward_response_callback = response_fn
    end

    # Register a REQUEST-phase breakpoint handler keyed by breakpoint id.
    def set_breakpoint_request_handler(breakpoint_id, handler)
      @breakpoint_request_handlers[breakpoint_id] = handler if breakpoint_id && handler
    end

    # Register a RESPONSE-phase breakpoint handler keyed by breakpoint id.
    def set_breakpoint_response_handler(breakpoint_id, handler)
      @breakpoint_response_handlers[breakpoint_id] = handler if breakpoint_id && handler
    end

    # Register a stream-frame breakpoint handler keyed by breakpoint id.
    def set_breakpoint_stream_frame_handler(breakpoint_id, handler)
      @breakpoint_stream_frame_handlers[breakpoint_id] = handler if breakpoint_id && handler
    end

    # Remove all handlers for the given breakpoint id.
    def remove_breakpoint_handlers(breakpoint_id)
      return unless breakpoint_id

      @breakpoint_request_handlers.delete(breakpoint_id)
      @breakpoint_response_handlers.delete(breakpoint_id)
      @breakpoint_stream_frame_handlers.delete(breakpoint_id)
    end

    # Remove all breakpoint handlers.
    def clear_breakpoint_handlers
      @breakpoint_request_handlers.clear
      @breakpoint_response_handlers.clear
      @breakpoint_stream_frame_handlers.clear
    end

    def listen
      @listen_thread = Thread.new { listen_loop }
      @listen_thread.abort_on_exception = false
    end

    def close
      @stopped = true
      @connected = false
      @ws&.close if @ws
    rescue StandardError
      # ignore close errors
    ensure
      @ws = nil
      @listen_thread&.join(5) if @listen_thread && @listen_thread != Thread.current
    end

    private

    def do_connect(registration_id)
      require 'websocket-client-simple'

      scheme = @secure ? 'wss' : 'ws'
      path = MockServer.clean_context_path(@context_path) + WEBSOCKET_PATH
      uri = "#{scheme}://#{@host}:#{@port}#{path}"

      headers = { CLIENT_REGISTRATION_ID_HEADER => registration_id }
      headers['Authorization'] = @authorization if @authorization

      @registration_queue = Queue.new
      @connected = false
      ws_client = self

      opts = { headers: headers }

      if @secure
        if @ca_cert_path
          cert_store = OpenSSL::X509::Store.new
          cert_store.set_default_paths
          cert_store.add_file(@ca_cert_path)
          opts[:cert_store] = cert_store
          opts[:verify_mode] = OpenSSL::SSL::VERIFY_PEER
        elsif !@tls_verify
          opts[:verify_mode] = OpenSSL::SSL::VERIFY_NONE
        else
          opts[:verify_mode] = OpenSSL::SSL::VERIFY_PEER
        end
      end

      @ws = ::WebSocket::Client::Simple.connect(uri, **opts) do |ws|
        ws.on :message do |msg|
          ws_client.send(:handle_raw_message, msg.data)
        end
        ws.on :error do |e|
          ws_client.send(:handle_ws_error, e)
        end
        ws.on :close do |_e|
          ws_client.send(:handle_ws_close)
        end
        ws.on :open do
          ws_client.send(:handle_ws_open)
        end
      end

      result = nil
      begin
        Timeout.timeout(REGISTRATION_TIMEOUT) do
          result = @registration_queue.pop
        end
      rescue Timeout::Error
        @ws&.close rescue nil
        @ws = nil
        @connected = false
        raise WebSocketError,
              "WebSocket registration timed out after #{REGISTRATION_TIMEOUT}s " \
              "connecting to #{@host}:#{@port}"
      end

      if result.is_a?(Exception)
        raise result
      end

      @client_id = result
    end

    def handle_ws_open
      @connected = true
    end

    def handle_ws_close
      @connected = false
    end

    def handle_ws_error(error)
      @connected = false
      if @registration_queue
        ws_error = WebSocketError.new(
          "WebSocket connection error to #{@host}:#{@port}: #{error.message}"
        )
        @registration_queue.push(ws_error)
      end
    end

    def listen_loop
      reconnect_attempts = 0
      until @stopped
        sleep 0.1
        next if connected?

        break if @stopped

        reconnect_attempts += 1
        if reconnect_attempts > MAX_RECONNECT_ATTEMPTS
          @logger.error('Max reconnect attempts reached, giving up')
          break
        end

        @logger.warn("WebSocket disconnected, reconnecting (attempt #{reconnect_attempts}/#{MAX_RECONNECT_ATTEMPTS})")
        begin
          do_connect(@client_id || SecureRandom.uuid)
          reconnect_attempts = 0
        rescue StandardError => e
          @logger.error("Reconnection failed: #{e.message}")
          sleep [2**reconnect_attempts, 8].min
        end
      end
    end

    def handle_raw_message(raw_message)
      begin
        parsed = JSON.parse(raw_message)
      rescue JSON::ParserError => e
        @logger.warn("Received unparseable WebSocket message: #{e.message}")
        return
      end
      msg_type = parsed['type']
      msg_value = parsed['value']

      if msg_type == TYPE_CLIENT_ID_DTO
        value = JSON.parse(msg_value)
        @client_id = value['clientId']
        @registration_queue&.push(@client_id)
        return
      end

      if msg_type == TYPE_HTTP_REQUEST
        request = HttpRequest.from_hash(JSON.parse(msg_value))
        correlation_id = MockServer.extract_correlation_id(request)
        breakpoint_id = MockServer.extract_breakpoint_id(request)

        bp_handler = breakpoint_id ? @breakpoint_request_handlers[breakpoint_id] : nil
        if bp_handler
          handle_breakpoint_request(request, correlation_id, bp_handler)
        elsif @forward_callback
          handle_forward_request(request, correlation_id)
        elsif @response_callback
          handle_response_request(request, correlation_id)
        else
          @logger.warn("Received HttpRequest callback but no callback registered")
        end
        return
      end

      if msg_type == TYPE_HTTP_REQUEST_AND_RESPONSE
        req_and_resp = HttpRequestAndHttpResponse.from_hash(JSON.parse(msg_value))
        correlation_id = MockServer.extract_correlation_id(req_and_resp.http_request)
        breakpoint_id = MockServer.extract_breakpoint_id(req_and_resp.http_request)

        bp_handler = breakpoint_id ? @breakpoint_response_handlers[breakpoint_id] : nil
        if bp_handler
          handle_breakpoint_response(req_and_resp, correlation_id, bp_handler)
        elsif @forward_response_callback
          handle_forward_response(req_and_resp, correlation_id)
        else
          @logger.warn("Received HttpRequestAndHttpResponse callback but no forward_response_callback registered")
        end
        return
      end

      if msg_type == TYPE_PAUSED_STREAM_FRAME_DTO
        paused_frame = JSON.parse(msg_value)
        handle_breakpoint_stream_frame(paused_frame)
        return
      end

      @logger.warn("Received unhandled WebSocket message type: #{msg_type}")
    end

    def handle_response_request(request, correlation_id)
      result = @response_callback.call(request)
      unless result.is_a?(HttpResponse)
        raise CallbackError, "Response callback must return HttpResponse, got #{result.class}"
      end

      MockServer.add_correlation_id_header(result, correlation_id) if correlation_id
      msg = MockServer.build_ws_message(TYPE_HTTP_RESPONSE, result.to_h)
      @ws.send(msg)
    rescue StandardError => exc
      @logger.error("Error in response callback: #{exc.message}")
      if correlation_id
        error_msg = MockServer.build_error_message(exc.message, correlation_id)
        @ws.send(error_msg)
      end
    end

    def handle_forward_request(request, correlation_id)
      result = @forward_callback.call(request)
      unless result.is_a?(HttpRequest)
        raise CallbackError, "Forward callback must return HttpRequest, got #{result.class}"
      end

      MockServer.add_correlation_id_header(result, correlation_id) if correlation_id
      msg = MockServer.build_ws_message(TYPE_HTTP_REQUEST, result.to_h)
      @ws.send(msg)
    rescue StandardError => exc
      @logger.error("Error in forward callback: #{exc.message}")
      if correlation_id
        error_msg = MockServer.build_error_message(exc.message, correlation_id)
        @ws.send(error_msg)
      end
    end

    def handle_forward_response(req_and_resp, correlation_id)
      result = @forward_response_callback.call(
        req_and_resp.http_request,
        req_and_resp.http_response
      )
      unless result.is_a?(HttpResponse)
        raise CallbackError, "Forward response callback must return HttpResponse, got #{result.class}"
      end

      MockServer.add_correlation_id_header(result, correlation_id) if correlation_id
      msg = MockServer.build_ws_message(TYPE_HTTP_RESPONSE, result.to_h)
      @ws.send(msg)
    rescue StandardError => exc
      @logger.error("Error in forward response callback: #{exc.message}")
      if correlation_id
        error_msg = MockServer.build_error_message(exc.message, correlation_id)
        @ws.send(error_msg)
      end
    end

    def handle_breakpoint_request(request, correlation_id, handler)
      result = handler.call(request)
      result = request if result.nil? # auto-continue

      MockServer.add_correlation_id_header(result, correlation_id) if correlation_id

      type_name = result.is_a?(HttpResponse) ? TYPE_HTTP_RESPONSE : TYPE_HTTP_REQUEST
      msg = MockServer.build_ws_message(type_name, result.to_h)
      @ws.send(msg)
    rescue StandardError => exc
      @logger.error("Error in breakpoint request handler, auto-continuing: #{exc.message}")
      MockServer.add_correlation_id_header(request, correlation_id) if correlation_id
      msg = MockServer.build_ws_message(TYPE_HTTP_REQUEST, request.to_h)
      @ws.send(msg)
    end

    def handle_breakpoint_response(req_and_resp, correlation_id, handler)
      result = handler.call(req_and_resp.http_request, req_and_resp.http_response)
      result = req_and_resp.http_response if result.nil? # auto-continue

      unless result.is_a?(HttpResponse)
        raise CallbackError, "Breakpoint response handler must return HttpResponse, got #{result.class}"
      end

      MockServer.add_correlation_id_header(result, correlation_id) if correlation_id
      msg = MockServer.build_ws_message(TYPE_HTTP_RESPONSE, result.to_h)
      @ws.send(msg)
    rescue StandardError => exc
      @logger.error("Error in breakpoint response handler, auto-continuing: #{exc.message}")
      resp = req_and_resp.http_response || HttpResponse.new
      MockServer.add_correlation_id_header(resp, correlation_id) if correlation_id
      msg = MockServer.build_ws_message(TYPE_HTTP_RESPONSE, resp.to_h)
      @ws.send(msg)
    end

    def handle_breakpoint_stream_frame(paused_frame)
      breakpoint_id = paused_frame['breakpointId']
      correlation_id = paused_frame['correlationId'] || ''
      handler = breakpoint_id ? @breakpoint_stream_frame_handlers[breakpoint_id] : nil

      decision = if handler
                   begin
                     result = handler.call(paused_frame)
                     if result.nil?
                       { 'correlationId' => correlation_id, 'action' => 'CONTINUE' }
                     else
                       result['correlationId'] = correlation_id # ensure echoed
                       result
                     end
                   rescue StandardError => exc
                     @logger.error("Error in breakpoint stream frame handler, auto-continuing: #{exc.message}")
                     { 'correlationId' => correlation_id, 'action' => 'CONTINUE' }
                   end
                 else
                   { 'correlationId' => correlation_id, 'action' => 'CONTINUE' }
                 end

      msg = JSON.generate({
        'type'  => TYPE_STREAM_FRAME_DECISION_DTO,
        'value' => JSON.generate(decision)
      })
      @ws.send(msg)
    end
  end
end
