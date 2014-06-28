# Mockserver Client

A Ruby client for [MockServer](http://www.mock-server.com) project. This client follows the Java client's fluent style closely by using Ruby blocks.

## Installation

Add this line to your application's Gemfile:

    gem 'mockserver-client'

And then execute:

    $ bundle

Or install it yourself as:

    $ gem install mockserver-client

## Usage

The usage notes here compare the Java syntax with the Ruby DSL for the various actions the MockServer client/proxy client supports. The Ruby code here assumes you have included `MockServer` and `MockServer::Model::DSL` modules. So these lines should  typically be at the top of your code:

```ruby
include MockServer
include MockServer::Model::DSL
```

### Create Expectations

##### Java
```java

new MockServerClient("localhost", 9999)
        .when(
                request()
                        .withMethod("POST")
                        .withPath("/login")
                        .withQueryStringParameters(
                                new Parameter("returnUrl", "/account")
                        )
                        .withCookies(
                                new Cookie("sessionId", "2By8LOhBmaW5nZXJwcmludCIlMDAzMW")
                        )
                        .withBody("{username: 'foo', password: 'bar'}"),
                Times.exactly(1)
        )
        .respond(
                response()
                        .withStatusCode(401)
                        .withHeaders(
                                new Header("Content-Type", "application/json; charset=utf-8"),
                                new Header("Cache-Control", "public, max-age=86400")
                        )
                        .withBody("{ message: 'incorrect username and password combination' }")
                        .withDelay(new Delay(TimeUnit.SECONDS, 1))
        );
```

##### Ruby

```ruby

client = MockServerClient.new('localhost', 9999)
expectation = expectation do |expectation|
     expectation.request do |request|
        request.method = 'POST'
        request.path = '/login'
        request.query_parameters << parameter('returnUrl', '/account')
        request.cookies = [cookie('sessionId', '2By8LOhBmaW5nZXJwcmludCIlMDAzMW')]
        request.body = exact("{username: 'foo', password: 'bar'}")
     end

    expectation.response do |response|
        response.status_code = 401
        response.headers << header('Content-Type', 'application/json; charset=utf-8')
        response.headers << header('Cache-Control', 'public, max-age=86400')
        response.body = body("{ message: 'incorrect username and password combination' }")
        response.delay = delay_by(:SECONDS, 1)
    end
end
client.register(expectation)
```

### Clear & Reset Server

##### Java

```java
mockServerClient.clear(
        request()
                .withMethod("POST")
                .withPath("/login")
);
mockServerClient.reset();
```

##### Ruby

 ```ruby
 client.clear(request('POST', '/login'))
 client.reset
 ```
### Verifying Behavior

##### Java

```java
new ProxyClient("localhost", 9090).verify(
        request()
                .withMethod("POST")
                .withPath("/login")
                .withBody(exact("{username: 'foo', password: 'bar'}"))
                .withCookies(
                        new Cookie("sessionId", ".*")
                ),
        Times.exactly(1)
);
```

##### Ruby

```ruby

# Not providing times here because the default is exactly(1) i.e. the second argument to verify method
ProxyClient.new('localhost', 9090).verify(request(:POST, '/login') do |request|
    request.body = exact("{username: 'foo', password: 'bar'}")
    request.cookies = [cookie("sessionId", ".*")]
end)
```


### Analyzing Behavior

##### Java

```java
new ProxyClient("localhost", 9090).dumpToLogAsJava(
        request()
                .withMethod("POST")
                .withPath("/login")
);
```

##### Ruby
```ruby
# Second argument is true to set output to Java; false to set output to default JSON (default)
ProxyClient.new('localhost', 9090).dump_log(request(:POST, '/login'), true)
```

## Complete Ruby DSL
The DSL is provided via the `MockServer::Model::DSL` module. Include this module in your code to make the DSL available.

Request
* **request**: Used to build a request object. If a block is passed, will configure request first and then return the configured request. Example: `request(:POST, '/login') {|r| r.headers << header("Content-Type", "application/json")}`.
* **http_request**: Alias for `request`.

Body
* **body**: Create a string body object (use in a response). Example: `body("unaccepted")`.
* **exact**: Create a body of type `EXACT`. Example: `exact('{"reason": "unauthorized"}')`.
* **regex**: Create a body of type `REGEX`. Example: `regex('username[a-z]{4}')`.
* **xpath**: Used to create a body of type `XPATH`. Example: `xpath("/element[key = 'some_key' and value = 'some_value']")`.
* **parameterized**; Create a body to type `PARAMETERS`. Example `parameterized(parameter('someValue', 1, 2), parameter('otherValue', 4, 5))`.

Parameters
* **parameter**: Create a generic parameter. Example: `parameter('key', 'value1' , 'value2')`.
* **cookie**: Create a cookie (same as `parameter` above but exists as syntactic sugar). Example: `cookie('sessionId', 'sessionid1ldj')`.
* **header**: Create a header (same as `parameter` above but exists as syntactic sugar). Example: `header('Content-Type', 'application/json')`.

Forward
* **forward**: Create a forwarding response. If a block is passed, will configure forward response first and then return the configured object. Example: `forward {|f| f.scheme = 'HTTPS' }`.
* **http_forward**: Alias for `forward`.

Response
* **response**: Create a response object. If a block is passed, will configure response first and then return the configured response. Example: `response {|r| r.status_code = 201 }`.
* **http_response**: Alias for `response`.

Delay
* **delay_by**. Create a delay object in a response. Example : `delay_by(:MICROSECONDS, 20)`.

Times (used in Expectation)
* **times**: Create an 'times' object. If a block is passed, will configure the object first before returning it. Example: `times {|t| t.unlimited = false }`.
* **unlimited**: Create an object with unlimited repeats. Example: `unlimited()`. (No parameters).
* **once**. Create an object that repeats only once. Example: `once()`. (No parameters).
* **exactly**. Create an object that repeats exactly the number of times specified. Example: `exactly(2)`.
* **at_least**: Create an object that repeats at least the given number of times. (Use in verify). Example: `at_least(2)`.

Expectation (use in register)
* **expectation**: Create an expectation object. If a block is passed, will configure the object first before returning it. Example: `expectation {|e| e.request {|r| r.path = "index.html} }`. 
Getter methods for `request`, `response` and `forward` methods will optionally accept a block. If block is passed object is configured before it is returned. The attribute `times` has conventional getter and setter methods.

## CLI

This gem comes with a command line interface which allow you to run the Mock Server calls from the command line. When this gem is installed, you will have the `mockserver` executable on your PATH. Type `mockserver --help` and you will get this output: 

```
mockserver --help 

Commands: 
  mockserver clear           # Clears all stored mock request/responses from server.
  mockserver dump_log        # Dumps the matching request to the mock server logs.
  mockserver help [COMMAND]  # Describe available commands or one specific command
  mockserver register        # Register an expectation with the mock server.
  mockserver reset           # Resets the server clearing all data.
  mockserver retrieve        # Retrieve the list of requests that have been made to the mock/proxy server.
  mockserver verify          # Verify that a request has been made the specified number of times to the server.

Options:
  -h, --host=HOST    # The host for the MockServer client.
                     # Default: localhost
  -p, --port=N       # The port for the MockServer client.
                     # Default: 8080
  -d, [--data=DATA]  # A JSON or YAML file containing the request payload.
```

To get help for an individual command, e.g. `dump_log`, you would do:

```
mockserver --help dump_log

Usage:
  mockserver dump_log

Options:
  -j, [--java], [--no-java]  # A switch to turn Java format for logs on/off.
  -h, --host=HOST            # The host for the MockServer client.
                             # Default: localhost
  -p, --port=N               # The port for the MockServer client.
                             # Default: 8080
  -d, [--data=DATA]          # A JSON or YAML file containing the request payload.

Dumps the matching request to the mock server logs.
```

Here is an example on how you would run the command:

```
mockserver dump_log -j true

Running with parameters:
	host: localhost
	port: 8080
	java: true

[2014-06-21 09:23:32] DEBUG [MockServerClient] Sending dump log request to mockserver
[2014-06-21 09:23:32] DEBUG [MockServerClient] URL: /dumpToLog?type=java. Payload: {}
[2014-06-21 09:23:32] DEBUG [MockServerClient] Got dump to log response: 202
```

## Contributing

1. Fork it ( https://github.com/[my-github-username]/mockserver-client/fork )
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create a new Pull Request
6. **IMPORTANT**: Keep code coverage high. Preferably above 95%. This project uses SimpleCov for code coverage which reports percentage coverage.
