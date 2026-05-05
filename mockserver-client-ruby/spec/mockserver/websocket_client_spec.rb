# frozen_string_literal: true

RSpec.describe 'MockServer WebSocket helpers' do
  # -------------------------------------------------------------------
  # Constants
  # -------------------------------------------------------------------
  describe 'constants' do
    it 'defines TYPE_HTTP_REQUEST' do
      expect(MockServer::TYPE_HTTP_REQUEST).to eq('org.mockserver.model.HttpRequest')
    end

    it 'defines TYPE_HTTP_RESPONSE' do
      expect(MockServer::TYPE_HTTP_RESPONSE).to eq('org.mockserver.model.HttpResponse')
    end

    it 'defines TYPE_HTTP_REQUEST_AND_RESPONSE' do
      expect(MockServer::TYPE_HTTP_REQUEST_AND_RESPONSE).to eq('org.mockserver.model.HttpRequestAndHttpResponse')
    end

    it 'defines TYPE_CLIENT_ID_DTO' do
      expect(MockServer::TYPE_CLIENT_ID_DTO).to eq('org.mockserver.serialization.model.WebSocketClientIdDTO')
    end

    it 'defines TYPE_ERROR_DTO' do
      expect(MockServer::TYPE_ERROR_DTO).to eq('org.mockserver.serialization.model.WebSocketErrorDTO')
    end

    it 'defines WEBSOCKET_PATH' do
      expect(MockServer::WEBSOCKET_PATH).to eq('/_mockserver_callback_websocket')
    end

    it 'defines CLIENT_REGISTRATION_ID_HEADER' do
      expect(MockServer::CLIENT_REGISTRATION_ID_HEADER).to eq('X-CLIENT-REGISTRATION-ID')
    end

    it 'defines WEB_SOCKET_CORRELATION_ID_HEADER_NAME' do
      expect(MockServer::WEB_SOCKET_CORRELATION_ID_HEADER_NAME).to eq('WebSocketCorrelationId')
    end

    it 'defines MAX_RECONNECT_ATTEMPTS' do
      expect(MockServer::MAX_RECONNECT_ATTEMPTS).to eq(3)
    end
  end

  # -------------------------------------------------------------------
  # extract_correlation_id
  # -------------------------------------------------------------------
  describe 'MockServer.extract_correlation_id' do
    it 'returns nil when headers are nil' do
      req = MockServer::HttpRequest.new
      expect(MockServer.extract_correlation_id(req)).to be_nil
    end

    it 'returns nil when no matching header' do
      req = MockServer::HttpRequest.new
      req.with_header('Content-Type', 'application/json')
      expect(MockServer.extract_correlation_id(req)).to be_nil
    end

    it 'returns correlation ID from header' do
      req = MockServer::HttpRequest.new
      req.headers = [
        MockServer::KeyToMultiValue.new(name: 'WebSocketCorrelationId', values: ['abc-123'])
      ]
      expect(MockServer.extract_correlation_id(req)).to eq('abc-123')
    end

    it 'returns first value when multiple values exist' do
      req = MockServer::HttpRequest.new
      req.headers = [
        MockServer::KeyToMultiValue.new(name: 'WebSocketCorrelationId', values: ['first', 'second'])
      ]
      expect(MockServer.extract_correlation_id(req)).to eq('first')
    end

    it 'returns nil when values are empty' do
      req = MockServer::HttpRequest.new
      req.headers = [
        MockServer::KeyToMultiValue.new(name: 'WebSocketCorrelationId', values: [])
      ]
      expect(MockServer.extract_correlation_id(req)).to be_nil
    end
  end

  # -------------------------------------------------------------------
  # add_correlation_id_header
  # -------------------------------------------------------------------
  describe 'MockServer.add_correlation_id_header' do
    it 'adds header when none exist' do
      resp = MockServer::HttpResponse.new
      result = MockServer.add_correlation_id_header(resp, 'corr-1')

      expect(result).to be(resp)
      expect(resp.headers.length).to eq(1)
      expect(resp.headers[0].name).to eq('WebSocketCorrelationId')
      expect(resp.headers[0].values).to eq(['corr-1'])
    end

    it 'updates existing correlation ID header' do
      resp = MockServer::HttpResponse.new
      resp.headers = [
        MockServer::KeyToMultiValue.new(name: 'WebSocketCorrelationId', values: ['old-id']),
        MockServer::KeyToMultiValue.new(name: 'Content-Type', values: ['text/plain'])
      ]
      MockServer.add_correlation_id_header(resp, 'new-id')

      expect(resp.headers.length).to eq(2)
      corr_header = resp.headers.find { |h| h.name == 'WebSocketCorrelationId' }
      expect(corr_header.values).to eq(['new-id'])
    end

    it 'appends header when other headers exist but no correlation ID' do
      resp = MockServer::HttpResponse.new
      resp.headers = [
        MockServer::KeyToMultiValue.new(name: 'Content-Type', values: ['text/plain'])
      ]
      MockServer.add_correlation_id_header(resp, 'corr-2')

      expect(resp.headers.length).to eq(2)
    end

    it 'initializes headers array when nil' do
      req = MockServer::HttpRequest.new
      expect(req.headers).to be_nil

      MockServer.add_correlation_id_header(req, 'corr-3')
      expect(req.headers).not_to be_nil
      expect(req.headers.length).to eq(1)
    end
  end

  # -------------------------------------------------------------------
  # build_ws_message
  # -------------------------------------------------------------------
  describe 'MockServer.build_ws_message' do
    it 'builds double-encoded JSON message' do
      result = MockServer.build_ws_message(
        MockServer::TYPE_HTTP_RESPONSE,
        { 'statusCode' => 200, 'body' => 'ok' }
      )
      parsed = JSON.parse(result)
      expect(parsed['type']).to eq(MockServer::TYPE_HTTP_RESPONSE)

      # value should be a JSON string (double-encoded)
      inner = JSON.parse(parsed['value'])
      expect(inner['statusCode']).to eq(200)
      expect(inner['body']).to eq('ok')
    end

    it 'returns a valid JSON string' do
      result = MockServer.build_ws_message('test.Type', { 'key' => 'val' })
      expect { JSON.parse(result) }.not_to raise_error
    end
  end

  # -------------------------------------------------------------------
  # build_error_message
  # -------------------------------------------------------------------
  describe 'MockServer.build_error_message' do
    it 'builds error message with type and double-encoded value' do
      result = MockServer.build_error_message('Something went wrong', 'corr-123')
      parsed = JSON.parse(result)
      expect(parsed['type']).to eq(MockServer::TYPE_ERROR_DTO)

      inner = JSON.parse(parsed['value'])
      expect(inner['message']).to eq('Something went wrong')
      expect(inner['webSocketCorrelationId']).to eq('corr-123')
    end
  end

  # -------------------------------------------------------------------
  # clean_context_path
  # -------------------------------------------------------------------
  describe 'MockServer.clean_context_path' do
    it 'returns empty string for nil' do
      expect(MockServer.clean_context_path(nil)).to eq('')
    end

    it 'returns empty string for empty string' do
      expect(MockServer.clean_context_path('')).to eq('')
    end

    it 'adds leading slash when missing' do
      expect(MockServer.clean_context_path('ctx')).to eq('/ctx')
    end

    it 'returns as-is when leading slash present' do
      expect(MockServer.clean_context_path('/ctx')).to eq('/ctx')
    end
  end

  # -------------------------------------------------------------------
  # WebSocketClient
  # -------------------------------------------------------------------
  describe 'MockServer::REGISTRATION_TIMEOUT' do
    it 'defines REGISTRATION_TIMEOUT' do
      expect(MockServer::REGISTRATION_TIMEOUT).to eq(10)
    end
  end

  describe MockServer::WebSocketClient do
    it 'initializes with nil client_id' do
      ws = MockServer::WebSocketClient.new
      expect(ws.client_id).to be_nil
    end

    it 'is not connected by default' do
      ws = MockServer::WebSocketClient.new
      expect(ws.connected?).to eq(false)
    end

    describe '#register_response_callback' do
      it 'accepts a proc' do
        ws = MockServer::WebSocketClient.new
        callback = ->(req) { MockServer::HttpResponse.new(status_code: 200) }
        expect { ws.register_response_callback(callback) }.not_to raise_error
      end
    end

    describe '#register_forward_callback' do
      it 'accepts forward and response procs' do
        ws = MockServer::WebSocketClient.new
        forward_fn = ->(req) { req }
        response_fn = ->(req, resp) { resp }
        expect { ws.register_forward_callback(forward_fn, response_fn) }.not_to raise_error
      end
    end

    describe '#close' do
      it 'sets connected? to false' do
        ws = MockServer::WebSocketClient.new
        ws.close
        expect(ws.connected?).to eq(false)
      end
    end

    describe 'message handling' do
      let(:ws_client) { MockServer::WebSocketClient.new }

      it 'handles registration message and sets client_id' do
        queue = Queue.new
        ws_client.instance_variable_set(:@registration_queue, queue)

        registration_msg = JSON.generate({
          'type' => MockServer::TYPE_CLIENT_ID_DTO,
          'value' => JSON.generate({ 'clientId' => 'test-client-id' })
        })

        ws_client.send(:handle_raw_message, registration_msg)

        expect(ws_client.client_id).to eq('test-client-id')
        expect(queue.pop(true)).to eq('test-client-id')
      end

      it 'calls response callback for HttpRequest messages' do
        response = MockServer::HttpResponse.new(status_code: 200)
        callback_called = false
        ws_client.register_response_callback(->(req) {
          callback_called = true
          response
        })

        fake_ws = double('ws')
        allow(fake_ws).to receive(:send)
        ws_client.instance_variable_set(:@ws, fake_ws)

        request_msg = JSON.generate({
          'type' => MockServer::TYPE_HTTP_REQUEST,
          'value' => JSON.generate({
            'method' => 'GET',
            'path' => '/test',
            'headers' => [{
              'name' => 'WebSocketCorrelationId',
              'values' => ['corr-123']
            }]
          })
        })

        ws_client.send(:handle_raw_message, request_msg)

        expect(callback_called).to eq(true)
        expect(fake_ws).to have_received(:send) do |msg|
          parsed = JSON.parse(msg)
          expect(parsed['type']).to eq(MockServer::TYPE_HTTP_RESPONSE)
          inner = JSON.parse(parsed['value'])
          expect(inner['statusCode']).to eq(200)
        end
      end

      it 'calls forward callback for HttpRequest messages' do
        forwarded_request = MockServer::HttpRequest.new(path: '/forwarded')
        ws_client.register_forward_callback(->(req) { forwarded_request })

        fake_ws = double('ws')
        allow(fake_ws).to receive(:send)
        ws_client.instance_variable_set(:@ws, fake_ws)

        request_msg = JSON.generate({
          'type' => MockServer::TYPE_HTTP_REQUEST,
          'value' => JSON.generate({
            'method' => 'GET',
            'path' => '/test',
            'headers' => [{
              'name' => 'WebSocketCorrelationId',
              'values' => ['corr-456']
            }]
          })
        })

        ws_client.send(:handle_raw_message, request_msg)

        expect(fake_ws).to have_received(:send) do |msg|
          parsed = JSON.parse(msg)
          expect(parsed['type']).to eq(MockServer::TYPE_HTTP_REQUEST)
          inner = JSON.parse(parsed['value'])
          expect(inner['path']).to eq('/forwarded')
        end
      end

      it 'calls forward_response callback for HttpRequestAndHttpResponse messages' do
        modified_response = MockServer::HttpResponse.new(status_code: 201)
        ws_client.register_forward_callback(
          ->(req) { req },
          ->(req, resp) { modified_response }
        )

        fake_ws = double('ws')
        allow(fake_ws).to receive(:send)
        ws_client.instance_variable_set(:@ws, fake_ws)

        msg = JSON.generate({
          'type' => MockServer::TYPE_HTTP_REQUEST_AND_RESPONSE,
          'value' => JSON.generate({
            'httpRequest' => {
              'method' => 'GET',
              'path' => '/test',
              'headers' => [{
                'name' => 'WebSocketCorrelationId',
                'values' => ['corr-789']
              }]
            },
            'httpResponse' => { 'statusCode' => 200 }
          })
        })

        ws_client.send(:handle_raw_message, msg)

        expect(fake_ws).to have_received(:send) do |sent_msg|
          parsed = JSON.parse(sent_msg)
          expect(parsed['type']).to eq(MockServer::TYPE_HTTP_RESPONSE)
          inner = JSON.parse(parsed['value'])
          expect(inner['statusCode']).to eq(201)
        end
      end

      it 'sends error message when callback raises' do
        ws_client.register_response_callback(->(req) {
          raise 'callback error'
        })

        fake_ws = double('ws')
        allow(fake_ws).to receive(:send)
        ws_client.instance_variable_set(:@ws, fake_ws)

        request_msg = JSON.generate({
          'type' => MockServer::TYPE_HTTP_REQUEST,
          'value' => JSON.generate({
            'path' => '/test',
            'headers' => [{
              'name' => 'WebSocketCorrelationId',
              'values' => ['corr-err']
            }]
          })
        })

        ws_client.send(:handle_raw_message, request_msg)

        expect(fake_ws).to have_received(:send) do |msg|
          parsed = JSON.parse(msg)
          expect(parsed['type']).to eq(MockServer::TYPE_ERROR_DTO)
          inner = JSON.parse(parsed['value'])
          expect(inner['message']).to include('callback error')
          expect(inner['webSocketCorrelationId']).to eq('corr-err')
        end
      end

      it 'sends error when response callback returns wrong type' do
        ws_client.register_response_callback(->(req) { 'not a response' })

        fake_ws = double('ws')
        allow(fake_ws).to receive(:send)
        ws_client.instance_variable_set(:@ws, fake_ws)

        request_msg = JSON.generate({
          'type' => MockServer::TYPE_HTTP_REQUEST,
          'value' => JSON.generate({
            'path' => '/test',
            'headers' => [{
              'name' => 'WebSocketCorrelationId',
              'values' => ['corr-type']
            }]
          })
        })

        ws_client.send(:handle_raw_message, request_msg)

        expect(fake_ws).to have_received(:send) do |msg|
          parsed = JSON.parse(msg)
          expect(parsed['type']).to eq(MockServer::TYPE_ERROR_DTO)
          inner = JSON.parse(parsed['value'])
          expect(inner['message']).to include('HttpResponse')
        end
      end

      it 'handles malformed JSON gracefully' do
        logger = instance_double(Logger)
        allow(logger).to receive(:warn)
        allow(logger).to receive(:progname=)
        allow(logger).to receive(:level=)
        ws_client.instance_variable_set(:@logger, logger)

        ws_client.send(:handle_raw_message, 'not valid json')

        expect(logger).to have_received(:warn).with(/unparseable/)
      end

      it 'logs warning when no callback registered for HttpRequest' do
        logger = instance_double(Logger)
        allow(logger).to receive(:warn)
        allow(logger).to receive(:progname=)
        allow(logger).to receive(:level=)
        ws_client.instance_variable_set(:@logger, logger)

        request_msg = JSON.generate({
          'type' => MockServer::TYPE_HTTP_REQUEST,
          'value' => JSON.generate({ 'path' => '/test' })
        })

        ws_client.send(:handle_raw_message, request_msg)

        expect(logger).to have_received(:warn).with(/no callback registered/)
      end

      it 'logs warning when no forward_response_callback registered for HttpRequestAndHttpResponse' do
        logger = instance_double(Logger)
        allow(logger).to receive(:warn)
        allow(logger).to receive(:progname=)
        allow(logger).to receive(:level=)
        ws_client.instance_variable_set(:@logger, logger)

        msg = JSON.generate({
          'type' => MockServer::TYPE_HTTP_REQUEST_AND_RESPONSE,
          'value' => JSON.generate({
            'httpRequest' => { 'path' => '/test' },
            'httpResponse' => { 'statusCode' => 200 }
          })
        })

        ws_client.send(:handle_raw_message, msg)

        expect(logger).to have_received(:warn).with(/no forward_response_callback registered/)
      end
    end

    describe 'TLS options' do
      it 'passes verify_mode VERIFY_NONE when tls_verify is false' do
        ws_client = MockServer::WebSocketClient.new
        ws_client.instance_variable_set(:@secure, true)
        ws_client.instance_variable_set(:@tls_verify, false)
        ws_client.instance_variable_set(:@host, 'localhost')
        ws_client.instance_variable_set(:@port, 1080)
        ws_client.instance_variable_set(:@context_path, '')

        require 'websocket-client-simple'
        allow(::WebSocket::Client::Simple).to receive(:connect) do |_uri, **opts|
          expect(opts[:verify_mode]).to eq(OpenSSL::SSL::VERIFY_NONE)
          fake_ws = double('ws')
          allow(fake_ws).to receive(:on)
          allow(fake_ws).to receive(:close)
          fake_ws
        end

        expect {
          ws_client.send(:do_connect, 'test-id')
        }.to raise_error(MockServer::WebSocketError, /timed out/)
      end

      it 'passes cert_store when ca_cert_path is provided' do
        ws_client = MockServer::WebSocketClient.new
        ws_client.instance_variable_set(:@secure, true)
        ws_client.instance_variable_set(:@tls_verify, true)
        ws_client.instance_variable_set(:@ca_cert_path, '/path/to/ca.pem')
        ws_client.instance_variable_set(:@host, 'localhost')
        ws_client.instance_variable_set(:@port, 1080)
        ws_client.instance_variable_set(:@context_path, '')

        require 'websocket-client-simple'

        cert_store = OpenSSL::X509::Store.new
        allow(OpenSSL::X509::Store).to receive(:new).and_return(cert_store)
        allow(cert_store).to receive(:set_default_paths)
        allow(cert_store).to receive(:add_file)

        allow(::WebSocket::Client::Simple).to receive(:connect) do |_uri, **opts|
          expect(opts[:verify_mode]).to eq(OpenSSL::SSL::VERIFY_PEER)
          expect(opts[:cert_store]).to eq(cert_store)
          fake_ws = double('ws')
          allow(fake_ws).to receive(:on)
          allow(fake_ws).to receive(:close)
          fake_ws
        end

        expect {
          ws_client.send(:do_connect, 'test-id')
        }.to raise_error(MockServer::WebSocketError, /timed out/)
        expect(cert_store).to have_received(:add_file).with('/path/to/ca.pem')
      end
    end

    describe 'error handling' do
      it 'pushes WebSocketError to registration queue on error' do
        ws_client = MockServer::WebSocketClient.new
        ws_client.instance_variable_set(:@host, 'localhost')
        ws_client.instance_variable_set(:@port, 1080)
        queue = Queue.new
        ws_client.instance_variable_set(:@registration_queue, queue)

        ws_client.send(:handle_ws_error, StandardError.new('connection failed'))

        expect(ws_client.connected?).to eq(false)
        result = queue.pop(true)
        expect(result).to be_a(MockServer::WebSocketError)
        expect(result.message).to include('connection failed')
      end

      it 'sets connected to false on close' do
        ws_client = MockServer::WebSocketClient.new
        ws_client.instance_variable_set(:@connected, true)

        ws_client.send(:handle_ws_close)

        expect(ws_client.connected?).to eq(false)
      end
    end
  end
end
