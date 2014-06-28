# encoding: UTF-8
require_relative '../spec_helper'

describe MockServer::Model::DSL do

  it 'generates http requests correctly' do
    mock_request = http_request(:POST, '/login')
    mock_request.query_parameters = [parameter('returnUrl', '/account')]
    mock_request.cookies = [cookie('sessionId', '2By8LOhBmaW5nZXJwcmludCIlMDAzMW')]
    mock_request.body = exact("{username:'foo', password:'bar'}")

    expect(to_camelized_hash(HTTP_REQUEST => mock_request)).to eq(FIXTURES.read('post_login_request.json'))

    # Block style
    mock_request = request(:POST, '/login') do |request|
      request.query_parameters = [parameter('returnUrl', '/account')]
      request.cookies = [cookie('sessionId', '2By8LOhBmaW5nZXJwcmludCIlMDAzMW')]
      request.body = exact("{username:'foo', password:'bar'}")
    end

    expect(to_camelized_hash(HTTP_REQUEST => mock_request)).to eq(FIXTURES.read('post_login_request.json'))
  end

  it 'generates http responses correctly' do
    mock_response = http_response
    mock_response.status_code = 401
    mock_response.headers = [header('Content-Type', 'application/json; charset=utf-8')]
    mock_response.headers << header('Cache-Control', 'public, max-age=86400')
    mock_response.body = body('incorrect username and password combination')
    mock_response.delay = delay_by(:SECONDS, 1)

    expect(to_camelized_hash(HTTP_RESPONSE => mock_response)).to eq(FIXTURES.read('incorrect_login_response.json'))

    # Block style
    mock_response = response do |response|
      response.status_code = 401
      response.headers = [header('Content-Type', 'application/json; charset=utf-8')]
      response.headers << header('Cache-Control', 'public, max-age=86400')
      response.body = body('incorrect username and password combination')
      response.delay = delay_by(:SECONDS, 1)
    end

    expect(to_camelized_hash(HTTP_RESPONSE => mock_response)).to eq(FIXTURES.read('incorrect_login_response.json'))
  end

  it 'generates http forwards correctly' do
    mock_forward = http_forward
    mock_forward.host = 'www.mock-server.com'
    mock_forward.port = 80

    expect(to_camelized_hash(HTTP_FORWARD => mock_forward)).to eq(FIXTURES.read('forward_mockserver.json'))

    # Block style
    mock_forward = forward do |forward|
      forward.host = 'www.mock-server.com'
      forward.port = 80
    end

    expect(to_camelized_hash(HTTP_FORWARD => mock_forward)).to eq(FIXTURES.read('forward_mockserver.json'))
  end

  it 'generates times correctly' do
    expect(to_camelized_hash(HTTP_TIMES => exactly(1))).to eq(FIXTURES.read('times_once.json'))

    # Block style
    mock_times = times do |times|
      times.remaining_times = 1
    end
    expect(to_camelized_hash(HTTP_TIMES => mock_times)).to eq(FIXTURES.read('times_once.json'))
  end

  it 'generates expectation correctly from file' do
    payload = FIXTURES.read('register_expectation.json')
    mock_expectation = expectation
    mock_expectation.populate_from_payload(payload)

    expect(to_camelized_hash(mock_expectation.to_hash)).to eq(payload)
  end

  it 'generates body correctly' do
    expect(to_camelized_hash(regex('*/login'))).to eq('type' => 'REGEX', 'value' => '*/login')
    expect(to_camelized_hash(xpath('/login[1]'))).to eq('type' => 'XPATH', 'value' => '/login[1]')
    expect(to_camelized_hash(parameterized(parameter('token', '4jy5hh')))).to eq('type' => 'PARAMETERS', 'parameters' => [{ 'name' => 'token', 'values' => ['4jy5hh'] }])
  end

  it 'generates times object correctly' do
    expect(to_camelized_hash(unlimited)).to eq('unlimited' => 'true', 'remainingTimes' => 0)
    expect(to_camelized_hash(at_least(2))).to eq('unlimited' => 'true', 'remainingTimes' => 2)
  end

end
