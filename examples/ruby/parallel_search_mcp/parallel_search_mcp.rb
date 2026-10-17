#!/usr/bin/env ruby
# frozen_string_literal: true

# MockServer Ruby client -- capture and replay a remote MCP server.
#
# Records an MCP (Model Context Protocol) client's JSON-RPC calls to the live
# Parallel Search MCP server (https://search.parallel.ai/mcp) through MockServer,
# then replays them offline from expectations that match on the JSON-RPC method
# and echo the caller's JSON-RPC id with a Velocity template.
#
#   ruby parallel_search_mcp.rb                    # replay the shared fixture (offline)
#   MCP_CAPTURE=true ruby parallel_search_mcp.rb   # capture live, then replay the capture
#
# Run against a MockServer on localhost:1080 (override with MOCKSERVER_HOST /
# MOCKSERVER_PORT). An optional PARALLEL_API_KEY is sent upstream during capture.

require 'mockserver-client'
require 'net/http'
require 'json'

include MockServer

MOCK_HOST = ENV.fetch('MOCKSERVER_HOST', 'localhost')
MOCK_PORT = Integer(ENV.fetch('MOCKSERVER_PORT', '1080'))
MCP_URI = URI("http://#{MOCK_HOST}:#{MOCK_PORT}/mcp")
UPSTREAM_HOST = 'search.parallel.ai'
FIXTURE = File.expand_path('../../json/parallel_search_mcp/replay_expectations.json', __dir__)

def check(condition, message)
  abort("FAILED: #{message}") unless condition
end

# Minimal MCP Streamable HTTP client: one JSON-RPC message per POST.
class McpClient
  def initialize(uri, api_key = nil)
    @uri = uri
    @api_key = api_key
    @next_id = 1
  end

  def send_message(method, params = nil, notification: false)
    message = { 'jsonrpc' => '2.0', 'method' => method }
    message['params'] = params unless params.nil?
    unless notification
      message['id'] = @next_id
      @next_id += 1
    end
    request = Net::HTTP::Post.new(@uri)
    request['Content-Type'] = 'application/json'
    request['Accept'] = 'application/json, text/event-stream'
    # an explicit User-Agent: Parallel's CDN rejects some library defaults
    request['User-Agent'] = 'mockserver-example/1.0'
    request['Authorization'] = "Bearer #{@api_key}" if @api_key && !@api_key.empty?
    request.body = JSON.generate(message)
    response = Net::HTTP.start(@uri.host, @uri.port, read_timeout: 120) { |http| http.request(request) }
    check(response.is_a?(Net::HTTPSuccess), "#{method}: HTTP #{response.code} #{response.body}")
    return nil if notification

    reply = JSON.parse(response.body)
    check(reply['id'] == message['id'], "#{method}: id #{reply['id'].inspect} != #{message['id']}")
    reply['result']
  end

  def call_tool(name, arguments)
    result = send_message('tools/call', { 'name' => name, 'arguments' => arguments })
    JSON.parse(result['content'][0]['text']) # Parallel returns its payload as JSON text
  end
end

# The application under test: initialize, list tools, search, then fetch.
def run_session(mcp)
  init = mcp.send_message('initialize', { 'protocolVersion' => '2025-06-18', 'capabilities' => {},
                                          'clientInfo' => { 'name' => 'mockserver-example', 'version' => '1.0.0' } })
  mcp.send_message('notifications/initialized', notification: true)
  tools = mcp.send_message('tools/list', {})['tools'].map { |tool| tool['name'] }
  search = mcp.call_tool('web_search', { 'objective' => 'What is MockServer?',
                                         'search_queries' => ['MockServer mock HTTP server'] })
  fetch = mcp.call_tool('web_fetch', { 'urls' => ['https://www.mock-server.com/'],
                                       'objective' => 'What is MockServer?' })
  [init['serverInfo']['name'], tools, search['results'], fetch['results']]
end

# MockServer records a JSON body as a string, or as {"json": ...} / {"string": ...}.
def body_json(body)
  body = body.key?('json') ? body['json'] : body.fetch('string', body) if body.is_a?(Hash)
  return body unless body.is_a?(String)

  body.empty? ? nil : JSON.parse(body)
end

# Turn one recorded JSON-RPC exchange into a replay expectation that echoes the caller's id.
def replay_expectation(recorded)
  request_body = body_json(recorded['httpRequest']['body'])
  status_code = recorded['httpResponse'].fetch('statusCode', 200)
  response_body = body_json(recorded['httpResponse']['body'])
  method = request_body['method']
  matcher = if method == 'tools/call'
              tool = request_body['params']['name']
              Body.json_path("$[?(@.method == 'tools/call' && @.params.name == '#{tool}')]")
            else
              JsonRpcBody.new(method_name: method)
            end
  request = HttpRequest.new(method: 'POST', path: '/mcp', body: matcher)
  if response_body.nil? || response_body.empty? # a notification: replay the bare status (202 Accepted)
    return Expectation.new(http_request: request, http_response: HttpResponse.new(status_code: status_code))
  end

  key = response_body.key?('result') ? 'result' : 'error'
  # escape Velocity metacharacters so recorded text (e.g. markdown "#") renders literally
  # "\" goes last because the $ and # replacements contain none, while its own contains a "$"
  recorded_json = JSON.generate(response_body[key]).gsub('$', '${esc.d}').gsub('#', '${esc.h}').gsub('\\', '${esc.b}')
  template = format('{"statusCode": %<status>d, "headers": [{"name": "Content-Type", "values": ["application/json"]}], ' \
                    '"body": {"jsonrpc": "2.0", "id": $!{request.jsonRpcRawId}, "%<key>s": %<recorded>s}}',
                    status: status_code, key: key, recorded: recorded_json)
  Expectation.new(http_request: request,
                  http_response_template: HttpTemplate.new(template_type: 'VELOCITY', template: template))
end

def capture(client)
  client.reset
  # keep credentials (e.g. the forwarded Authorization header) out of MockServer's own log
  client.update_configuration('{"redactSecretsInLog": true}')
  client.when(HttpRequest.request(path: '/mcp')).forward(HttpOverrideForwardedRequest.new(
    request_override: HttpRequest.new(socket_address: SocketAddress.new(host: UPSTREAM_HOST, port: 443, scheme: 'HTTPS'))
                                 .with_header('Host', UPSTREAM_HOST)
  ))
  puts "capture: forwarding /mcp to https://#{UPSTREAM_HOST}/mcp"
  server, tools, search, fetch = run_session(McpClient.new(MCP_URI, ENV.fetch('PARALLEL_API_KEY', nil)))
  puts "capture: #{server}, tools #{tools.inspect}, #{search.length} search results, fetched #{fetch[0]['url']}"
  # MockServer records every proxied exchange as an expectation; rewrite each one for replay
  recorded = client.retrieve_recorded_expectations(request: HttpRequest.request(path: '/mcp'))
  expectations = recorded.map { |r| replay_expectation(r.to_h) }
  puts "capture: recorded #{recorded.length} exchanges -> #{expectations.length} replay expectations"
  expectations
end

def replay(client, expectations, source)
  client.reset # drops the forward expectation and the recorded log
  client.upsert(*expectations)
  puts "replay: loaded #{expectations.length} expectations from #{source}"
  server, tools, search, fetch = run_session(McpClient.new(MCP_URI))
  check((%w[web_search web_fetch] - tools).empty?, "tools/list returned #{tools.inspect}")
  check(!search.empty? && search.all? { |result| result['url'] && !result['url'].empty? },
        'web_search returned no results')
  check(!fetch.empty? && fetch[0]['excerpts'] && !fetch[0]['excerpts'][0].to_s.empty?, 'web_fetch returned no content')
  # only proxied traffic is recorded, so an empty list means nothing reached Parallel
  forwarded = client.retrieve_recorded_expectations(request: HttpRequest.request(path: '/mcp'))
  check(forwarded.empty?, "#{forwarded.length} requests were forwarded upstream during replay")
  puts "replay: #{server}, tools #{tools.inspect}"
  puts "replay: web_search -> #{search.length} results, first #{search[0]['url']}"
  puts "replay: web_fetch -> #{fetch[0]['url']} (#{fetch[0]['excerpts'][0].length} chars)"
  puts "replay: 0 requests forwarded to #{UPSTREAM_HOST} -- answered from recorded fixtures"
end

client = Client.new(MOCK_HOST, MOCK_PORT)
if ENV['MCP_CAPTURE'] == 'true'
  replay(client, capture(client), 'the live capture')
else
  fixture = JSON.parse(File.read(FIXTURE)).map { |e| Expectation.from_hash(e) }
  replay(client, fixture, File.basename(FIXTURE))
end
