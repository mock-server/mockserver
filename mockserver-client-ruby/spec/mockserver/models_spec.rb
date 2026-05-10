# frozen_string_literal: true

RSpec.describe 'MockServer models' do
  # -------------------------------------------------------------------
  # DelayDistribution
  # -------------------------------------------------------------------
  describe MockServer::DelayDistribution do
    it 'has nil defaults' do
      dist = MockServer::DelayDistribution.new
      expect(dist.type).to be_nil
      expect(dist.min).to be_nil
      expect(dist.max).to be_nil
    end

    it 'serializes uniform to camelCase hash' do
      dist = MockServer::DelayDistribution.new(type: 'UNIFORM', min: 100, max: 500)
      expect(dist.to_h).to eq({ 'type' => 'UNIFORM', 'min' => 100, 'max' => 500 })
    end

    it 'serializes log_normal to camelCase hash' do
      dist = MockServer::DelayDistribution.new(type: 'LOG_NORMAL', median: 200, p99: 800)
      expect(dist.to_h).to eq({ 'type' => 'LOG_NORMAL', 'median' => 200, 'p99' => 800 })
    end

    it 'serializes gaussian to camelCase hash' do
      dist = MockServer::DelayDistribution.new(type: 'GAUSSIAN', mean: 200, std_dev: 50)
      expect(dist.to_h).to eq({ 'type' => 'GAUSSIAN', 'mean' => 200, 'stdDev' => 50 })
    end

    it 'deserializes from camelCase hash' do
      dist = MockServer::DelayDistribution.from_hash({ 'type' => 'UNIFORM', 'min' => 10, 'max' => 20 })
      expect(dist.type).to eq('UNIFORM')
      expect(dist.min).to eq(10)
      expect(dist.max).to eq(20)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::DelayDistribution.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::DelayDistribution.new(type: 'GAUSSIAN', mean: 100, std_dev: 25)
      roundtrip = MockServer::DelayDistribution.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # Delay
  # -------------------------------------------------------------------
  describe MockServer::Delay do
    it 'has sensible defaults' do
      delay = MockServer::Delay.new
      expect(delay.time_unit).to eq('MILLISECONDS')
      expect(delay.value).to eq(0)
      expect(delay.distribution).to be_nil
    end

    it 'serializes to camelCase hash' do
      delay = MockServer::Delay.new(time_unit: 'SECONDS', value: 5)
      expect(delay.to_h).to eq({ 'timeUnit' => 'SECONDS', 'value' => 5 })
    end

    it 'deserializes from camelCase hash' do
      delay = MockServer::Delay.from_hash({ 'timeUnit' => 'SECONDS', 'value' => 3 })
      expect(delay.time_unit).to eq('SECONDS')
      expect(delay.value).to eq(3)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::Delay.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::Delay.new(time_unit: 'SECONDS', value: 10)
      roundtrip = MockServer::Delay.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end

    it 'serializes with distribution' do
      dist = MockServer::DelayDistribution.new(type: 'UNIFORM', min: 100, max: 500)
      delay = MockServer::Delay.new(time_unit: 'MILLISECONDS', distribution: dist)
      expect(delay.to_h).to eq({
        'timeUnit' => 'MILLISECONDS',
        'value' => 0,
        'distribution' => { 'type' => 'UNIFORM', 'min' => 100, 'max' => 500 }
      })
    end

    it 'deserializes with distribution' do
      delay = MockServer::Delay.from_hash({
        'timeUnit' => 'MILLISECONDS',
        'distribution' => { 'type' => 'LOG_NORMAL', 'median' => 200, 'p99' => 800 }
      })
      expect(delay.distribution).not_to be_nil
      expect(delay.distribution.type).to eq('LOG_NORMAL')
      expect(delay.distribution.median).to eq(200)
      expect(delay.distribution.p99).to eq(800)
    end

    it 'round-trips with distribution' do
      dist = MockServer::DelayDistribution.new(type: 'GAUSSIAN', mean: 200, std_dev: 50)
      original = MockServer::Delay.new(time_unit: 'MILLISECONDS', distribution: dist)
      roundtrip = MockServer::Delay.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # Times
  # -------------------------------------------------------------------
  describe MockServer::Times do
    it 'defaults all fields to nil' do
      times = MockServer::Times.new
      expect(times.remaining_times).to be_nil
      expect(times.unlimited).to be_nil
    end

    it 'serializes stripping nil values' do
      times = MockServer::Times.new(remaining_times: 3)
      expect(times.to_h).to eq({ 'remainingTimes' => 3 })
    end

    it 'deserializes from hash' do
      times = MockServer::Times.from_hash({ 'remainingTimes' => 5, 'unlimited' => false })
      expect(times.remaining_times).to eq(5)
      expect(times.unlimited).to eq(false)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::Times.from_hash(nil)).to be_nil
    end

    describe '.unlimited' do
      it 'creates unlimited Times' do
        times = MockServer::Times.unlimited
        expect(times.unlimited).to eq(true)
        expect(times.remaining_times).to be_nil
      end
    end

    describe '.exactly' do
      it 'creates Times with exact count' do
        times = MockServer::Times.exactly(3)
        expect(times.remaining_times).to eq(3)
        expect(times.unlimited).to eq(false)
      end
    end

    it 'round-trips correctly' do
      original = MockServer::Times.exactly(5)
      roundtrip = MockServer::Times.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # TimeToLive
  # -------------------------------------------------------------------
  describe MockServer::TimeToLive do
    it 'defaults all fields to nil' do
      ttl = MockServer::TimeToLive.new
      expect(ttl.time_unit).to be_nil
      expect(ttl.time_to_live).to be_nil
      expect(ttl.unlimited).to be_nil
    end

    it 'serializes stripping nil values' do
      ttl = MockServer::TimeToLive.new(unlimited: true)
      expect(ttl.to_h).to eq({ 'unlimited' => true })
    end

    it 'deserializes from hash' do
      ttl = MockServer::TimeToLive.from_hash({
        'timeUnit' => 'SECONDS',
        'timeToLive' => 30,
        'unlimited' => false
      })
      expect(ttl.time_unit).to eq('SECONDS')
      expect(ttl.time_to_live).to eq(30)
      expect(ttl.unlimited).to eq(false)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::TimeToLive.from_hash(nil)).to be_nil
    end

    describe '.unlimited' do
      it 'creates unlimited TimeToLive' do
        ttl = MockServer::TimeToLive.unlimited
        expect(ttl.unlimited).to eq(true)
      end
    end

    describe '.exactly' do
      it 'creates TimeToLive with exact duration' do
        ttl = MockServer::TimeToLive.exactly(60, 'SECONDS')
        expect(ttl.time_to_live).to eq(60)
        expect(ttl.time_unit).to eq('SECONDS')
        expect(ttl.unlimited).to eq(false)
      end
    end

    it 'round-trips correctly' do
      original = MockServer::TimeToLive.exactly(30, 'SECONDS')
      roundtrip = MockServer::TimeToLive.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # KeyToMultiValue
  # -------------------------------------------------------------------
  describe MockServer::KeyToMultiValue do
    it 'defaults to empty name and values' do
      kv = MockServer::KeyToMultiValue.new
      expect(kv.name).to eq('')
      expect(kv.values).to eq([])
    end

    it 'always emits name and values (server-required fields)' do
      kv = MockServer::KeyToMultiValue.new(name: 'Content-Type', values: ['application/json'])
      h = kv.to_h
      expect(h).to have_key('name')
      expect(h).to have_key('values')
      expect(h['name']).to eq('Content-Type')
      expect(h['values']).to eq(['application/json'])
    end

    it 'emits name and values even when empty' do
      kv = MockServer::KeyToMultiValue.new
      h = kv.to_h
      expect(h).to eq({ 'name' => '', 'values' => [] })
    end

    it 'deserializes from hash' do
      kv = MockServer::KeyToMultiValue.from_hash({ 'name' => 'Accept', 'values' => ['*/*'] })
      expect(kv.name).to eq('Accept')
      expect(kv.values).to eq(['*/*'])
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::KeyToMultiValue.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::KeyToMultiValue.new(name: 'X-Test', values: %w[a b])
      roundtrip = MockServer::KeyToMultiValue.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # Body
  # -------------------------------------------------------------------
  describe MockServer::Body do
    it 'defaults all fields to nil' do
      body = MockServer::Body.new
      expect(body.type).to be_nil
      expect(body.string).to be_nil
      expect(body.json).to be_nil
    end

    it 'serializes only non-nil fields' do
      body = MockServer::Body.new(type: 'STRING', string: 'hello')
      expect(body.to_h).to eq({ 'type' => 'STRING', 'string' => 'hello' })
    end

    it 'serializes not_body as "not" key' do
      body = MockServer::Body.new(type: 'STRING', string: 'hello', not_body: true)
      expect(body.to_h['not']).to eq(true)
    end

    it 'serializes base64_bytes as "base64Bytes"' do
      body = MockServer::Body.new(type: 'BINARY', base64_bytes: 'AAAA')
      expect(body.to_h['base64Bytes']).to eq('AAAA')
    end

    it 'serializes content_type as "contentType"' do
      body = MockServer::Body.new(type: 'STRING', string: 'x', content_type: 'text/plain')
      expect(body.to_h['contentType']).to eq('text/plain')
    end

    it 'deserializes from hash' do
      body = MockServer::Body.from_hash({
        'type' => 'JSON',
        'json' => { 'key' => 'value' },
        'not' => false,
        'contentType' => 'application/json'
      })
      expect(body.type).to eq('JSON')
      expect(body.json).to eq({ 'key' => 'value' })
      expect(body.not_body).to eq(false)
      expect(body.content_type).to eq('application/json')
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::Body.from_hash(nil)).to be_nil
    end

    describe 'factory methods' do
      it '.string creates STRING body' do
        body = MockServer::Body.string('hello')
        expect(body.type).to eq('STRING')
        expect(body.string).to eq('hello')
      end

      it '.json creates JSON body' do
        body = MockServer::Body.json({ 'key' => 'val' })
        expect(body.type).to eq('JSON')
        expect(body.json).to eq({ 'key' => 'val' })
      end

      it '.regex creates REGEX body' do
        body = MockServer::Body.regex('.*test.*')
        expect(body.type).to eq('REGEX')
        expect(body.string).to eq('.*test.*')
      end

      it '.exact creates STRING body' do
        body = MockServer::Body.exact('exact match')
        expect(body.type).to eq('STRING')
        expect(body.string).to eq('exact match')
      end

      it '.xml creates XML body' do
        body = MockServer::Body.xml('<root/>')
        expect(body.type).to eq('XML')
        expect(body.string).to eq('<root/>')
      end
    end

    it 'round-trips correctly' do
      original = MockServer::Body.new(
        type: 'JSON',
        json: { 'a' => 1 },
        content_type: 'application/json'
      )
      roundtrip = MockServer::Body.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # Body deserialization (via deserialize_body helper)
  # -------------------------------------------------------------------
  describe 'body deserialization' do
    it 'returns nil for nil' do
      expect(MockServer.deserialize_body(nil)).to be_nil
    end

    it 'returns string as-is' do
      expect(MockServer.deserialize_body('hello')).to eq('hello')
    end

    it 'returns Body for known BODY_TYPES' do
      result = MockServer.deserialize_body({ 'type' => 'STRING', 'string' => 'test' })
      expect(result).to be_a(MockServer::Body)
      expect(result.type).to eq('STRING')
    end

    it 'returns plain hash for unknown types' do
      result = MockServer.deserialize_body({ 'custom' => 'data' })
      expect(result).to be_a(Hash)
      expect(result['custom']).to eq('data')
    end

    it 'returns plain hash when type is not a known BODY_TYPE' do
      result = MockServer.deserialize_body({ 'type' => 'UNKNOWN', 'data' => 'x' })
      expect(result).to be_a(Hash)
    end
  end

  # -------------------------------------------------------------------
  # SocketAddress
  # -------------------------------------------------------------------
  describe MockServer::SocketAddress do
    it 'defaults all fields to nil' do
      sa = MockServer::SocketAddress.new
      expect(sa.host).to be_nil
      expect(sa.port).to be_nil
      expect(sa.scheme).to be_nil
    end

    it 'serializes stripping nil values' do
      sa = MockServer::SocketAddress.new(host: 'localhost', port: 8080)
      expect(sa.to_h).to eq({ 'host' => 'localhost', 'port' => 8080 })
    end

    it 'deserializes from hash' do
      sa = MockServer::SocketAddress.from_hash({ 'host' => 'example.com', 'port' => 443, 'scheme' => 'HTTPS' })
      expect(sa.host).to eq('example.com')
      expect(sa.port).to eq(443)
      expect(sa.scheme).to eq('HTTPS')
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::SocketAddress.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::SocketAddress.new(host: 'test.com', port: 9090, scheme: 'HTTP')
      roundtrip = MockServer::SocketAddress.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpRequest
  # -------------------------------------------------------------------
  describe MockServer::HttpRequest do
    it 'defaults all fields to nil' do
      req = MockServer::HttpRequest.new
      expect(req.method).to be_nil
      expect(req.path).to be_nil
      expect(req.headers).to be_nil
    end

    describe '.request factory' do
      it 'creates a request with path' do
        req = MockServer::HttpRequest.request(path: '/test')
        expect(req.path).to eq('/test')
      end

      it 'creates a request without path' do
        req = MockServer::HttpRequest.request
        expect(req.path).to be_nil
      end
    end

    describe 'builder methods' do
      it '#with_method returns self' do
        req = MockServer::HttpRequest.new
        result = req.with_method('GET')
        expect(result).to be(req)
        expect(req.method).to eq('GET')
      end

      it '#with_path returns self' do
        req = MockServer::HttpRequest.new
        result = req.with_path('/api')
        expect(result).to be(req)
        expect(req.path).to eq('/api')
      end

      it '#with_header appends headers and returns self' do
        req = MockServer::HttpRequest.new
        result = req.with_header('Accept', 'application/json')
        expect(result).to be(req)
        expect(req.headers.length).to eq(1)
        expect(req.headers[0].name).to eq('Accept')
        expect(req.headers[0].values).to eq(['application/json'])
      end

      it '#with_header supports multiple values' do
        req = MockServer::HttpRequest.new
        req.with_header('Accept', 'text/html', 'application/json')
        expect(req.headers[0].values).to eq(['text/html', 'application/json'])
      end

      it '#with_query_param appends query params and returns self' do
        req = MockServer::HttpRequest.new
        result = req.with_query_param('page', '1')
        expect(result).to be(req)
        expect(req.query_string_parameters.length).to eq(1)
        expect(req.query_string_parameters[0].name).to eq('page')
      end

      it '#with_cookie appends cookies and returns self' do
        req = MockServer::HttpRequest.new
        result = req.with_cookie('session', 'abc123')
        expect(result).to be(req)
        expect(req.cookies.length).to eq(1)
        expect(req.cookies[0].name).to eq('session')
        expect(req.cookies[0].values).to eq(['abc123'])
      end

      it '#with_body sets body and returns self' do
        req = MockServer::HttpRequest.new
        result = req.with_body('test body')
        expect(result).to be(req)
        expect(req.body).to eq('test body')
      end

      it '#with_secure sets secure and returns self' do
        req = MockServer::HttpRequest.new
        result = req.with_secure(true)
        expect(result).to be(req)
        expect(req.secure).to eq(true)
      end

      it '#with_keep_alive sets keep_alive and returns self' do
        req = MockServer::HttpRequest.new
        result = req.with_keep_alive(true)
        expect(result).to be(req)
        expect(req.keep_alive).to eq(true)
      end
    end

    it 'serializes to camelCase hash stripping nils' do
      req = MockServer::HttpRequest.new(method: 'POST', path: '/api')
      h = req.to_h
      expect(h).to eq({ 'method' => 'POST', 'path' => '/api' })
      expect(h).not_to have_key('headers')
    end

    it 'serializes headers as array of hashes' do
      req = MockServer::HttpRequest.new
      req.with_header('Content-Type', 'application/json')
      h = req.to_h
      expect(h['headers']).to eq([{ 'name' => 'Content-Type', 'values' => ['application/json'] }])
    end

    it 'serializes body as string' do
      req = MockServer::HttpRequest.new(body: 'hello')
      expect(req.to_h['body']).to eq('hello')
    end

    it 'serializes Body object' do
      req = MockServer::HttpRequest.new(body: MockServer::Body.string('hello'))
      expect(req.to_h['body']).to eq({ 'type' => 'STRING', 'string' => 'hello' })
    end

    it 'serializes socket_address' do
      sa = MockServer::SocketAddress.new(host: 'localhost', port: 8080)
      req = MockServer::HttpRequest.new(socket_address: sa)
      expect(req.to_h['socketAddress']).to eq({ 'host' => 'localhost', 'port' => 8080 })
    end

    it 'deserializes from hash' do
      data = {
        'method' => 'GET',
        'path' => '/test',
        'headers' => [{ 'name' => 'Accept', 'values' => ['*/*'] }],
        'body' => 'test body',
        'secure' => true,
        'keepAlive' => false,
        'socketAddress' => { 'host' => 'example.com', 'port' => 443 }
      }
      req = MockServer::HttpRequest.from_hash(data)
      expect(req.method).to eq('GET')
      expect(req.path).to eq('/test')
      expect(req.headers.length).to eq(1)
      expect(req.headers[0].name).to eq('Accept')
      expect(req.body).to eq('test body')
      expect(req.secure).to eq(true)
      expect(req.keep_alive).to eq(false)
      expect(req.socket_address.host).to eq('example.com')
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::HttpRequest.from_hash(nil)).to be_nil
    end

    it 'deserializes body with known type as Body object' do
      data = {
        'body' => { 'type' => 'JSON', 'json' => { 'key' => 'val' } }
      }
      req = MockServer::HttpRequest.from_hash(data)
      expect(req.body).to be_a(MockServer::Body)
      expect(req.body.type).to eq('JSON')
    end

    it 'round-trips correctly' do
      original = MockServer::HttpRequest.request(path: '/test')
                                        .with_method('POST')
                                        .with_header('X-Test', 'val')
                                        .with_query_param('q', 'search')
                                        .with_body('data')
      roundtrip = MockServer::HttpRequest.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # ConnectionOptions
  # -------------------------------------------------------------------
  describe MockServer::ConnectionOptions do
    it 'defaults all fields to nil' do
      co = MockServer::ConnectionOptions.new
      expect(co.close_socket).to be_nil
    end

    it 'serializes correctly' do
      co = MockServer::ConnectionOptions.new(
        close_socket: true,
        suppress_content_length_header: true,
        keep_alive_override: false
      )
      h = co.to_h
      expect(h['closeSocket']).to eq(true)
      expect(h['suppressContentLengthHeader']).to eq(true)
      expect(h['keepAliveOverride']).to eq(false)
    end

    it 'serializes close_socket_delay' do
      delay = MockServer::Delay.new(time_unit: 'SECONDS', value: 1)
      co = MockServer::ConnectionOptions.new(close_socket: true, close_socket_delay: delay)
      expect(co.to_h['closeSocketDelay']).to eq({ 'timeUnit' => 'SECONDS', 'value' => 1 })
    end

    it 'deserializes from hash' do
      co = MockServer::ConnectionOptions.from_hash({
        'closeSocket' => true,
        'closeSocketDelay' => { 'timeUnit' => 'SECONDS', 'value' => 2 }
      })
      expect(co.close_socket).to eq(true)
      expect(co.close_socket_delay.time_unit).to eq('SECONDS')
      expect(co.close_socket_delay.value).to eq(2)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::ConnectionOptions.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::ConnectionOptions.new(
        close_socket: true,
        content_length_header_override: 100
      )
      roundtrip = MockServer::ConnectionOptions.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpResponse
  # -------------------------------------------------------------------
  describe MockServer::HttpResponse do
    it 'defaults all fields to nil' do
      resp = MockServer::HttpResponse.new
      expect(resp.status_code).to be_nil
      expect(resp.headers).to be_nil
    end

    describe '.response factory' do
      it 'sets body and defaults to 200 OK' do
        resp = MockServer::HttpResponse.response(body: 'hello')
        expect(resp.body).to eq('hello')
        expect(resp.status_code).to eq(200)
        expect(resp.reason_phrase).to eq('OK')
      end

      it 'sets body with custom status code' do
        resp = MockServer::HttpResponse.response(body: 'created', status_code: 201)
        expect(resp.body).to eq('created')
        expect(resp.status_code).to eq(201)
        expect(resp.reason_phrase).to be_nil
      end

      it 'sets only status_code when no body' do
        resp = MockServer::HttpResponse.response(status_code: 204)
        expect(resp.body).to be_nil
        expect(resp.status_code).to eq(204)
      end

      it 'creates empty response with no args' do
        resp = MockServer::HttpResponse.response
        expect(resp.body).to be_nil
        expect(resp.status_code).to be_nil
      end
    end

    describe '.not_found_response' do
      it 'creates 404 Not Found' do
        resp = MockServer::HttpResponse.not_found_response
        expect(resp.status_code).to eq(404)
        expect(resp.reason_phrase).to eq('Not Found')
      end
    end

    describe 'builder methods' do
      it '#with_status_code returns self' do
        resp = MockServer::HttpResponse.new
        result = resp.with_status_code(201)
        expect(result).to be(resp)
        expect(resp.status_code).to eq(201)
      end

      it '#with_header returns self' do
        resp = MockServer::HttpResponse.new
        result = resp.with_header('Content-Type', 'text/plain')
        expect(result).to be(resp)
        expect(resp.headers.length).to eq(1)
      end

      it '#with_cookie returns self' do
        resp = MockServer::HttpResponse.new
        result = resp.with_cookie('id', 'abc')
        expect(result).to be(resp)
        expect(resp.cookies.length).to eq(1)
      end

      it '#with_body returns self' do
        resp = MockServer::HttpResponse.new
        result = resp.with_body('test')
        expect(result).to be(resp)
        expect(resp.body).to eq('test')
      end

      it '#with_delay returns self' do
        resp = MockServer::HttpResponse.new
        delay = MockServer::Delay.new(time_unit: 'SECONDS', value: 1)
        result = resp.with_delay(delay)
        expect(result).to be(resp)
        expect(resp.delay).to eq(delay)
      end

      it '#with_reason_phrase returns self' do
        resp = MockServer::HttpResponse.new
        result = resp.with_reason_phrase('Created')
        expect(result).to be(resp)
        expect(resp.reason_phrase).to eq('Created')
      end
    end

    it 'serializes to camelCase hash' do
      resp = MockServer::HttpResponse.new(status_code: 200, reason_phrase: 'OK', body: 'test')
      h = resp.to_h
      expect(h['statusCode']).to eq(200)
      expect(h['reasonPhrase']).to eq('OK')
      expect(h['body']).to eq('test')
    end

    it 'serializes connection_options' do
      co = MockServer::ConnectionOptions.new(close_socket: true)
      resp = MockServer::HttpResponse.new(connection_options: co)
      expect(resp.to_h['connectionOptions']).to eq({ 'closeSocket' => true })
    end

    it 'deserializes from hash' do
      data = {
        'statusCode' => 200,
        'reasonPhrase' => 'OK',
        'body' => 'hello',
        'delay' => { 'timeUnit' => 'MILLISECONDS', 'value' => 100 },
        'connectionOptions' => { 'closeSocket' => false }
      }
      resp = MockServer::HttpResponse.from_hash(data)
      expect(resp.status_code).to eq(200)
      expect(resp.body).to eq('hello')
      expect(resp.delay.value).to eq(100)
      expect(resp.connection_options.close_socket).to eq(false)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::HttpResponse.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::HttpResponse.response(body: 'test', status_code: 200)
                                          .with_header('X-Custom', 'val')
      roundtrip = MockServer::HttpResponse.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpForward
  # -------------------------------------------------------------------
  describe MockServer::HttpForward do
    it 'defaults all fields to nil' do
      fwd = MockServer::HttpForward.new
      expect(fwd.host).to be_nil
    end

    describe '.forward factory' do
      it 'creates empty HttpForward' do
        fwd = MockServer::HttpForward.forward
        expect(fwd).to be_a(MockServer::HttpForward)
      end
    end

    it 'serializes correctly' do
      fwd = MockServer::HttpForward.new(host: 'backend.local', port: 8080, scheme: 'HTTP')
      expect(fwd.to_h).to eq({
        'host' => 'backend.local',
        'port' => 8080,
        'scheme' => 'HTTP'
      })
    end

    it 'serializes with delay' do
      delay = MockServer::Delay.new(value: 100)
      fwd = MockServer::HttpForward.new(host: 'x', delay: delay)
      expect(fwd.to_h['delay']).to eq({ 'timeUnit' => 'MILLISECONDS', 'value' => 100 })
    end

    it 'deserializes from hash' do
      fwd = MockServer::HttpForward.from_hash({
        'host' => 'test.com',
        'port' => 443,
        'delay' => { 'timeUnit' => 'SECONDS', 'value' => 1 }
      })
      expect(fwd.host).to eq('test.com')
      expect(fwd.delay.time_unit).to eq('SECONDS')
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::HttpForward.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::HttpForward.new(host: 'test.com', port: 8080, scheme: 'HTTPS')
      roundtrip = MockServer::HttpForward.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpTemplate
  # -------------------------------------------------------------------
  describe MockServer::HttpTemplate do
    it 'defaults template_type to JAVASCRIPT' do
      tpl = MockServer::HttpTemplate.new
      expect(tpl.template_type).to eq('JAVASCRIPT')
    end

    describe '.template factory' do
      it 'creates template with type' do
        tpl = MockServer::HttpTemplate.template('VELOCITY', 'return ...')
        expect(tpl.template_type).to eq('VELOCITY')
        expect(tpl.template).to eq('return ...')
      end
    end

    it 'serializes correctly' do
      tpl = MockServer::HttpTemplate.new(template_type: 'JAVASCRIPT', template: 'return {};')
      expect(tpl.to_h).to eq({
        'templateType' => 'JAVASCRIPT',
        'template' => 'return {};'
      })
    end

    it 'deserializes from hash' do
      tpl = MockServer::HttpTemplate.from_hash({
        'templateType' => 'VELOCITY',
        'template' => '#set($x = 1)'
      })
      expect(tpl.template_type).to eq('VELOCITY')
      expect(tpl.template).to eq('#set($x = 1)')
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::HttpTemplate.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::HttpTemplate.new(template_type: 'JAVASCRIPT', template: 'code')
      roundtrip = MockServer::HttpTemplate.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpClassCallback
  # -------------------------------------------------------------------
  describe MockServer::HttpClassCallback do
    it 'defaults all fields to nil' do
      cb = MockServer::HttpClassCallback.new
      expect(cb.callback_class).to be_nil
    end

    describe '.callback factory' do
      it 'creates callback with class name' do
        cb = MockServer::HttpClassCallback.callback(callback_class: 'com.example.MyCallback')
        expect(cb.callback_class).to eq('com.example.MyCallback')
      end
    end

    it 'serializes correctly' do
      cb = MockServer::HttpClassCallback.new(callback_class: 'com.test.Cb')
      expect(cb.to_h).to eq({ 'callbackClass' => 'com.test.Cb' })
    end

    it 'deserializes from hash' do
      cb = MockServer::HttpClassCallback.from_hash({ 'callbackClass' => 'com.test.X' })
      expect(cb.callback_class).to eq('com.test.X')
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::HttpClassCallback.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::HttpClassCallback.callback(callback_class: 'com.test.Y')
      roundtrip = MockServer::HttpClassCallback.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpObjectCallback
  # -------------------------------------------------------------------
  describe MockServer::HttpObjectCallback do
    it 'defaults all fields to nil' do
      cb = MockServer::HttpObjectCallback.new
      expect(cb.client_id).to be_nil
    end

    it 'serializes correctly' do
      cb = MockServer::HttpObjectCallback.new(client_id: 'abc-123', response_callback: true)
      expect(cb.to_h).to eq({ 'clientId' => 'abc-123', 'responseCallback' => true })
    end

    it 'deserializes from hash' do
      cb = MockServer::HttpObjectCallback.from_hash({ 'clientId' => 'x', 'responseCallback' => false })
      expect(cb.client_id).to eq('x')
      expect(cb.response_callback).to eq(false)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::HttpObjectCallback.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::HttpObjectCallback.new(client_id: 'test-id')
      roundtrip = MockServer::HttpObjectCallback.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpError
  # -------------------------------------------------------------------
  describe MockServer::HttpError do
    it 'defaults all fields to nil' do
      err = MockServer::HttpError.new
      expect(err.drop_connection).to be_nil
    end

    describe '.error factory' do
      it 'creates empty HttpError' do
        err = MockServer::HttpError.error
        expect(err).to be_a(MockServer::HttpError)
      end
    end

    it 'serializes correctly' do
      err = MockServer::HttpError.new(drop_connection: true, response_bytes: 'AAAA')
      expect(err.to_h).to eq({
        'dropConnection' => true,
        'responseBytes' => 'AAAA'
      })
    end

    it 'deserializes from hash' do
      err = MockServer::HttpError.from_hash({ 'dropConnection' => true })
      expect(err.drop_connection).to eq(true)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::HttpError.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::HttpError.new(drop_connection: true, response_bytes: 'XYZ')
      roundtrip = MockServer::HttpError.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpOverrideForwardedRequest
  # -------------------------------------------------------------------
  describe MockServer::HttpOverrideForwardedRequest do
    it 'defaults all fields to nil' do
      override = MockServer::HttpOverrideForwardedRequest.new
      expect(override.http_request).to be_nil
    end

    describe '.forward_overridden_request factory' do
      it 'creates override with request' do
        req = MockServer::HttpRequest.new(path: '/test')
        override = MockServer::HttpOverrideForwardedRequest.forward_overridden_request(request: req)
        expect(override.http_request.path).to eq('/test')
      end
    end

    it 'serializes correctly' do
      req = MockServer::HttpRequest.new(path: '/test')
      resp = MockServer::HttpResponse.new(status_code: 200)
      override = MockServer::HttpOverrideForwardedRequest.new(
        http_request: req,
        http_response: resp,
        request_modifier: { 'path' => { 'regex' => '/old', 'substitution' => '/new' } }
      )
      h = override.to_h
      expect(h['httpRequest']['path']).to eq('/test')
      expect(h['httpResponse']['statusCode']).to eq(200)
      expect(h['requestModifier']).to be_a(Hash)
    end

    it 'deserializes from hash' do
      data = {
        'httpRequest' => { 'method' => 'GET' },
        'httpResponse' => { 'statusCode' => 201 },
        'requestModifier' => { 'x' => 'y' }
      }
      override = MockServer::HttpOverrideForwardedRequest.from_hash(data)
      expect(override.http_request.method).to eq('GET')
      expect(override.http_response.status_code).to eq(201)
      expect(override.request_modifier).to eq({ 'x' => 'y' })
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::HttpOverrideForwardedRequest.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::HttpOverrideForwardedRequest.new(
        http_request: MockServer::HttpRequest.new(path: '/a'),
        response_modifier: { 'test' => true }
      )
      roundtrip = MockServer::HttpOverrideForwardedRequest.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpRequestAndHttpResponse
  # -------------------------------------------------------------------
  describe MockServer::HttpRequestAndHttpResponse do
    it 'defaults all fields to nil' do
      rr = MockServer::HttpRequestAndHttpResponse.new
      expect(rr.http_request).to be_nil
      expect(rr.http_response).to be_nil
    end

    it 'serializes correctly' do
      req = MockServer::HttpRequest.new(path: '/test')
      resp = MockServer::HttpResponse.new(status_code: 200)
      rr = MockServer::HttpRequestAndHttpResponse.new(http_request: req, http_response: resp)
      h = rr.to_h
      expect(h['httpRequest']['path']).to eq('/test')
      expect(h['httpResponse']['statusCode']).to eq(200)
    end

    it 'deserializes from hash' do
      data = {
        'httpRequest' => { 'path' => '/api' },
        'httpResponse' => { 'statusCode' => 201 }
      }
      rr = MockServer::HttpRequestAndHttpResponse.from_hash(data)
      expect(rr.http_request.path).to eq('/api')
      expect(rr.http_response.status_code).to eq(201)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::HttpRequestAndHttpResponse.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::HttpRequestAndHttpResponse.new(
        http_request: MockServer::HttpRequest.new(path: '/x'),
        http_response: MockServer::HttpResponse.new(status_code: 404)
      )
      roundtrip = MockServer::HttpRequestAndHttpResponse.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # ExpectationId
  # -------------------------------------------------------------------
  describe MockServer::ExpectationId do
    it 'defaults id to empty string' do
      eid = MockServer::ExpectationId.new
      expect(eid.id).to eq('')
    end

    it 'serializes correctly' do
      eid = MockServer::ExpectationId.new(id: 'test-123')
      expect(eid.to_h).to eq({ 'id' => 'test-123' })
    end

    it 'deserializes from hash' do
      eid = MockServer::ExpectationId.from_hash({ 'id' => 'abc' })
      expect(eid.id).to eq('abc')
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::ExpectationId.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::ExpectationId.new(id: 'xyz')
      roundtrip = MockServer::ExpectationId.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # Expectation
  # -------------------------------------------------------------------
  describe MockServer::Expectation do
    it 'defaults all fields to nil' do
      exp = MockServer::Expectation.new
      expect(exp.id).to be_nil
      expect(exp.http_request).to be_nil
    end

    it 'serializes a complete expectation' do
      exp = MockServer::Expectation.new(
        id: 'test-1',
        priority: 10,
        http_request: MockServer::HttpRequest.new(path: '/api'),
        http_response: MockServer::HttpResponse.new(status_code: 200),
        times: MockServer::Times.exactly(3),
        time_to_live: MockServer::TimeToLive.unlimited
      )
      h = exp.to_h
      expect(h['id']).to eq('test-1')
      expect(h['priority']).to eq(10)
      expect(h['httpRequest']['path']).to eq('/api')
      expect(h['httpResponse']['statusCode']).to eq(200)
      expect(h['times']['remainingTimes']).to eq(3)
      expect(h['timeToLive']['unlimited']).to eq(true)
    end

    it 'serializes all action types' do
      exp = MockServer::Expectation.new(
        http_forward: MockServer::HttpForward.new(host: 'backend'),
        http_error: MockServer::HttpError.new(drop_connection: true),
        http_response_template: MockServer::HttpTemplate.new(template: 'code'),
        http_response_class_callback: MockServer::HttpClassCallback.new(callback_class: 'x'),
        http_response_object_callback: MockServer::HttpObjectCallback.new(client_id: 'y'),
        http_forward_template: MockServer::HttpTemplate.new(template: 'fwd'),
        http_forward_class_callback: MockServer::HttpClassCallback.new(callback_class: 'z'),
        http_forward_object_callback: MockServer::HttpObjectCallback.new(client_id: 'w'),
        http_override_forwarded_request: MockServer::HttpOverrideForwardedRequest.new(
          http_request: MockServer::HttpRequest.new(path: '/override')
        )
      )
      h = exp.to_h
      expect(h['httpForward']['host']).to eq('backend')
      expect(h['httpError']['dropConnection']).to eq(true)
      expect(h['httpResponseTemplate']['template']).to eq('code')
      expect(h['httpResponseClassCallback']['callbackClass']).to eq('x')
      expect(h['httpResponseObjectCallback']['clientId']).to eq('y')
      expect(h['httpForwardTemplate']['template']).to eq('fwd')
      expect(h['httpForwardClassCallback']['callbackClass']).to eq('z')
      expect(h['httpForwardObjectCallback']['clientId']).to eq('w')
      expect(h['httpOverrideForwardedRequest']['httpRequest']['path']).to eq('/override')
    end

    it 'deserializes from hash' do
      data = {
        'id' => 'exp-1',
        'priority' => 5,
        'httpRequest' => { 'path' => '/test' },
        'httpResponse' => { 'statusCode' => 200 },
        'times' => { 'unlimited' => true },
        'timeToLive' => { 'unlimited' => true }
      }
      exp = MockServer::Expectation.from_hash(data)
      expect(exp.id).to eq('exp-1')
      expect(exp.priority).to eq(5)
      expect(exp.http_request.path).to eq('/test')
      expect(exp.http_response.status_code).to eq(200)
      expect(exp.times.unlimited).to eq(true)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::Expectation.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::Expectation.new(
        id: 'rt-test',
        http_request: MockServer::HttpRequest.new(method: 'GET', path: '/api'),
        http_response: MockServer::HttpResponse.new(status_code: 200, body: 'ok'),
        times: MockServer::Times.exactly(1)
      )
      roundtrip = MockServer::Expectation.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # SseEvent — falsy value handling
  # -------------------------------------------------------------------
  describe MockServer::SseEvent do
    it 'serializes retry of 0' do
      event = MockServer::SseEvent.new(event: 'ping', retry_ms: 0)
      h = event.to_h
      expect(h).to have_key('retry')
      expect(h['retry']).to eq(0)
    end

    it 'serializes empty string data' do
      event = MockServer::SseEvent.new(data: '')
      h = event.to_h
      expect(h).to have_key('data')
      expect(h['data']).to eq('')
    end

    it 'omits nil fields' do
      event = MockServer::SseEvent.new
      h = event.to_h
      expect(h).not_to have_key('event')
      expect(h).not_to have_key('data')
      expect(h).not_to have_key('id')
      expect(h).not_to have_key('retry')
    end

    it 'round-trips correctly' do
      original = MockServer::SseEvent.new(event: 'msg', data: 'hello', id: '1', retry_ms: 5000)
      roundtrip = MockServer::SseEvent.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpSseResponse — falsy value handling
  # -------------------------------------------------------------------
  describe MockServer::HttpSseResponse do
    it 'serializes status_code of 0' do
      resp = MockServer::HttpSseResponse.new(status_code: 0)
      h = resp.to_h
      expect(h).to have_key('statusCode')
      expect(h['statusCode']).to eq(0)
    end

    it 'serializes close_connection of false' do
      resp = MockServer::HttpSseResponse.new(close_connection: false)
      h = resp.to_h
      expect(h).to have_key('closeConnection')
      expect(h['closeConnection']).to eq(false)
    end

    it 'omits nil fields' do
      resp = MockServer::HttpSseResponse.new
      h = resp.to_h
      expect(h).not_to have_key('statusCode')
      expect(h).not_to have_key('closeConnection')
    end

    it 'round-trips correctly' do
      original = MockServer::HttpSseResponse.new(
        status_code: 200,
        close_connection: true,
        events: [MockServer::SseEvent.new(event: 'test', data: 'data')]
      )
      roundtrip = MockServer::HttpSseResponse.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # WebSocketMessage — falsy value handling
  # -------------------------------------------------------------------
  describe MockServer::WebSocketMessage do
    it 'serializes empty string text' do
      msg = MockServer::WebSocketMessage.new(text: '')
      h = msg.to_h
      expect(h).to have_key('text')
      expect(h['text']).to eq('')
    end

    it 'omits nil fields' do
      msg = MockServer::WebSocketMessage.new
      h = msg.to_h
      expect(h).not_to have_key('text')
      expect(h).not_to have_key('binary')
    end

    it 'round-trips correctly' do
      original = MockServer::WebSocketMessage.new(text: 'hello')
      roundtrip = MockServer::WebSocketMessage.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # HttpWebSocketResponse — falsy value handling
  # -------------------------------------------------------------------
  describe MockServer::HttpWebSocketResponse do
    it 'serializes close_connection of false' do
      resp = MockServer::HttpWebSocketResponse.new(close_connection: false)
      h = resp.to_h
      expect(h).to have_key('closeConnection')
      expect(h['closeConnection']).to eq(false)
    end

    it 'serializes empty string subprotocol' do
      resp = MockServer::HttpWebSocketResponse.new(subprotocol: '')
      h = resp.to_h
      expect(h).to have_key('subprotocol')
      expect(h['subprotocol']).to eq('')
    end

    it 'omits nil fields' do
      resp = MockServer::HttpWebSocketResponse.new
      h = resp.to_h
      expect(h).not_to have_key('subprotocol')
      expect(h).not_to have_key('closeConnection')
    end

    it 'round-trips correctly' do
      original = MockServer::HttpWebSocketResponse.new(
        subprotocol: 'graphql-ws',
        close_connection: true,
        messages: [MockServer::WebSocketMessage.new(text: 'hi')]
      )
      roundtrip = MockServer::HttpWebSocketResponse.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # OpenAPIDefinition
  # -------------------------------------------------------------------
  describe MockServer::OpenAPIDefinition do
    it 'defaults all fields to nil' do
      oad = MockServer::OpenAPIDefinition.new
      expect(oad.spec_url_or_payload).to be_nil
    end

    it 'serializes correctly' do
      oad = MockServer::OpenAPIDefinition.new(
        spec_url_or_payload: 'https://example.com/api.yaml',
        operation_id: 'getUser'
      )
      expect(oad.to_h).to eq({
        'specUrlOrPayload' => 'https://example.com/api.yaml',
        'operationId' => 'getUser'
      })
    end

    it 'deserializes from hash' do
      oad = MockServer::OpenAPIDefinition.from_hash({
        'specUrlOrPayload' => 'spec.yaml',
        'operationId' => 'createUser'
      })
      expect(oad.spec_url_or_payload).to eq('spec.yaml')
      expect(oad.operation_id).to eq('createUser')
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::OpenAPIDefinition.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::OpenAPIDefinition.new(
        spec_url_or_payload: 'http://spec.io',
        operation_id: 'op'
      )
      roundtrip = MockServer::OpenAPIDefinition.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # OpenAPIExpectation
  # -------------------------------------------------------------------
  describe MockServer::OpenAPIExpectation do
    it 'defaults all fields to nil' do
      oae = MockServer::OpenAPIExpectation.new
      expect(oae.spec_url_or_payload).to be_nil
    end

    it 'serializes correctly' do
      oae = MockServer::OpenAPIExpectation.new(
        spec_url_or_payload: 'http://spec.io',
        operations_and_responses: { 'getUser' => '200' }
      )
      expect(oae.to_h).to eq({
        'specUrlOrPayload' => 'http://spec.io',
        'operationsAndResponses' => { 'getUser' => '200' }
      })
    end

    it 'deserializes from hash' do
      oae = MockServer::OpenAPIExpectation.from_hash({
        'specUrlOrPayload' => 'spec.yaml',
        'operationsAndResponses' => { 'op1' => '201' }
      })
      expect(oae.spec_url_or_payload).to eq('spec.yaml')
      expect(oae.operations_and_responses).to eq({ 'op1' => '201' })
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::OpenAPIExpectation.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::OpenAPIExpectation.new(
        spec_url_or_payload: 'test.yaml',
        operations_and_responses: { 'a' => '200', 'b' => '404' }
      )
      roundtrip = MockServer::OpenAPIExpectation.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # VerificationTimes
  # -------------------------------------------------------------------
  describe MockServer::VerificationTimes do
    it 'defaults all fields to nil' do
      vt = MockServer::VerificationTimes.new
      expect(vt.at_least).to be_nil
      expect(vt.at_most).to be_nil
    end

    it 'serializes correctly' do
      vt = MockServer::VerificationTimes.new(at_least: 1, at_most: 5)
      expect(vt.to_h).to eq({ 'atLeast' => 1, 'atMost' => 5 })
    end

    describe 'factory methods' do
      it '.at_least creates VerificationTimes' do
        vt = MockServer::VerificationTimes.at_least(2)
        expect(vt.at_least).to eq(2)
        expect(vt.at_most).to be_nil
      end

      it '.at_most creates VerificationTimes' do
        vt = MockServer::VerificationTimes.at_most(5)
        expect(vt.at_most).to eq(5)
        expect(vt.at_least).to be_nil
      end

      it '.exactly creates VerificationTimes' do
        vt = MockServer::VerificationTimes.exactly(3)
        expect(vt.at_least).to eq(3)
        expect(vt.at_most).to eq(3)
      end

      it '.once creates VerificationTimes' do
        vt = MockServer::VerificationTimes.once
        expect(vt.at_least).to eq(1)
        expect(vt.at_most).to eq(1)
      end

      it '.between creates VerificationTimes' do
        vt = MockServer::VerificationTimes.between(2, 5)
        expect(vt.at_least).to eq(2)
        expect(vt.at_most).to eq(5)
      end
    end

    it 'deserializes from hash' do
      vt = MockServer::VerificationTimes.from_hash({ 'atLeast' => 1, 'atMost' => 3 })
      expect(vt.at_least).to eq(1)
      expect(vt.at_most).to eq(3)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::VerificationTimes.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::VerificationTimes.between(2, 10)
      roundtrip = MockServer::VerificationTimes.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # Verification
  # -------------------------------------------------------------------
  describe MockServer::Verification do
    it 'defaults all fields to nil' do
      v = MockServer::Verification.new
      expect(v.http_request).to be_nil
    end

    it 'serializes correctly' do
      v = MockServer::Verification.new(
        http_request: MockServer::HttpRequest.new(path: '/test'),
        times: MockServer::VerificationTimes.at_least(1),
        maximum_number_of_request_to_return_in_verification_failure: 10
      )
      h = v.to_h
      expect(h['httpRequest']['path']).to eq('/test')
      expect(h['times']['atLeast']).to eq(1)
      expect(h['maximumNumberOfRequestToReturnInVerificationFailure']).to eq(10)
    end

    it 'serializes with expectation_id' do
      v = MockServer::Verification.new(
        expectation_id: MockServer::ExpectationId.new(id: 'exp-1')
      )
      expect(v.to_h['expectationId']).to eq({ 'id' => 'exp-1' })
    end

    it 'deserializes from hash' do
      data = {
        'httpRequest' => { 'path' => '/api' },
        'times' => { 'atLeast' => 2 },
        'expectationId' => { 'id' => 'x' }
      }
      v = MockServer::Verification.from_hash(data)
      expect(v.http_request.path).to eq('/api')
      expect(v.times.at_least).to eq(2)
      expect(v.expectation_id.id).to eq('x')
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::Verification.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::Verification.new(
        http_request: MockServer::HttpRequest.new(path: '/x'),
        times: MockServer::VerificationTimes.once
      )
      roundtrip = MockServer::Verification.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # VerificationSequence
  # -------------------------------------------------------------------
  describe MockServer::VerificationSequence do
    it 'defaults all fields to nil' do
      vs = MockServer::VerificationSequence.new
      expect(vs.http_requests).to be_nil
      expect(vs.expectation_ids).to be_nil
    end

    it 'serializes http_requests' do
      vs = MockServer::VerificationSequence.new(
        http_requests: [
          MockServer::HttpRequest.new(path: '/a'),
          MockServer::HttpRequest.new(path: '/b')
        ]
      )
      h = vs.to_h
      expect(h['httpRequests'].length).to eq(2)
      expect(h['httpRequests'][0]['path']).to eq('/a')
    end

    it 'serializes expectation_ids' do
      vs = MockServer::VerificationSequence.new(
        expectation_ids: [
          MockServer::ExpectationId.new(id: 'e1'),
          MockServer::ExpectationId.new(id: 'e2')
        ]
      )
      h = vs.to_h
      expect(h['expectationIds'].length).to eq(2)
    end

    it 'deserializes from hash' do
      data = {
        'httpRequests' => [{ 'path' => '/x' }, { 'path' => '/y' }],
        'expectationIds' => [{ 'id' => 'a' }]
      }
      vs = MockServer::VerificationSequence.from_hash(data)
      expect(vs.http_requests.length).to eq(2)
      expect(vs.expectation_ids.length).to eq(1)
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::VerificationSequence.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::VerificationSequence.new(
        http_requests: [MockServer::HttpRequest.new(path: '/test')]
      )
      roundtrip = MockServer::VerificationSequence.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # Ports
  # -------------------------------------------------------------------
  describe MockServer::Ports do
    it 'defaults to empty ports array' do
      ports = MockServer::Ports.new
      expect(ports.ports).to eq([])
    end

    it 'serializes correctly' do
      ports = MockServer::Ports.new(ports: [1080, 1081])
      expect(ports.to_h).to eq({ 'ports' => [1080, 1081] })
    end

    it 'deserializes from hash' do
      ports = MockServer::Ports.from_hash({ 'ports' => [8080] })
      expect(ports.ports).to eq([8080])
    end

    it 'returns nil from_hash when data is nil' do
      expect(MockServer::Ports.from_hash(nil)).to be_nil
    end

    it 'round-trips correctly' do
      original = MockServer::Ports.new(ports: [1080, 1081, 1082])
      roundtrip = MockServer::Ports.from_hash(original.to_h)
      expect(roundtrip.to_h).to eq(original.to_h)
    end
  end

  # -------------------------------------------------------------------
  # RequestDefinition alias
  # -------------------------------------------------------------------
  describe 'RequestDefinition alias' do
    it 'is the same as HttpRequest' do
      expect(MockServer::RequestDefinition).to eq(MockServer::HttpRequest)
    end
  end

  # -------------------------------------------------------------------
  # Helper functions
  # -------------------------------------------------------------------
  describe 'MockServer.to_camel' do
    it 'converts known field names' do
      expect(MockServer.to_camel('status_code')).to eq('statusCode')
      expect(MockServer.to_camel('time_to_live')).to eq('timeToLive')
      expect(MockServer.to_camel('not_body')).to eq('not')
    end

    it 'converts generic snake_case' do
      expect(MockServer.to_camel('some_field')).to eq('someField')
    end
  end

  describe 'MockServer.from_camel' do
    it 'converts known camelCase names' do
      expect(MockServer.from_camel('statusCode')).to eq('status_code')
      expect(MockServer.from_camel('timeToLive')).to eq('time_to_live')
    end

    it 'converts generic camelCase' do
      expect(MockServer.from_camel('someField')).to eq('some_field')
    end
  end

  describe 'MockServer.strip_none' do
    it 'removes nil values from hash' do
      result = MockServer.strip_none({ 'a' => 1, 'b' => nil, 'c' => 'test' })
      expect(result).to eq({ 'a' => 1, 'c' => 'test' })
    end

    it 'keeps false and empty string values' do
      result = MockServer.strip_none({ 'a' => false, 'b' => '', 'c' => nil })
      expect(result).to eq({ 'a' => false, 'b' => '' })
    end
  end
end
