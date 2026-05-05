# frozen_string_literal: true

RSpec.describe MockServer::Client do
  let(:host) { 'localhost' }
  let(:port) { 1080 }
  let(:base_url) { "http://#{host}:#{port}" }
  let(:client) { MockServer::Client.new(host, port) }

  after { client.close }

  # -------------------------------------------------------------------
  # upsert
  # -------------------------------------------------------------------
  describe '#upsert' do
    it 'sends PUT to /mockserver/expectation and returns expectations' do
      response_body = [{ 'id' => 'exp-1', 'httpRequest' => { 'path' => '/test' } }]
      stub_request(:put, "#{base_url}/mockserver/expectation")
        .to_return(status: 201, body: JSON.generate(response_body))

      exp = MockServer::Expectation.new(http_request: MockServer::HttpRequest.new(path: '/test'))
      result = client.upsert(exp)

      expect(result).to be_an(Array)
      expect(result.length).to eq(1)
      expect(result[0]).to be_a(MockServer::Expectation)
      expect(result[0].id).to eq('exp-1')
    end

    it 'returns input expectations when response is empty' do
      stub_request(:put, "#{base_url}/mockserver/expectation")
        .to_return(status: 201, body: '')

      exp = MockServer::Expectation.new(http_request: MockServer::HttpRequest.new(path: '/test'))
      result = client.upsert(exp)
      expect(result.length).to eq(1)
    end

    it 'raises Error on 400 response' do
      stub_request(:put, "#{base_url}/mockserver/expectation")
        .to_return(status: 400, body: 'invalid')

      exp = MockServer::Expectation.new
      expect { client.upsert(exp) }.to raise_error(MockServer::Error, /Invalid expectation/)
    end

    it 'raises Error on 500 response' do
      stub_request(:put, "#{base_url}/mockserver/expectation")
        .to_return(status: 500, body: 'server error')

      exp = MockServer::Expectation.new
      expect { client.upsert(exp) }.to raise_error(MockServer::Error, /Failed to upsert/)
    end

    it 'sends correct JSON body' do
      stub_request(:put, "#{base_url}/mockserver/expectation")
        .to_return(status: 201, body: '[]')

      exp = MockServer::Expectation.new(
        http_request: MockServer::HttpRequest.new(method: 'GET', path: '/api'),
        http_response: MockServer::HttpResponse.new(status_code: 200)
      )
      client.upsert(exp)

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/expectation")
        .with { |req|
          parsed = JSON.parse(req.body)
          parsed.is_a?(Array) &&
            parsed.length == 1 &&
            parsed[0]['httpRequest']['method'] == 'GET' &&
            parsed[0]['httpResponse']['statusCode'] == 200
        }
    end
  end

  # -------------------------------------------------------------------
  # open_api_expectation
  # -------------------------------------------------------------------
  describe '#open_api_expectation' do
    it 'sends PUT to /mockserver/openapi' do
      stub_request(:put, "#{base_url}/mockserver/openapi")
        .to_return(status: 201, body: '')

      oae = MockServer::OpenAPIExpectation.new(spec_url_or_payload: 'http://spec.io')
      result = client.open_api_expectation(oae)
      expect(result).to be_nil
    end

    it 'raises Error on failure' do
      stub_request(:put, "#{base_url}/mockserver/openapi")
        .to_return(status: 400, body: 'bad request')

      oae = MockServer::OpenAPIExpectation.new
      expect { client.open_api_expectation(oae) }.to raise_error(MockServer::Error)
    end
  end

  # -------------------------------------------------------------------
  # clear
  # -------------------------------------------------------------------
  describe '#clear' do
    it 'sends PUT to /mockserver/clear' do
      stub_request(:put, "#{base_url}/mockserver/clear")
        .to_return(status: 200, body: '')

      result = client.clear
      expect(result).to be_nil
    end

    it 'includes type query parameter' do
      stub_request(:put, "#{base_url}/mockserver/clear?type=EXPECTATIONS")
        .to_return(status: 200, body: '')

      client.clear(type: 'EXPECTATIONS')
      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/clear?type=EXPECTATIONS")
    end

    it 'includes request body' do
      stub_request(:put, "#{base_url}/mockserver/clear")
        .to_return(status: 200, body: '')

      req = MockServer::HttpRequest.new(path: '/test')
      client.clear(req)

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/clear")
        .with { |r| JSON.parse(r.body)['path'] == '/test' }
    end

    it 'accepts request as positional argument' do
      stub_request(:put, "#{base_url}/mockserver/clear")
        .to_return(status: 200, body: '')

      req = MockServer::HttpRequest.new(path: '/positional')
      client.clear(req)

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/clear")
        .with { |r| JSON.parse(r.body)['path'] == '/positional' }
    end

    it 'raises Error on failure' do
      stub_request(:put, "#{base_url}/mockserver/clear")
        .to_return(status: 500, body: 'error')

      expect { client.clear }.to raise_error(MockServer::Error)
    end
  end

  # -------------------------------------------------------------------
  # clear_by_id
  # -------------------------------------------------------------------
  describe '#clear_by_id' do
    it 'sends PUT to /mockserver/clear with id body' do
      stub_request(:put, "#{base_url}/mockserver/clear")
        .to_return(status: 200, body: '')

      client.clear_by_id('exp-1')

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/clear")
        .with { |r| JSON.parse(r.body)['id'] == 'exp-1' }
    end

    it 'includes type query parameter' do
      stub_request(:put, "#{base_url}/mockserver/clear?type=LOG")
        .to_return(status: 200, body: '')

      client.clear_by_id('exp-1', type: 'LOG')
    end
  end

  # -------------------------------------------------------------------
  # reset
  # -------------------------------------------------------------------
  describe '#reset' do
    it 'sends PUT to /mockserver/reset' do
      stub_request(:put, "#{base_url}/mockserver/reset")
        .to_return(status: 200, body: '')

      result = client.reset
      expect(result).to be_nil
    end

    it 'raises Error on failure' do
      stub_request(:put, "#{base_url}/mockserver/reset")
        .to_return(status: 500, body: 'error')

      expect { client.reset }.to raise_error(MockServer::Error)
    end

    it 'closes WebSocket connections on successful reset' do
      stub_request(:put, "#{base_url}/mockserver/reset")
        .to_return(status: 200, body: '')

      ws_client = instance_double(MockServer::WebSocketClient)
      allow(ws_client).to receive(:close)
      client.instance_variable_get(:@websocket_clients) << ws_client

      client.reset

      expect(ws_client).to have_received(:close)
      expect(client.instance_variable_get(:@websocket_clients)).to be_empty
    end

    it 'closes WebSocket connections even on failure' do
      stub_request(:put, "#{base_url}/mockserver/reset")
        .to_return(status: 500, body: 'error')

      ws_client = instance_double(MockServer::WebSocketClient)
      allow(ws_client).to receive(:close)
      client.instance_variable_get(:@websocket_clients) << ws_client

      expect { client.reset }.to raise_error(MockServer::Error)
      expect(ws_client).to have_received(:close)
    end
  end

  # -------------------------------------------------------------------
  # verify
  # -------------------------------------------------------------------
  describe '#verify' do
    it 'sends PUT to /mockserver/verify' do
      stub_request(:put, "#{base_url}/mockserver/verify")
        .to_return(status: 202, body: '')

      req = MockServer::HttpRequest.new(path: '/test')
      result = client.verify(req)
      expect(result).to be_nil
    end

    it 'sends verification times' do
      stub_request(:put, "#{base_url}/mockserver/verify")
        .to_return(status: 202, body: '')

      req = MockServer::HttpRequest.new(path: '/test')
      client.verify(req, times: MockServer::VerificationTimes.at_least(1))

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/verify")
        .with { |r|
          parsed = JSON.parse(r.body)
          parsed['times']['atLeast'] == 1
        }
    end

    it 'raises VerificationError on 406' do
      stub_request(:put, "#{base_url}/mockserver/verify")
        .to_return(status: 406, body: 'Request not found')

      req = MockServer::HttpRequest.new(path: '/test')
      expect { client.verify(req) }.to raise_error(MockServer::VerificationError, 'Request not found')
    end

    it 'raises Error on other errors' do
      stub_request(:put, "#{base_url}/mockserver/verify")
        .to_return(status: 500, body: 'server error')

      req = MockServer::HttpRequest.new(path: '/test')
      expect { client.verify(req) }.to raise_error(MockServer::Error, /Failed to verify/)
    end
  end

  # -------------------------------------------------------------------
  # verify_sequence
  # -------------------------------------------------------------------
  describe '#verify_sequence' do
    it 'sends PUT to /mockserver/verifySequence' do
      stub_request(:put, "#{base_url}/mockserver/verifySequence")
        .to_return(status: 202, body: '')

      req1 = MockServer::HttpRequest.new(path: '/a')
      req2 = MockServer::HttpRequest.new(path: '/b')
      result = client.verify_sequence(req1, req2)
      expect(result).to be_nil
    end

    it 'raises VerificationError on 406' do
      stub_request(:put, "#{base_url}/mockserver/verifySequence")
        .to_return(status: 406, body: 'Sequence not found')

      req = MockServer::HttpRequest.new(path: '/a')
      expect { client.verify_sequence(req) }.to raise_error(MockServer::VerificationError)
    end
  end

  # -------------------------------------------------------------------
  # verify_zero_interactions
  # -------------------------------------------------------------------
  describe '#verify_zero_interactions' do
    it 'verifies with at_most 0' do
      stub_request(:put, "#{base_url}/mockserver/verify")
        .to_return(status: 202, body: '')

      client.verify_zero_interactions

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/verify")
        .with { |r|
          parsed = JSON.parse(r.body)
          parsed['times']['atMost'] == 0
        }
    end
  end

  # -------------------------------------------------------------------
  # retrieve_recorded_requests
  # -------------------------------------------------------------------
  describe '#retrieve_recorded_requests' do
    it 'returns array of HttpRequest' do
      response_body = [{ 'method' => 'GET', 'path' => '/test' }]
      stub_request(:put, "#{base_url}/mockserver/retrieve?format=JSON&type=REQUESTS")
        .to_return(status: 200, body: JSON.generate(response_body))

      result = client.retrieve_recorded_requests
      expect(result.length).to eq(1)
      expect(result[0]).to be_a(MockServer::HttpRequest)
      expect(result[0].path).to eq('/test')
    end

    it 'returns empty array when no data' do
      stub_request(:put, "#{base_url}/mockserver/retrieve?format=JSON&type=REQUESTS")
        .to_return(status: 200, body: '')

      result = client.retrieve_recorded_requests
      expect(result).to eq([])
    end

    it 'sends request filter in body' do
      stub_request(:put, "#{base_url}/mockserver/retrieve?format=JSON&type=REQUESTS")
        .to_return(status: 200, body: '[]')

      req = MockServer::HttpRequest.new(path: '/filter')
      client.retrieve_recorded_requests(request: req)

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/retrieve?format=JSON&type=REQUESTS")
        .with { |r| JSON.parse(r.body)['path'] == '/filter' }
    end
  end

  # -------------------------------------------------------------------
  # retrieve_active_expectations
  # -------------------------------------------------------------------
  describe '#retrieve_active_expectations' do
    it 'returns array of Expectation' do
      response_body = [{ 'id' => 'exp-1', 'httpRequest' => { 'path' => '/test' } }]
      stub_request(:put, "#{base_url}/mockserver/retrieve?format=JSON&type=ACTIVE_EXPECTATIONS")
        .to_return(status: 200, body: JSON.generate(response_body))

      result = client.retrieve_active_expectations
      expect(result.length).to eq(1)
      expect(result[0]).to be_a(MockServer::Expectation)
    end
  end

  # -------------------------------------------------------------------
  # retrieve_recorded_expectations
  # -------------------------------------------------------------------
  describe '#retrieve_recorded_expectations' do
    it 'returns array of Expectation' do
      response_body = [{ 'httpRequest' => { 'path' => '/recorded' } }]
      stub_request(:put, "#{base_url}/mockserver/retrieve?format=JSON&type=RECORDED_EXPECTATIONS")
        .to_return(status: 200, body: JSON.generate(response_body))

      result = client.retrieve_recorded_expectations
      expect(result.length).to eq(1)
      expect(result[0]).to be_a(MockServer::Expectation)
    end
  end

  # -------------------------------------------------------------------
  # retrieve_recorded_requests_and_responses
  # -------------------------------------------------------------------
  describe '#retrieve_recorded_requests_and_responses' do
    it 'returns array of HttpRequestAndHttpResponse' do
      response_body = [{
        'httpRequest' => { 'path' => '/test' },
        'httpResponse' => { 'statusCode' => 200 }
      }]
      stub_request(:put, "#{base_url}/mockserver/retrieve?format=JSON&type=REQUEST_RESPONSES")
        .to_return(status: 200, body: JSON.generate(response_body))

      result = client.retrieve_recorded_requests_and_responses
      expect(result.length).to eq(1)
      expect(result[0]).to be_a(MockServer::HttpRequestAndHttpResponse)
      expect(result[0].http_request.path).to eq('/test')
      expect(result[0].http_response.status_code).to eq(200)
    end
  end

  # -------------------------------------------------------------------
  # retrieve_log_messages
  # -------------------------------------------------------------------
  describe '#retrieve_log_messages' do
    it 'returns array of strings from JSON response' do
      stub_request(:put, "#{base_url}/mockserver/retrieve?type=LOGS")
        .to_return(status: 200, body: JSON.generate(['log entry 1', 'log entry 2']))

      result = client.retrieve_log_messages
      expect(result).to eq(['log entry 1', 'log entry 2'])
    end

    it 'splits plain text response by separator' do
      stub_request(:put, "#{base_url}/mockserver/retrieve?type=LOGS")
        .to_return(status: 200, body: "entry 1------------------------------------\nentry 2")

      result = client.retrieve_log_messages
      expect(result).to eq(['entry 1', 'entry 2'])
    end

    it 'returns empty array when no data' do
      stub_request(:put, "#{base_url}/mockserver/retrieve?type=LOGS")
        .to_return(status: 200, body: '')

      result = client.retrieve_log_messages
      expect(result).to eq([])
    end
  end

  # -------------------------------------------------------------------
  # bind
  # -------------------------------------------------------------------
  describe '#bind' do
    it 'sends ports and returns bound ports' do
      stub_request(:put, "#{base_url}/mockserver/bind")
        .to_return(status: 200, body: JSON.generate({ 'ports' => [1080, 1081] }))

      result = client.bind(1080, 1081)
      expect(result).to eq([1080, 1081])
    end

    it 'returns empty array when no response' do
      stub_request(:put, "#{base_url}/mockserver/bind")
        .to_return(status: 200, body: '')

      result = client.bind(1080)
      expect(result).to eq([])
    end

    it 'raises Error on failure' do
      stub_request(:put, "#{base_url}/mockserver/bind")
        .to_return(status: 400, body: 'bad request')

      expect { client.bind(1080) }.to raise_error(MockServer::Error)
    end
  end

  # -------------------------------------------------------------------
  # stop
  # -------------------------------------------------------------------
  describe '#stop' do
    it 'sends PUT to /mockserver/stop' do
      stub_request(:put, "#{base_url}/mockserver/stop")
        .to_return(status: 200, body: '')

      result = client.stop
      expect(result).to be_nil
    end

    it 'returns nil even on connection error (server shutting down)' do
      stub_request(:put, "#{base_url}/mockserver/stop")
        .to_raise(Errno::ECONNREFUSED)

      result = client.stop
      expect(result).to be_nil
    end

    it 'closes WebSocket connections on stop' do
      stub_request(:put, "#{base_url}/mockserver/stop")
        .to_return(status: 200, body: '')

      ws_client = instance_double(MockServer::WebSocketClient)
      allow(ws_client).to receive(:close)
      client.instance_variable_get(:@websocket_clients) << ws_client

      client.stop

      expect(ws_client).to have_received(:close)
      expect(client.instance_variable_get(:@websocket_clients)).to be_empty
    end

    it 'closes WebSocket connections even when stop raises connection error' do
      stub_request(:put, "#{base_url}/mockserver/stop")
        .to_raise(Errno::ECONNREFUSED)

      ws_client = instance_double(MockServer::WebSocketClient)
      allow(ws_client).to receive(:close)
      client.instance_variable_get(:@websocket_clients) << ws_client

      client.stop

      expect(ws_client).to have_received(:close)
    end
  end

  # -------------------------------------------------------------------
  # has_started?
  # -------------------------------------------------------------------
  describe '#has_started?' do
    it 'returns true when server responds with 200' do
      stub_request(:put, "#{base_url}/mockserver/status")
        .to_return(status: 200, body: '')

      expect(client.has_started?(attempts: 1, timeout: 0.01)).to eq(true)
    end

    it 'retries on connection error' do
      call_count = 0
      stub_request(:put, "#{base_url}/mockserver/status")
        .to_return { |_req|
          call_count += 1
          if call_count < 3
            raise Errno::ECONNREFUSED
          else
            { status: 200, body: '' }
          end
        }

      expect(client.has_started?(attempts: 5, timeout: 0.01)).to eq(true)
    end

    it 'returns false after all attempts fail' do
      stub_request(:put, "#{base_url}/mockserver/status")
        .to_raise(Errno::ECONNREFUSED)

      expect(client.has_started?(attempts: 2, timeout: 0.01)).to eq(false)
    end

    it 'has has_started alias without question mark' do
      stub_request(:put, "#{base_url}/mockserver/status")
        .to_return(status: 200, body: '')

      expect(client.has_started(attempts: 1, timeout: 0.01)).to eq(true)
    end
  end

  # -------------------------------------------------------------------
  # context_path
  # -------------------------------------------------------------------
  describe 'with context_path' do
    let(:client) { MockServer::Client.new(host, port, context_path: '/mockserver-ctx') }

    it 'includes context path in URL' do
      stub_request(:put, "#{base_url}/mockserver-ctx/mockserver/reset")
        .to_return(status: 200, body: '')

      client.reset
      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver-ctx/mockserver/reset")
    end
  end

  describe 'with context_path without leading slash' do
    let(:client) { MockServer::Client.new(host, port, context_path: 'ctx') }

    it 'adds leading slash' do
      stub_request(:put, "#{base_url}/ctx/mockserver/reset")
        .to_return(status: 200, body: '')

      client.reset
      expect(WebMock).to have_requested(:put, "#{base_url}/ctx/mockserver/reset")
    end
  end

  # -------------------------------------------------------------------
  # Block form (context manager pattern)
  # -------------------------------------------------------------------
  describe 'block form' do
    it 'yields client and closes after block' do
      stub_request(:put, "#{base_url}/mockserver/reset")
        .to_return(status: 200, body: '')

      yielded_client = nil
      MockServer::Client.new(host, port) do |c|
        yielded_client = c
        c.reset
      end

      expect(yielded_client).to be_a(MockServer::Client)
      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/reset")
    end
  end

  # -------------------------------------------------------------------
  # Fluent API (when)
  # -------------------------------------------------------------------
  describe '#when' do
    it 'returns a ForwardChainExpectation' do
      req = MockServer::HttpRequest.request(path: '/test')
      chain = client.when(req)
      expect(chain).to be_a(MockServer::ForwardChainExpectation)
    end

    it 'chains to respond with HttpResponse' do
      stub_request(:put, "#{base_url}/mockserver/expectation")
        .to_return(status: 201, body: '[]')

      client.when(
        MockServer::HttpRequest.request(path: '/test')
      ).respond(
        MockServer::HttpResponse.response(body: 'hello')
      )

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/expectation")
        .with { |req|
          parsed = JSON.parse(req.body)
          parsed[0]['httpRequest']['path'] == '/test' &&
            parsed[0]['httpResponse']['body'] == 'hello'
        }
    end

    it 'chains with times and time_to_live' do
      stub_request(:put, "#{base_url}/mockserver/expectation")
        .to_return(status: 201, body: '[]')

      client.when(
        MockServer::HttpRequest.request(path: '/test'),
        times: MockServer::Times.exactly(3),
        time_to_live: MockServer::TimeToLive.exactly(60, 'SECONDS')
      ).respond(
        MockServer::HttpResponse.response(body: 'ok')
      )

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/expectation")
        .with { |req|
          parsed = JSON.parse(req.body)
          parsed[0]['times']['remainingTimes'] == 3 &&
            parsed[0]['timeToLive']['timeToLive'] == 60
        }
    end

    it 'chains to forward with HttpForward' do
      stub_request(:put, "#{base_url}/mockserver/expectation")
        .to_return(status: 201, body: '[]')

      client.when(
        MockServer::HttpRequest.request(path: '/proxy')
      ).forward(
        MockServer::HttpForward.new(host: 'backend.local', port: 8080)
      )

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/expectation")
        .with { |req|
          parsed = JSON.parse(req.body)
          parsed[0]['httpForward']['host'] == 'backend.local'
        }
    end

    it 'chains to error' do
      stub_request(:put, "#{base_url}/mockserver/expectation")
        .to_return(status: 201, body: '[]')

      client.when(
        MockServer::HttpRequest.request(path: '/error')
      ).error(
        MockServer::HttpError.new(drop_connection: true)
      )

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/expectation")
        .with { |req|
          parsed = JSON.parse(req.body)
          parsed[0]['httpError']['dropConnection'] == true
        }
    end
  end

  # -------------------------------------------------------------------
  # Connection error handling
  # -------------------------------------------------------------------
  describe 'connection error handling' do
    it 'raises ConnectionError on connection refused' do
      stub_request(:put, "#{base_url}/mockserver/reset")
        .to_raise(Errno::ECONNREFUSED)

      expect { client.reset }.to raise_error(MockServer::ConnectionError)
    end

    it 'raises ConnectionError on timeout' do
      stub_request(:put, "#{base_url}/mockserver/reset")
        .to_timeout

      expect { client.reset }.to raise_error(MockServer::ConnectionError)
    end

    it 'raises ConnectionError on SSL error' do
      stub_request(:put, "#{base_url}/mockserver/reset")
        .to_raise(OpenSSL::SSL::SSLError.new('certificate verify failed'))

      expect { client.reset }.to raise_error(MockServer::ConnectionError, /TLS error/)
    end
  end
end
