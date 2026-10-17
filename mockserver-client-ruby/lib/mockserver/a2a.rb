# frozen_string_literal: true

require 'json'
require 'uri'
require_relative 'mcp'

module MockServer
  # Fluent builder for mocking an A2A (Agent-to-Agent) agent.
  #
  # Mirrors the Java +A2aMockBuilder+ (and its Node/Python siblings). It produces
  # the same wire-level expectation JSON: a set of HTTP expectations that emulate
  # an A2A agent serving an agent card and speaking JSON-RPC 2.0 over +POST
  # <path>+. The generated control-plane expectations are:
  #
  #   * agent-card +GET <agent_card_path>+ (default +/.well-known/agent.json+);
  #   * JSON-RPC +tasks/send+, +tasks/get+, +tasks/cancel+;
  #   * optional SSE streaming (+with_streaming+);
  #   * optional push-notification config + delivery (+with_push_notifications+);
  #   * optional per-message custom task handlers (+on_task_send+);
  #   * optional advertised skills (+with_skill+).
  #
  # Each JSON-RPC response is a Velocity template that echoes the incoming
  # JSON-RPC id back via +$!{request.jsonRpcRawId}+.
  #
  # @example
  #   MockServer::A2A.a2a_mock('/a2a')
  #     .with_agent_name('WeatherAgent')
  #     .with_skill('weather')
  #       .with_name('Weather lookup')
  #       .with_tag('forecast')
  #     .and_then
  #     .on_task_send
  #       .matching_message('forecast')
  #       .responding_with('Sunny, 25C')
  #     .and_then
  #     .apply_to(client)
  module A2A
    # JSON-escape a string the same way Jackson +writeValueAsString+ does, then
    # strip the surrounding quotes — yielding only the escaped inner content.
    # @api private
    def self.escape_json(value)
      return '' if value.nil?

      quoted = JSON.generate(value.to_s)
      quoted[1...-1]
    end

    # Escape Velocity metacharacters so literal +$+ / +#+ survive rendering.
    # Shares the MCP builder's rules (backslash runs before +$+, +#+ or the end
    # of the value are escaped too).
    # @api private
    def self.escape_velocity(value)
      MCP.escape_velocity(value)
    end

    # @api private
    def self.velocity_json_rpc_response(result_json)
      '{"statusCode": 200, ' \
        '"headers": [{"name": "Content-Type", "values": ["application/json"]}], ' \
        '"body": {"jsonrpc": "2.0", "result": ' + result_json + ', "id": $!{request.jsonRpcRawId}}}'
    end

    # @api private
    # Wraps a Hash so it responds to +to_h+, allowing it to be passed to
    # +Client#upsert+.
    class RawExpectation
      def initialize(hash)
        @hash = hash
      end

      def to_h
        @hash
      end
    end

    # @api private
    SkillDef = Struct.new(:id, :name, :description, :tags, :examples)
    # @api private
    TaskHandler = Struct.new(:message_pattern, :response_text, :is_error)

    # Resolved components of a push-notification webhook URL.
    # @api private
    WebhookTarget = Struct.new(:host, :port, :secure, :path) do
      def host_header
        "#{host}:#{port}"
      end

      def self.parse(url)
        uri = URI.parse(url)
        secure = uri.scheme.to_s.downcase == 'https'
        host = uri.host
        raise ArgumentError, "Invalid push-notification webhook URL (no host): #{url}" if host.nil? || host.empty?

        port = uri.port || (secure ? 443 : 80)
        path = uri.path
        path = '/' if path.nil? || path.empty?
        new(host, port, secure, path)
      end
    end

    class A2aMockBuilder
      def initialize(path = '/a2a')
        @path = path.is_a?(String) ? path : '/a2a'
        @agent_card_path = '/.well-known/agent.json'
        @agent_name = 'MockAgent'
        @agent_description = 'A mock A2A agent'
        @agent_version = '1.0.0'
        @agent_url = nil
        @skills = []
        @task_handlers = []
        @default_task_response = 'Task completed successfully'
        @streaming = false
        @streaming_method = 'message/stream'
        @push_notification_url = nil
      end

      # @return [self]
      def with_agent_name(name)
        @agent_name = name
        self
      end

      # @return [self]
      def with_agent_description(description)
        @agent_description = description
        self
      end

      # @return [self]
      def with_agent_version(version)
        @agent_version = version
        self
      end

      # @return [self]
      def with_agent_url(url)
        @agent_url = url
        self
      end

      # @return [self]
      def with_agent_card_path(path)
        @agent_card_path = path
        self
      end

      # @return [self]
      def with_default_task_response(response)
        @default_task_response = response
        self
      end

      # Advertise + mock the A2A streaming capability. The agent card reports
      # +capabilities.streaming: true+ and the streaming JSON-RPC method (default
      # +message/stream+) returns an SSE stream of status/artifact update events.
      # @return [self]
      def with_streaming
        @streaming = true
        self
      end

      # Override the JSON-RPC method that triggers the streaming response.
      # Implies {#with_streaming}.
      # @return [self]
      def with_streaming_method(method)
        @streaming_method = method
        @streaming = true
        self
      end

      # Advertise + mock A2A push notifications. The agent card reports
      # +capabilities.pushNotifications: true+, +tasks/pushNotificationConfig/set+
      # echoes the registered config, and each +tasks/send+ additionally POSTs the
      # completed task to +webhook_url+ while still returning the JSON-RPC task
      # response to the caller.
      # @return [self]
      def with_push_notifications(webhook_url)
        @push_notification_url = webhook_url
        self
      end

      # @return [A2aSkillBuilder]
      def with_skill(id)
        A2aSkillBuilder.new(self, id)
      end

      # @return [A2aTaskHandlerBuilder]
      def on_task_send
        A2aTaskHandlerBuilder.new(self)
      end

      # @api private
      def add_skill(skill)
        @skills << skill
      end

      # @api private
      def add_task_handler(handler)
        @task_handlers << handler
      end

      # @return [Array<Hash>] the ordered list of expectations
      def build
        expectations = [build_agent_card_expectation]

        @task_handlers.each { |handler| expectations << build_custom_task_handler(handler) }

        expectations << build_streaming_expectation if @streaming

        if @push_notification_url
          expectations << build_push_notification_config_expectation
          expectations << build_push_notification_delivery_expectation
        else
          expectations << build_tasks_send_expectation
        end
        expectations << build_tasks_get_expectation
        expectations << build_tasks_cancel_expectation

        expectations
      end

      # @return [Array<Expectation>]
      def apply_to(client)
        client.upsert(*build.map { |h| RawExpectation.new(h) })
      end

      private

      def json_rpc_request(method)
        { 'method' => 'POST', 'path' => @path, 'body' => { 'type' => 'JSON_RPC', 'method' => method } }
      end

      def json_path_request(json_path)
        { 'method' => 'POST', 'path' => @path, 'body' => { 'type' => 'JSON_PATH', 'jsonPath' => json_path } }
      end

      def velocity_template_response_expectation(http_request, result_json)
        {
          'httpRequest' => http_request,
          'httpResponseTemplate' => {
            'template' => A2A.velocity_json_rpc_response(result_json),
            'templateType' => 'VELOCITY'
          }
        }
      end

      def build_agent_card_expectation
        skill_items = @skills.map do |skill|
          parts = ['{"id": "' + esc_plain(skill.id) + '"']
          parts << ', "name": "' + esc_plain(skill.name || skill.id) + '"'
          parts << ', "description": "' + esc_plain(skill.description) + '"' unless skill.description.nil?
          unless skill.tags.empty?
            parts << ', "tags": [' + skill.tags.map { |t| '"' + esc_plain(t) + '"' }.join(', ') + ']'
          end
          unless skill.examples.empty?
            parts << ', "examples": [' + skill.examples.map { |e| '"' + esc_plain(e) + '"' }.join(', ') + ']'
          end
          parts << '}'
          parts.join
        end
        skills_json = '[' + skill_items.join(', ') + ']'

        url = @agent_url || ('http://localhost' + @path)

        agent_card_json =
          '{' \
          '"name": "' + esc_plain(@agent_name) + '", ' \
          '"description": "' + esc_plain(@agent_description) + '", ' \
          '"version": "' + esc_plain(@agent_version) + '", ' \
          '"url": "' + esc_plain(url) + '", ' \
          '"capabilities": {"streaming": ' + bool(@streaming) + ', ' \
          '"pushNotifications": ' + bool(!@push_notification_url.nil?) + ', ' \
          '"stateTransitionHistory": false}, ' \
          '"skills": ' + skills_json + '}'

        {
          'httpRequest' => { 'method' => 'GET', 'path' => @agent_card_path },
          'httpResponse' => {
            'statusCode' => 200,
            'headers' => [{ 'name' => 'Content-Type', 'values' => ['application/json'] }],
            'body' => agent_card_json
          }
        }
      end

      def build_tasks_send_expectation
        velocity_template_response_expectation(
          json_rpc_request('tasks/send'),
          task_result_json(@default_task_response, false)
        )
      end

      def build_tasks_get_expectation
        velocity_template_response_expectation(
          json_rpc_request('tasks/get'),
          task_result_json(@default_task_response, false)
        )
      end

      def build_tasks_cancel_expectation
        result_json = '{"id": "mock-task-id", "status": {"state": "canceled"}}'
        velocity_template_response_expectation(json_rpc_request('tasks/cancel'), result_json)
      end

      def build_custom_task_handler(handler)
        escaped_pattern = escape_message_pattern(handler.message_pattern)
        json_path = "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /" + escaped_pattern + '/)]'
        velocity_template_response_expectation(
          json_path_request(json_path),
          task_result_json(handler.response_text, handler.is_error)
        )
      end

      # Neutralize only the regex-delimiter breakout while preserving every
      # existing regex escape sequence. The user-supplied message_pattern is
      # documented as a regular expression and is embedded between `/.../`
      # delimiters inside a JSONPath. A single-pass scanner is required (rather
      # than independent gsubs) so that a backslash and the character it escapes
      # are consumed together — otherwise a trailing lone backslash, or an input
      # containing `\/`, could escape the closing `/` delimiter and break out of
      # the regex literal into the surrounding JSONPath/JSON.
      # (CodeQL rb/incomplete-sanitization, alert #65.)
      def escape_message_pattern(pattern)
        chars = pattern.to_s.chars
        out = +''
        i = 0
        while i < chars.length
          c = chars[i]
          case c
          when '\\'
            if i + 1 < chars.length
              out << '\\' << chars[i + 1] # preserve escape sequence (\d, \/, \\)
              i += 1                       # extra advance past the escaped char
            else
              out << '\\\\'                # trailing lone backslash -> literal backslash
            end
          when '/'  then out << '\\/'
          when "\n" then out << '\\n'
          when "\r" then out << '\\r'
          when "\0" then nil               # strip NUL
          else out << c
          end
          i += 1
        end
        out
      end

      def build_streaming_expectation
        text = A2A.escape_json(@default_task_response)
        task_id = 'mock-task-id'

        events = [
          { 'event' => 'message',
            'data' => '{"jsonrpc": "2.0", "id": "1", "result": ' \
              '{"taskId": "' + task_id + '", "kind": "status-update", ' \
              '"status": {"state": "working"}, "final": false}}' },
          { 'event' => 'message',
            'data' => '{"jsonrpc": "2.0", "id": "1", "result": ' \
              '{"taskId": "' + task_id + '", "kind": "artifact-update", ' \
              '"artifact": {"parts": [{"type": "text", "text": "' + text + '"}]}}}' },
          { 'event' => 'message',
            'data' => '{"jsonrpc": "2.0", "id": "1", "result": ' \
              '{"taskId": "' + task_id + '", "kind": "status-update", ' \
              '"status": {"state": "completed"}, "final": true}}' }
        ]

        {
          'httpRequest' => json_rpc_request(@streaming_method),
          'httpSseResponse' => {
            'statusCode' => 200,
            'events' => events,
            'closeConnection' => true
          }
        }
      end

      def build_push_notification_config_expectation
        # Echo the registered push-notification config back as the JSON-RPC result.
        result_json = '{"url": "' + A2A.escape_velocity(A2A.escape_json(@push_notification_url)) + '"}'
        velocity_template_response_expectation(
          json_rpc_request('tasks/pushNotificationConfig/set'),
          result_json
        )
      end

      def build_push_notification_delivery_expectation
        # A tasks/send both returns the JSON-RPC task response to the caller AND
        # POSTs the completed task to the configured webhook. Modelled with an
        # override-forwarded-request: the request override targets the webhook
        # (literal body, JSON-escaped only — no Velocity engine runs over a request
        # override), and a Velocity response *template* produces the caller's
        # JSON-RPC response so the request's id is echoed back.
        target = WebhookTarget.parse(@push_notification_url)

        push_body = '{"jsonrpc": "2.0", "result": ' + task_result_json_raw(@default_task_response, false) + '}'

        webhook_request = {
          'method' => 'POST',
          'path' => target.path,
          'socketAddress' => {
            'host' => target.host,
            'port' => target.port,
            'scheme' => target.secure ? 'HTTPS' : 'HTTP'
          },
          'secure' => target.secure,
          'headers' => [
            { 'name' => 'Host', 'values' => [target.host_header] },
            { 'name' => 'Content-Type', 'values' => ['application/json'] }
          ],
          'body' => push_body
        }

        {
          'httpRequest' => json_rpc_request('tasks/send'),
          'httpOverrideForwardedRequest' => {
            'requestOverride' => webhook_request,
            'responseTemplate' => {
              'template' => A2A.velocity_json_rpc_response(task_result_json(@default_task_response, false)),
              'templateType' => 'VELOCITY'
            }
          }
        }
      end

      # Velocity-templated result body: text must survive the Velocity engine, so
      # both JSON and Velocity escaping are applied (Velocity un-escapes at render).
      def task_result_json(response_text, is_error)
        task_result_json_with_text(A2A.escape_velocity(A2A.escape_json(response_text)), is_error)
      end

      # Literal (non-templated) result body (e.g. the webhook payload): only JSON
      # escaping — Velocity escaping would corrupt '$' / '#'.
      def task_result_json_raw(response_text, is_error)
        task_result_json_with_text(A2A.escape_json(response_text), is_error)
      end

      def task_result_json_with_text(escaped_text, is_error)
        state = is_error ? 'failed' : 'completed'
        '{"id": "mock-task-id", ' \
        '"status": {"state": "' + state + '"}, ' \
        '"artifacts": [{"parts": [{"type": "text", "text": "' + escaped_text + '"}]}]}'
      end

      def bool(value)
        value ? 'true' : 'false'
      end

      # JSON-escape then Velocity-escape, matching the reference clients (used for
      # the agent card, which is a literal — but kept Velocity-safe for parity with
      # the Java builder's escapeJson-only card; here the card is a literal HTTP
      # response body, so only JSON escaping is required).
      def esc_plain(value)
        A2A.escape_json(value)
      end
    end

    class A2aSkillBuilder
      def initialize(parent, id)
        @parent = parent
        @skill = SkillDef.new(id, nil, nil, [], [])
      end

      # @return [self]
      def with_name(name)
        @skill.name = name
        self
      end

      # @return [self]
      def with_description(description)
        @skill.description = description
        self
      end

      # @return [self]
      def with_tag(tag)
        @skill.tags << tag
        self
      end

      # @return [self]
      def with_example(example)
        @skill.examples << example
        self
      end

      # Commit the skill and return to the root builder.
      # @return [A2aMockBuilder]
      def and_then
        @parent.add_skill(@skill)
        @parent
      end
      alias and_ and_then
    end

    class A2aTaskHandlerBuilder
      def initialize(parent)
        @parent = parent
        @handler = TaskHandler.new('.*', 'Task completed', false)
      end

      # @return [self]
      def matching_message(pattern)
        @handler.message_pattern = pattern
        self
      end

      # @return [self]
      def responding_with(text, is_error = false)
        @handler.response_text = text
        @handler.is_error = is_error
        self
      end

      # Commit the handler and return to the root builder.
      # @return [A2aMockBuilder]
      def and_then
        @parent.add_task_handler(@handler)
        @parent
      end
      alias and_ and_then
    end

    # Create a new A2A mock builder. +path+ defaults to +/a2a+.
    # @return [A2aMockBuilder]
    def self.a2a_mock(path = '/a2a')
      A2aMockBuilder.new(path)
    end
  end
end
