# encoding: UTF-8
require_relative '../spec_helper'

describe MockServer::MockServerClient do

  let(:client) { MockServer::MockServerClient.new('localhost', 8080) }
  let(:register_expectation_json) { FIXTURES.read('register_expectation.json').to_json }
  let(:search_request_json) { FIXTURES.read('search_request.json').to_json }

  before do
    # To suppress logging output to standard output, write to a temporary file
    client.logger = LoggingFactory::DEFAULT_FACTORY.log('test', output: 'tmp.log', truncate: true)

    # Stub requests
    stub_request(:put, /.+\/expectation/).with(body: register_expectation_json, headers: { 'Content-Type' => 'application/json' }).to_return(status: 201)
    stub_request(:put, /.+\/clear/).with(body: search_request_json, headers: { 'Content-Type' => 'application/json' }).to_return(status: 202)
    stub_request(:put, /.+\/reset/).with(headers: { 'Content-Type' => 'application/json' }).to_return(status: 202)
    stub_request(:put, /.+\/retrieve/).with(body: search_request_json, headers: { 'Content-Type' => 'application/json' }).to_return(body: '', status: 200)
  end

  it 'registers an expectation correctly' do
    mock_expectation = expectation do |expectation|
      expectation.request do |request|
        request.method = 'POST'
        request.path   = '/login'
        request.query_parameters << parameter('returnUrl', '/account')
        request.cookies << cookie('sessionId', '2By8LOhBmaW5nZXJwcmludCIlMDAzMW')
        request.body = exact({ username: 'foo', password: 'bar' }.to_json)
      end

      expectation.response do |response|
        response.status_code = 401
        response.headers << header('Content-Type', 'application/json; charset=utf-8')
        response.headers << header('Cache-Control', 'public, max-age=86400')
        response.body  = body({ message: 'incorrect username and password combination' }.to_json)
        response.delay = delay_by(:SECONDS, 1)
      end

      expectation.times = exactly(2)
    end

    response = client.register(mock_expectation)
    expect(response.code).to eq(201)
  end

  it 'raises an error when trying to set both forward and response' do
    mock_expectation = expectation do |expectation|
      expectation.request do |request|
        request.method = 'POST'
        request.path   = '/login'
      end

      expectation.response do |response|
        response.status_code = 401
      end

      expectation.forward do |forward|
        forward.scheme = :HTTP
      end
    end

    expect { client.register(mock_expectation) }.to raise_error(RuntimeError, 'You can only set either of ["httpResponse", "httpForward"]. But not both')
  end

  it 'clears requests from the mock server' do
    expect(client.clear(request(:POST, '/login')).code).to eq(202)
  end

  it 'resets the mock server' do
    expect(client.reset.code).to eq(202)
  end

  it 'retrieves requests correctly' do
    expect(client.retrieve(request(:POST, '/login')).code).to eq(200)
  end

end
