# frozen_string_literal: true

require 'mockserver-client'
require 'net/http'
require 'json'
require 'socket'
require 'timeout'

RSpec.describe 'Integration', :integration do
  def self.find_free_port
    server = TCPServer.new('127.0.0.1', 0)
    port = server.addr[1]
    server.close
    port
  end

  def self.start_mockserver_container(port)
    container_name = "mockserver-ruby-integration-#{port}"
    output = `docker run -d --name #{container_name} -p #{port}:1080 mockserver/mockserver:latest 2>&1`
    raise "Failed to start container: #{output}" unless $?.success?

    container_id = output.strip

    deadline = Time.now + 30
    loop do
      uri = URI("http://localhost:#{port}/mockserver/status")
      req = Net::HTTP::Put.new(uri)
      resp = Net::HTTP.start(uri.hostname, uri.port) { |http| http.request(req) }
      break if resp.code == '200'
    rescue StandardError
      raise "MockServer did not start within 30s" if Time.now > deadline

      sleep 0.5
      retry
    end

    container_id
  end

  def self.stop_mockserver_container(container_id)
    system("docker rm -f #{container_id} >/dev/null 2>&1")
  end

  MOCKSERVER_PORT = find_free_port
  @container_id = nil

  class << self
    attr_accessor :container_id
  end

  before(:context) do
    skip 'Docker not available' unless system('docker', 'info', out: File::NULL, err: File::NULL)
    self.class.container_id = self.class.start_mockserver_container(MOCKSERVER_PORT)
  end

  after(:context) do
    self.class.stop_mockserver_container(self.class.container_id) if self.class.container_id
  end

  let(:port) { MOCKSERVER_PORT }
  let(:client) { MockServer::Client.new('localhost', port) }

  before do
    client.reset
  end

  after do
    client.close
  end

  def make_request(port, method, path, body: nil, headers: {})
    uri = URI("http://localhost:#{port}#{path}")
    klass = case method.upcase
            when 'GET'    then Net::HTTP::Get
            when 'POST'   then Net::HTTP::Post
            when 'PUT'    then Net::HTTP::Put
            when 'DELETE' then Net::HTTP::Delete
            else raise "Unknown method: #{method}"
            end
    req = klass.new(uri)
    req.body = body if body
    headers.each { |k, v| req[k] = v }
    Net::HTTP.start(uri.hostname, uri.port) { |http| http.request(req) }
  end

  describe 'connection' do
    it 'reports server has started' do
      expect(client.has_started?).to be true
    end

    it 'works with block form' do
      MockServer::Client.new('localhost', port) do |c|
        expect(c.has_started?).to be true
      end
    end
  end

  describe 'expectation lifecycle' do
    it 'creates and retrieves expectations' do
      expectation = MockServer::Expectation.new(
        http_request: MockServer::HttpRequest.request(path: '/test'),
        http_response: MockServer::HttpResponse.response(body: 'hello', status_code: 200),
        times: MockServer::Times.unlimited
      )
      result = client.upsert(expectation)
      expect(result.length).to eq(1)
      expect(result[0].http_request.path).to eq('/test')

      active = client.retrieve_active_expectations
      expect(active.any? { |e| e.http_request.path == '/test' }).to be true
    end

    it 'clears expectations' do
      client.upsert(MockServer::Expectation.new(
        http_request: MockServer::HttpRequest.request(path: '/to-clear'),
        http_response: MockServer::HttpResponse.response(body: 'gone')
      ))
      client.clear(MockServer::HttpRequest.request(path: '/to-clear'))
      active = client.retrieve_active_expectations
      expect(active.none? { |e| e.http_request.path == '/to-clear' }).to be true
    end

    it 'resets all state' do
      client.upsert(MockServer::Expectation.new(
        http_request: MockServer::HttpRequest.request(path: '/before-reset'),
        http_response: MockServer::HttpResponse.response
      ))
      client.reset
      expect(client.retrieve_active_expectations).to be_empty
    end
  end

  describe 'request matching' do
    it 'matches simple GET' do
      client.when(
        MockServer::HttpRequest.new(method: 'GET', path: '/api/hello')
      ).respond(
        MockServer::HttpResponse.response(body: 'world', status_code: 200)
      )

      resp = make_request(port, 'GET', '/api/hello')
      expect(resp.code).to eq('200')
      expect(resp.body).to eq('world')
    end

    it 'matches POST with JSON body' do
      client.when(
        MockServer::HttpRequest.new(
          method: 'POST',
          path: '/api/data',
          body: MockServer::Body.json({ key: 'value' })
        )
      ).respond(
        MockServer::HttpResponse.response(body: '{"result":"created"}', status_code: 201)
      )

      resp = make_request(port, 'POST', '/api/data',
                          body: '{"key":"value"}',
                          headers: { 'Content-Type' => 'application/json' })
      expect(resp.code).to eq('201')
      expect(JSON.parse(resp.body)['result']).to eq('created')
    end

    it 'returns 404 for unmatched requests' do
      resp = make_request(port, 'GET', '/no-such-path')
      expect(resp.code).to eq('404')
    end

    it 'returns custom status codes' do
      client.when(
        MockServer::HttpRequest.new(method: 'DELETE', path: '/api/resource')
      ).respond(
        MockServer::HttpResponse.response(status_code: 204)
      )

      resp = make_request(port, 'DELETE', '/api/resource')
      expect(resp.code).to eq('204')
    end

    it 'returns custom response headers' do
      client.when(
        MockServer::HttpRequest.new(method: 'GET', path: '/with-headers')
      ).respond(
        MockServer::HttpResponse.new(
          status_code: 200,
          body: 'ok',
          headers: [MockServer::KeyToMultiValue.new(name: 'X-Custom', values: ['test-value'])]
        )
      )

      resp = make_request(port, 'GET', '/with-headers')
      expect(resp['X-Custom']).to eq('test-value')
    end

    it 'honours Times.exactly' do
      client.when(
        MockServer::HttpRequest.new(method: 'GET', path: '/once-only'),
        times: MockServer::Times.exactly(1)
      ).respond(
        MockServer::HttpResponse.response(body: 'first', status_code: 200)
      )

      resp1 = make_request(port, 'GET', '/once-only')
      expect(resp1.code).to eq('200')
      expect(resp1.body).to eq('first')

      resp2 = make_request(port, 'GET', '/once-only')
      expect(resp2.code).to eq('404')
    end
  end

  describe 'verification' do
    it 'verifies a request was received' do
      client.when(
        MockServer::HttpRequest.new(method: 'GET', path: '/verify-me')
      ).respond(
        MockServer::HttpResponse.response(status_code: 200)
      )

      make_request(port, 'GET', '/verify-me')

      expect {
        client.verify(
          MockServer::HttpRequest.new(method: 'GET', path: '/verify-me'),
          times: MockServer::VerificationTimes.exactly(1)
        )
      }.not_to raise_error
    end

    it 'raises on verification failure' do
      expect {
        client.verify(
          MockServer::HttpRequest.new(method: 'GET', path: '/never-called'),
          times: MockServer::VerificationTimes.exactly(1)
        )
      }.to raise_error(MockServer::VerificationError)
    end

    it 'verifies zero interactions' do
      expect { client.verify_zero_interactions }.not_to raise_error
    end

    it 'verifies multiple calls' do
      client.when(
        MockServer::HttpRequest.new(method: 'GET', path: '/multi')
      ).respond(
        MockServer::HttpResponse.response(status_code: 200)
      )

      3.times { make_request(port, 'GET', '/multi') }

      expect {
        client.verify(
          MockServer::HttpRequest.new(method: 'GET', path: '/multi'),
          times: MockServer::VerificationTimes.exactly(3)
        )
      }.not_to raise_error
    end

    it 'verifies request sequence' do
      client.when(
        MockServer::HttpRequest.new(path: '/seq')
      ).respond(
        MockServer::HttpResponse.response(status_code: 200)
      )

      make_request(port, 'GET', '/seq')
      make_request(port, 'POST', '/seq')

      expect {
        client.verify_sequence(
          MockServer::HttpRequest.new(method: 'GET', path: '/seq'),
          MockServer::HttpRequest.new(method: 'POST', path: '/seq')
        )
      }.not_to raise_error
    end
  end

  describe 'retrieval' do
    it 'retrieves recorded requests' do
      make_request(port, 'GET', '/record-me')

      requests = client.retrieve_recorded_requests(request: MockServer::HttpRequest.request(path: '/record-me'))
      expect(requests.length).to be >= 1
      expect(requests[0].path).to eq('/record-me')
      expect(requests[0].method).to eq('GET')
    end

    it 'retrieves log messages' do
      make_request(port, 'GET', '/log-test')

      logs = client.retrieve_log_messages
      expect(logs.length).to be > 0
    end

    it 'retrieves requests and responses' do
      client.when(
        MockServer::HttpRequest.new(method: 'GET', path: '/req-resp')
      ).respond(
        MockServer::HttpResponse.response(body: 'matched', status_code: 200)
      )

      make_request(port, 'GET', '/req-resp')

      pairs = client.retrieve_recorded_requests_and_responses(
        request: MockServer::HttpRequest.request(path: '/req-resp')
      )
      expect(pairs.length).to be >= 1
      expect(pairs[0].http_request.path).to eq('/req-resp')
      expect(pairs[0].http_response.status_code).to eq(200)
    end
  end

  describe 'fluent API' do
    it 'supports with_id' do
      client.when(
        MockServer::HttpRequest.new(method: 'GET', path: '/fluent-id')
      ).with_id('my-expectation-id').respond(
        MockServer::HttpResponse.response(body: 'fluent', status_code: 200)
      )

      resp = make_request(port, 'GET', '/fluent-id')
      expect(resp.code).to eq('200')
      expect(resp.body).to eq('fluent')
    end

    it 'supports priority ordering' do
      client.when(
        MockServer::HttpRequest.new(method: 'GET', path: '/priority-test')
      ).with_priority(10).respond(
        MockServer::HttpResponse.response(body: 'high-priority', status_code: 200)
      )

      client.when(
        MockServer::HttpRequest.new(method: 'GET', path: '/priority-test')
      ).with_priority(1).respond(
        MockServer::HttpResponse.response(body: 'low-priority', status_code: 200)
      )

      resp = make_request(port, 'GET', '/priority-test')
      expect(resp.code).to eq('200')
      expect(resp.body).to eq('high-priority')
    end
  end
end
