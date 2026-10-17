# frozen_string_literal: true

RSpec.describe MockServer::A2A do
  def find_json_rpc(expectations, method)
    expectations.find do |e|
      body = e['httpRequest']['body']
      body && body['type'] == 'JSON_RPC' && body['method'] == method
    end
  end

  def find_json_path(expectations, needle)
    expectations.find do |e|
      body = e['httpRequest']['body']
      body && body['type'] == 'JSON_PATH' && body['jsonPath'].include?(needle)
    end
  end

  def agent_card(expectations)
    expectations.find { |e| e['httpRequest']['method'] == 'GET' }
  end

  describe '.a2a_mock' do
    it 'emits agent-card, tasks/send, tasks/get and tasks/cancel by default' do
      expectations = described_class.a2a_mock.build

      expect(expectations.length).to eq(4)

      card = agent_card(expectations)
      expect(card['httpRequest']).to eq(
        'method' => 'GET',
        'path' => '/.well-known/agent.json'
      )
      response = card['httpResponse']
      expect(response['statusCode']).to eq(200)
      expect(response['headers']).to eq([{ 'name' => 'Content-Type', 'values' => ['application/json'] }])
      body = response['body']
      expect(body).to include('"name": "MockAgent"')
      expect(body).to include('"description": "A mock A2A agent"')
      expect(body).to include('"version": "1.0.0"')
      expect(body).to include('"url": "http://localhost/a2a"')
      expect(body).to include(
        '"capabilities": {"streaming": false, "pushNotifications": false, "stateTransitionHistory": false}'
      )
      expect(body).to include('"skills": []')

      send = find_json_rpc(expectations, 'tasks/send')
      template = send['httpResponseTemplate']
      expect(template['templateType']).to eq('VELOCITY')
      expect(template['template']).to include('$!{request.jsonRpcRawId}')
      expect(template['template']).to include('"status": {"state": "completed"}')
      expect(template['template']).to include('"text": "Task completed successfully"')

      get = find_json_rpc(expectations, 'tasks/get')
      expect(get['httpResponseTemplate']['template']).to include('"status": {"state": "completed"}')

      cancel = find_json_rpc(expectations, 'tasks/cancel')
      expect(cancel['httpResponseTemplate']['template']).to include('"status": {"state": "canceled"}')
    end

    it 'honours a custom path, agent identity and default task response' do
      expectations = described_class.a2a_mock('/agent')
                                    .with_agent_name('WeatherAgent')
                                    .with_agent_description('Forecasts')
                                    .with_agent_version('2.1.0')
                                    .with_agent_url('https://agents.example/agent')
                                    .with_agent_card_path('/card.json')
                                    .with_default_task_response('Sunny')
                                    .build

      card = agent_card(expectations)
      expect(card['httpRequest']['path']).to eq('/card.json')
      body = card['httpResponse']['body']
      expect(body).to include('"name": "WeatherAgent"')
      expect(body).to include('"version": "2.1.0"')
      expect(body).to include('"url": "https://agents.example/agent"')

      send = find_json_rpc(expectations, 'tasks/send')
      expect(send['httpRequest']['path']).to eq('/agent')
      expect(send['httpResponseTemplate']['template']).to include('"text": "Sunny"')
    end

    it 'advertises skills in the agent card' do
      expectations = described_class.a2a_mock
                                    .with_skill('weather')
                                      .with_name('Weather lookup')
                                      .with_description('Looks up weather')
                                      .with_tag('forecast')
                                      .with_tag('meteo')
                                      .with_example('What is the weather in Paris?')
                                    .and_then
                                    .build

      body = agent_card(expectations)['httpResponse']['body']
      expect(body).to include('"id": "weather"')
      expect(body).to include('"name": "Weather lookup"')
      expect(body).to include('"description": "Looks up weather"')
      expect(body).to include('"tags": ["forecast", "meteo"]')
      expect(body).to include('"examples": ["What is the weather in Paris?"]')
    end

    it 'defaults a skill name to its id when none is given' do
      expectations = described_class.a2a_mock.with_skill('echo').and_then.build
      body = agent_card(expectations)['httpResponse']['body']
      expect(body).to include('"id": "echo"')
      expect(body).to include('"name": "echo"')
    end

    it 'emits a custom task handler matching the first message part via JSON_PATH' do
      expectations = described_class.a2a_mock
                                    .on_task_send
                                      .matching_message('forecast')
                                      .responding_with('Sunny, 25C')
                                    .and_then
                                    .build

      handler = find_json_path(expectations, 'tasks/send')
      expect(handler['httpRequest']['body']['jsonPath']).to eq(
        "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /forecast/)]"
      )
      template = handler['httpResponseTemplate']['template']
      expect(template).to include('"status": {"state": "completed"}')
      expect(template).to include('"text": "Sunny, 25C"')

      # The custom handler is emitted before the catch-all tasks/send.
      handler_index = expectations.index(handler)
      send_index = expectations.index(find_json_rpc(expectations, 'tasks/send'))
      expect(handler_index).to be < send_index
    end

    it 'emits an error task handler with state failed' do
      expectations = described_class.a2a_mock
                                    .on_task_send
                                      .matching_message('boom')
                                      .responding_with('it failed', true)
                                    .and_then
                                    .build

      handler = find_json_path(expectations, 'tasks/send')
      expect(handler['httpResponseTemplate']['template']).to include('"status": {"state": "failed"}')
    end

    it 'escapes regex slashes in the custom-handler pattern' do
      expectations = described_class.a2a_mock
                                    .on_task_send.matching_message('a/b').responding_with('ok')
                                    .and_then
                                    .build

      handler = find_json_path(expectations, 'tasks/send')
      expect(handler['httpRequest']['body']['jsonPath']).to include('a\\/b')
    end

    it 'preserves a regex escape sequence without doubling the backslash' do
      # "\\d+" in a Ruby single-element double-quoted string is the 3 chars: \ d +
      expectations = described_class.a2a_mock
                                    .on_task_send.matching_message("\\d+").responding_with('ok')
                                    .and_then
                                    .build

      handler = find_json_path(expectations, 'tasks/send')
      json_path = handler['httpRequest']['body']['jsonPath']
      # "\\d+" => the 3 chars \ d + ; must be emitted verbatim, NOT doubled to \\d+
      expect(json_path).to include('\\d+')
      expect(json_path).not_to include('\\\\d')
    end

    it 'does not double an already-escaped slash in the pattern' do
      # "a\\/b" is the 4 chars: a \ / b (an already-escaped slash)
      expectations = described_class.a2a_mock
                                    .on_task_send.matching_message("a\\/b").responding_with('ok')
                                    .and_then
                                    .build

      handler = find_json_path(expectations, 'tasks/send')
      json_path = handler['httpRequest']['body']['jsonPath']
      # the escaped slash must stay a\/b — NOT become a\\/b (which would be a double backslash)
      expect(json_path).to include('a\\/b')
      expect(json_path).not_to include('a\\\\/b')
    end

    it 'neutralizes a trailing lone backslash so the regex literal cannot break out' do
      # "trail\\" is the 6 chars: t r a i l \ (one real, trailing backslash)
      expectations = described_class.a2a_mock
                                    .on_task_send.matching_message("trail\\").responding_with('ok')
                                    .and_then
                                    .build

      handler = find_json_path(expectations, 'tasks/send')
      json_path = handler['httpRequest']['body']['jsonPath']
      # the lone trailing backslash is doubled to a literal backslash: trail\\
      expect(json_path).to include('trail\\\\')
      # SECURITY: the regex literal stays balanced — the closing delimiter and
      # JSONPath tail are emitted UNESCAPED, so the input cannot break out.
      expect(json_path).to end_with('/)]')
      expect(json_path).to eq(
        "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /trail\\\\/)]"
      )
    end

    it 'emits an SSE streaming expectation when streaming is enabled' do
      expectations = described_class.a2a_mock.with_streaming.build

      card_body = agent_card(expectations)['httpResponse']['body']
      expect(card_body).to include('"streaming": true')

      stream = find_json_rpc(expectations, 'message/stream')
      expect(stream).not_to have_key('httpResponseSse') # guard the correct wire key
      sse = stream['httpSseResponse']
      expect(sse['statusCode']).to eq(200)
      expect(sse['closeConnection']).to be(true)
      expect(sse['events'].length).to eq(3)
      expect(sse['events'][0]['event']).to eq('message')
      expect(sse['events'][0]['data']).to include('"kind": "status-update"')
      expect(sse['events'][0]['data']).to include('"state": "working"')
      expect(sse['events'][1]['data']).to include('"kind": "artifact-update"')
      expect(sse['events'][2]['data']).to include('"state": "completed"')
      expect(sse['events'][2]['data']).to include('"final": true')
    end

    it 'uses a custom streaming method name' do
      expectations = described_class.a2a_mock.with_streaming_method('tasks/sendSubscribe').build

      expect(find_json_rpc(expectations, 'tasks/sendSubscribe')).not_to be_nil
      expect(agent_card(expectations)['httpResponse']['body']).to include('"streaming": true')
    end

    it 'emits push-notification config + delivery when configured' do
      expectations = described_class.a2a_mock
                                    .with_push_notifications('http://localhost:1234/a2a/callback')
                                    .build

      card_body = agent_card(expectations)['httpResponse']['body']
      expect(card_body).to include('"pushNotifications": true')

      config = find_json_rpc(expectations, 'tasks/pushNotificationConfig/set')
      expect(config['httpResponseTemplate']['template']).to include('http://localhost:1234/a2a/callback')

      # The push-notification delivery replaces the plain tasks/send expectation.
      send = expectations.select do |e|
        e['httpRequest']['body'] && e['httpRequest']['body']['method'] == 'tasks/send'
      end
      expect(send.length).to eq(1)
      delivery = send.first
      override = delivery['httpOverrideForwardedRequest']
      expect(override).not_to be_nil

      webhook = override['requestOverride']
      expect(webhook['method']).to eq('POST')
      expect(webhook['path']).to eq('/a2a/callback')
      expect(webhook['secure']).to be(false)
      expect(webhook['socketAddress']).to eq(
        'host' => 'localhost', 'port' => 1234, 'scheme' => 'HTTP'
      )
      expect(webhook['headers']).to include(
        { 'name' => 'Host', 'values' => ['localhost:1234'] }
      )
      expect(webhook['body']).to include('"jsonrpc": "2.0"')
      expect(webhook['body']).to include('"status": {"state": "completed"}')

      template = override['responseTemplate']
      expect(template['templateType']).to eq('VELOCITY')
      expect(template['template']).to include('$!{request.jsonRpcRawId}')
    end

    it 'parses an https webhook with a default port' do
      expectations = described_class.a2a_mock
                                    .with_push_notifications('https://hooks.example/cb')
                                    .build
      delivery = expectations.find { |e| e['httpOverrideForwardedRequest'] }
      webhook = delivery['httpOverrideForwardedRequest']['requestOverride']
      expect(webhook['secure']).to be(true)
      expect(webhook['socketAddress']).to eq('host' => 'hooks.example', 'port' => 443, 'scheme' => 'HTTPS')
      expect(webhook['headers']).to include({ 'name' => 'Host', 'values' => ['hooks.example:443'] })
    end

    it 'raises on a webhook URL without a host' do
      expect do
        described_class.a2a_mock.with_push_notifications('not-a-url').build
      end.to raise_error(ArgumentError, /no host/)
    end

    it 'JSON- and Velocity-escapes the default task response' do
      expectations = described_class.a2a_mock
                                    .with_default_task_response('cost is $5 #1 "quoted"')
                                    .build
      template = find_json_rpc(expectations, 'tasks/send')['httpResponseTemplate']['template']
      expect(template).to include('cost is ${esc.d}5 ${esc.h}1')
      expect(template).to include('\\"quoted\\"')
    end

    it 'Velocity-escapes a backslash before # in the default task response' do
      expectations = described_class.a2a_mock.with_default_task_response('a\\#b').build
      template = find_json_rpc(expectations, 'tasks/send')['httpResponseTemplate']['template']
      expect(template).to include('a${esc.b}${esc.b}${esc.h}b')
    end
  end

  describe '#apply_to' do
    it 'registers all built expectations via client.upsert' do
      client = instance_double(MockServer::Client)
      allow(client).to receive(:upsert)

      described_class.a2a_mock
                     .with_skill('s').and_then
                     .apply_to(client)

      expect(client).to have_received(:upsert) do |*args|
        expect(args.length).to eq(4)
        expect(args.first.to_h['httpRequest']['method']).to eq('GET')
      end
    end
  end
end
