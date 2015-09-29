# encoding: UTF-8
require 'rspec'
require 'net/http'
require 'webmock/rspec'
require_relative '../../lib/mockserver-client'

RSpec.configure do |config|
  include WebMock::API
  include MockServer
  include MockServer::UtilityMethods
  include MockServer::Model::DSL

  # Only accept expect syntax do not allow old should syntax
  config.expect_with :rspec do |c|
    c.syntax = :expect
  end
end

describe MockServer::MockServerClient do

  let(:client) { MockServer::MockServerClient.new('localhost', 1080) }

  before do
    # To suppress logging output to standard output, write to a temporary file
    client.logger = LoggingFactory::DEFAULT_FACTORY.log('test', output: 'tmp.log', truncate: true)
  end

  def create_agent
    uri      = URI('http://api.nsa.gov:1337/agent')
    http     = Net::HTTP.new(uri.host, uri.port)
    req      = Net::HTTP::Post.new(uri.path, 'Content-Type' => 'application/json')
    req.body = { name: 'John Doe', role: 'agent' }.to_json
    res = http.request(req)
    puts "response #{res.body}"
  rescue => e
    puts "failed #{e}"
  end

  it 'setup complex expectation' do
    WebMock.allow_net_connect!

    # given
    mock_expectation = expectation do |expectation|
      expectation.request do |request|
        request.method = 'POST'
        request.path   = '/somePath'
        request.query_string_parameters << parameter('param', 'someQueryStringValue')
        request.headers << header('Header', 'someHeaderValue')
        request.cookies << cookie('cookie', 'someCookieValue')
        request.body = exact('someBody')
      end

      expectation.response do |response|
        response.status_code = 201
        response.headers << header('header', 'someHeaderValue')
        response.body  = body('someBody')
        response.delay = delay_by(:SECONDS, 1)
      end

      expectation.times = exactly(1)
    end

    # and
    expect(client.register(mock_expectation).code).to eq(201)

    # when
    uri           = URI('http://localhost:1080/somePath')
    http          = Net::HTTP.new(uri.host, uri.port)
    req           = Net::HTTP::Post.new('/somePath?param=someQueryStringValue')
    req['Header'] = 'someHeaderValue'
    req['Cookie'] = 'cookie=someCookieValue'
    req.body      = 'someBody'
    res           = http.request(req)

    # then
    expect(res.code).to eq('201')
    expect(res.body).to eq('someBody')

    WebMock.disable_net_connect!
  end

end
