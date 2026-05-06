# MockServer Ruby Client

Hand-written Ruby client for [MockServer](https://www.mock-server.com) with full REST API, fluent builder DSL, and WebSocket callback support.

## Installation

Add to your Gemfile:

```ruby
gem 'mockserver-client', '~> 5.16'
```

Or install directly:

```bash
gem install mockserver-client
```

## Quick Start

```ruby
require 'mockserver-client'

# Create a client
client = MockServer::Client.new('localhost', 1080)

# Set up an expectation using the fluent API
client.when(
  MockServer::HttpRequest.request(path: '/hello')
    .with_method('GET')
).respond(
  MockServer::HttpResponse.response(body: 'world', status_code: 200)
)

# Verify a request was received
client.verify(
  MockServer::HttpRequest.request(path: '/hello'),
  times: MockServer::VerificationTimes.at_least(1)
)

# Clean up
client.reset
client.close
```

## Block Form

```ruby
MockServer::Client.new('localhost', 1080) do |client|
  client.when(
    MockServer::HttpRequest.request(path: '/api/test')
  ).respond(
    MockServer::HttpResponse.response(body: '{"status":"ok"}', status_code: 200)
  )
end
# Client is automatically closed when the block exits
```

## WebSocket Callbacks

```ruby
client = MockServer::Client.new('localhost', 1080)

# Response callback - dynamically generate responses
client.mock_with_callback(
  MockServer::HttpRequest.request(path: '/dynamic'),
  ->(request) {
    MockServer::HttpResponse.new(
      status_code: 200,
      body: "Echo: #{request.path}"
    )
  }
)

# Forward callback - modify requests before forwarding
client.mock_with_forward_callback(
  MockServer::HttpRequest.request(path: '/proxy'),
  ->(request) {
    request.with_header('X-Proxied', 'true')
  }
)

client.close
```

## Models

All 25 domain model classes are available under the `MockServer` module:

- `Delay`, `Times`, `TimeToLive`
- `KeyToMultiValue`, `Body`, `SocketAddress`
- `HttpRequest`, `HttpResponse`, `HttpForward`, `HttpTemplate`
- `HttpClassCallback`, `HttpObjectCallback`, `HttpError`
- `HttpOverrideForwardedRequest`, `HttpRequestAndHttpResponse`
- `ConnectionOptions`
- `Expectation`, `ExpectationId`
- `OpenAPIDefinition`, `OpenAPIExpectation`
- `Verification`, `VerificationSequence`, `VerificationTimes`
- `Ports`
- `RequestDefinition` (alias for `HttpRequest`)

## License

Apache-2.0
