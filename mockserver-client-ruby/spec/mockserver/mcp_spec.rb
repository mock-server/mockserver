# frozen_string_literal: true

RSpec.describe MockServer::MCP do
  def find_request(expectations, method)
    expectations.find do |e|
      body = e['httpRequest']['body']
      body && body['type'] == 'JSON_RPC' && body['method'] == method
    end
  end

  describe '.mcp_mock' do
    it 'emits initialize, ping and notifications/initialized by default' do
      expectations = described_class.mcp_mock.build

      expect(expectations.length).to eq(3)

      initialize = find_request(expectations, 'initialize')
      expect(initialize['httpRequest']).to eq(
        'method' => 'POST',
        'path' => '/mcp',
        'body' => { 'type' => 'JSON_RPC', 'method' => 'initialize' }
      )

      template = initialize['httpResponseTemplate']
      expect(template['templateType']).to eq('VELOCITY')
      # Velocity template echoes the JSON-RPC id and wraps the result.
      expect(template['template']).to include('$!{request.jsonRpcRawId}')
      expect(template['template']).to include('"protocolVersion": "2025-03-26"')
      expect(template['template']).to include('"serverInfo": {"name": "MockMCPServer", "version": "1.0.0"}')
      # No capabilities advertised when nothing is configured.
      expect(template['template']).to include('"capabilities": {}')

      ping = find_request(expectations, 'ping')
      expect(ping['httpResponseTemplate']['template']).to include('"result": {}')

      notif = find_request(expectations, 'notifications/initialized')
      expect(notif['httpResponse']).to eq(
        'statusCode' => 200,
        'headers' => [{ 'name' => 'Content-Type', 'values' => ['application/json'] }],
        'body' => '{}'
      )
    end

    it 'advertises tools capability and emits tools/list + tools/call with JSON_PATH match' do
      expectations = described_class.mcp_mock('/mcp')
                                    .with_tool('get_weather')
                                      .with_description('Get the weather')
                                      .with_input_schema('{"type":"object", "properties": {"city": {"type": "string"}}}')
                                      .responding_with('72F and sunny')
                                    .and_then
                                    .build

      initialize = find_request(expectations, 'initialize')
      expect(initialize['httpResponseTemplate']['template']).to include('"tools": {"listChanged": false}')

      tools_list = find_request(expectations, 'tools/list')
      template = tools_list['httpResponseTemplate']['template']
      expect(template).to include('"name": "get_weather"')
      expect(template).to include('"description": "Get the weather"')
      # inputSchema is validated + compacted (no whitespace).
      expect(template).to include('"inputSchema": {"type":"object","properties":{"city":{"type":"string"}}}')

      tools_call = expectations.find do |e|
        e['httpRequest']['body'] && e['httpRequest']['body']['type'] == 'JSON_PATH'
      end
      expect(tools_call['httpRequest']['body']['jsonPath']).to eq(
        "$[?(@.method == 'tools/call' && @.params.name == 'get_weather')]"
      )
      call_template = tools_call['httpResponseTemplate']['template']
      expect(call_template).to include('"content": [{"type": "text", "text": "72F and sunny"}]')
      expect(call_template).to include('"isError": false')
    end

    it 'emits resources/list + resources/read with the uri JSON_PATH match' do
      expectations = described_class.mcp_mock
                                    .with_resource('file:///config.json')
                                      .with_name('config')
                                      .with_mime_type('application/json')
                                      .with_content('{"debug":true}')
                                    .and_then
                                    .build

      initialize = find_request(expectations, 'initialize')
      expect(initialize['httpResponseTemplate']['template']).to include(
        '"resources": {"subscribe": false, "listChanged": false}'
      )

      read = expectations.find do |e|
        e['httpRequest']['body'] &&
          e['httpRequest']['body']['type'] == 'JSON_PATH' &&
          e['httpRequest']['body']['jsonPath'].include?('resources/read')
      end
      expect(read['httpRequest']['body']['jsonPath']).to eq(
        "$[?(@.method == 'resources/read' && @.params.uri == 'file:///config.json')]"
      )
      expect(read['httpResponseTemplate']['template']).to include('"mimeType": "application/json"')
    end

    it 'emits prompts/list with arguments and prompts/get with messages' do
      expectations = described_class.mcp_mock
                                    .with_prompt('greet')
                                      .with_description('Greeting prompt')
                                      .with_argument('name', 'the name', true)
                                      .responding_with('user', 'Hello there')
                                    .and_then
                                    .build

      prompts_list = find_request(expectations, 'prompts/list')
      template = prompts_list['httpResponseTemplate']['template']
      expect(template).to include('"name": "greet"')
      expect(template).to include('"arguments": [{"name": "name", "description": "the name", "required": true}]')

      get = expectations.find do |e|
        e['httpRequest']['body'] &&
          e['httpRequest']['body']['type'] == 'JSON_PATH' &&
          e['httpRequest']['body']['jsonPath'].include?('prompts/get')
      end
      expect(get['httpResponseTemplate']['template']).to include(
        '"messages": [{"role": "user", "content": {"type": "text", "text": "Hello there"}}]'
      )
    end

    it 'escapes velocity metacharacters and JSON-path single quotes' do
      expectations = described_class.mcp_mock
                                    .with_tool("o'brien$#")
                                      .responding_with('cost is $5 #1')
                                    .and_then
                                    .build

      call = expectations.find do |e|
        e['httpRequest']['body'] && e['httpRequest']['body']['type'] == 'JSON_PATH'
      end
      expect(call['httpRequest']['body']['jsonPath']).to include("o\\'brien")
      template = call['httpResponseTemplate']['template']
      expect(template).to include('cost is ${esc.d}5 ${esc.h}1')
    end

    it 'Velocity-escapes trailing, mixed and very long backslash runs' do
      expect(MockServer::MCP.escape_velocity('end\\\\')).to eq('end${esc.b}${esc.b}')
      expect(MockServer::MCP.escape_velocity('a\\b\\$c#')).to eq('a\\b${esc.b}${esc.d}c${esc.h}')
      expect(MockServer::MCP.escape_velocity("a\\\n")).to eq("a\\\n")
      run = '\\' * 100_000
      expect(MockServer::MCP.escape_velocity("#{run}x")).to eq("#{run}x")
      expect(MockServer::MCP.escape_velocity("#{run}$")).to eq(('${esc.b}' * 100_000) + '${esc.d}')
      expect(MockServer::MCP.escape_velocity(run)).to eq('${esc.b}' * 100_000)
    end

    it 'Velocity-escapes backslashes only before $, # or the end of the value' do
      # shared cross-client table, asserted identically in all eight client builders
      {
        'a\\$b' => 'a${esc.b}${esc.d}b',
        'a\\#b' => 'a${esc.b}${esc.h}b',
        'end\\' => 'end${esc.b}',
        '\\\\$' => '${esc.b}${esc.b}${esc.d}',
        '${esc.b}' => '${esc.d}{esc.b}',
        'é\\$ü' => 'é${esc.b}${esc.d}ü',
        'a\\nb \\"q\\"' => 'a\\nb \\"q\\"',
        '\\\\x' => '\\\\x',
        '$#' => '${esc.d}${esc.h}',
        '' => ''
      }.each do |input, expected|
        expect(MockServer::MCP.escape_velocity(input)).to eq(expected), "input: #{input}"
        expect(MockServer::A2A.escape_velocity(input)).to eq(expected), "input: #{input}"
      end

      expectations = described_class.mcp_mock.with_tool('t').responding_with('a\\$b').and_then.build
      call = expectations.find do |e|
        e['httpRequest']['body'] && e['httpRequest']['body']['type'] == 'JSON_PATH'
      end
      # JSON-escaped "a\\$b": both backslashes precede '$', so all are escaped
      expect(call['httpResponseTemplate']['template']).to include('a${esc.b}${esc.b}${esc.d}b')
    end

    it 'raises on invalid input schema JSON' do
      expect do
        described_class.mcp_mock.with_tool('t').with_input_schema('{not json').and_then.build
      end.to raise_error(ArgumentError, /Invalid JSON/)
    end
  end

  describe 'apply_to' do
    it 'registers all built expectations via client.upsert' do
      client = instance_double(MockServer::Client)
      allow(client).to receive(:upsert)

      described_class.mcp_mock
                     .with_tool('t').responding_with('ok')
                     .and_then
                     .apply_to(client)

      expect(client).to have_received(:upsert) do |*args|
        expect(args.length).to be >= 4 # initialize, ping, notifications, tools/list, tools/call
        expect(args.first.to_h['httpRequest']['body']['method']).to eq('initialize')
      end
    end
  end
end
