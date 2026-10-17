# frozen_string_literal: true

require 'json'

module MockServer
  # Fluent builder for mocking an MCP (Model Context Protocol) server.
  #
  # Mirrors the Java/Node/Python +McpMockBuilder+. It produces the same
  # wire-level expectation JSON: a set of HTTP expectations that emulate a
  # Streamable-HTTP MCP server speaking JSON-RPC 2.0. Each generated
  # expectation matches a JSON-RPC method on +POST <path>+ and responds with a
  # Velocity template that echoes back the incoming JSON-RPC id via
  # +$!{request.jsonRpcRawId}+.
  #
  # @example
  #   MockServer::MCP.mcp_mock('/mcp')
  #     .with_tool('get_weather')
  #       .with_description('Get weather for a city')
  #       .with_input_schema('{"type":"object"}')
  #       .responding_with('72F and sunny')
  #     .and_then
  #     .apply_to(client)
  module MCP
    # JSON-escape a string the same way Jackson +writeValueAsString+ does, then
    # strip the surrounding quotes — yielding only the escaped inner content.
    # @api private
    def self.escape_json(value)
      return '' if value.nil?

      quoted = JSON.generate(value.to_s)
      quoted[1...-1]
    end

    VELOCITY_METACHARACTER_ESCAPES = { '$' => '${esc.d}', '#' => '${esc.h}' }.freeze
    private_constant :VELOCITY_METACHARACTER_ESCAPES

    # Escape Velocity metacharacters so literal +$+ / +#+ survive rendering.
    # Velocity consumes backslashes written before a reference or directive, so
    # a run of backslashes followed by +$+, +#+ or the end of the value is
    # escaped too; other backslashes are literal to Velocity and left as-is.
    # @api private
    def self.escape_velocity(value)
      return value if value.nil?

      # group 2 consumes the metacharacter after a run, is empty at the end and
      # unset (empty alternative) otherwise, so no lookaround is needed
      value.to_s.gsub(/(\\+)(?:([$#]|\z)|)|[$#]/) do |match|
        run = Regexp.last_match(1)
        next VELOCITY_METACHARACTER_ESCAPES[match] if run.nil?

        following = Regexp.last_match(2)
        next run if following.nil?

        ('${esc.b}' * run.length) + VELOCITY_METACHARACTER_ESCAPES.fetch(following, '')
      end
    end

    # Escape single quotes for safe inclusion inside a JSONPath string literal.
    # @api private
    def self.escape_json_path(value)
      return '' if value.nil?

      value.to_s.gsub("'", "\\\\'")
    end

    # Parse + re-serialize JSON (compact) to validate it. Raises on invalid JSON.
    # @api private
    def self.validate_and_serialize_json(raw)
      JSON.generate(JSON.parse(raw))
    rescue JSON::ParserError => e
      raise ArgumentError, "Invalid JSON for inputSchema: #{e.message}"
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
    ToolDef = Struct.new(:name, :description, :input_schema, :response_content, :response_is_error)
    # @api private
    ResourceDef = Struct.new(:uri, :name, :description, :mime_type, :content)
    # @api private
    PromptArg = Struct.new(:name, :description, :required)
    # @api private
    PromptMessage = Struct.new(:role, :text)
    # @api private
    PromptDef = Struct.new(:name, :description, :arguments, :messages)

    class McpMockBuilder
      def initialize(path = '/mcp')
        @path = path.is_a?(String) ? path : '/mcp'
        @server_name = 'MockMCPServer'
        @server_version = '1.0.0'
        @protocol_version = '2025-03-26'
        @tools_capability = false
        @resources_capability = false
        @prompts_capability = false
        @tools = []
        @resources = []
        @prompts = []
      end

      # @return [self]
      def with_server_name(name)
        @server_name = name
        self
      end

      # @return [self]
      def with_server_version(version)
        @server_version = version
        self
      end

      # @return [self]
      def with_protocol_version(version)
        @protocol_version = version
        self
      end

      # @return [self]
      def with_tools_capability
        @tools_capability = true
        self
      end

      # @return [self]
      def with_resources_capability
        @resources_capability = true
        self
      end

      # @return [self]
      def with_prompts_capability
        @prompts_capability = true
        self
      end

      # @return [McpToolBuilder]
      def with_tool(name)
        McpToolBuilder.new(self, name)
      end

      # @return [McpResourceBuilder]
      def with_resource(uri)
        McpResourceBuilder.new(self, uri)
      end

      # @return [McpPromptBuilder]
      def with_prompt(name)
        McpPromptBuilder.new(self, name)
      end

      # @api private
      def add_tool(tool)
        @tools << tool
        @tools_capability = true
      end

      # @api private
      def add_resource(resource)
        @resources << resource
        @resources_capability = true
      end

      # @api private
      def add_prompt(prompt)
        @prompts << prompt
        @prompts_capability = true
      end

      # @return [Array<Hash>] the ordered list of expectations
      def build
        expectations = [
          build_initialize_expectation,
          build_ping_expectation,
          build_notifications_initialized_expectation
        ]

        if @tools_capability || !@tools.empty?
          expectations << build_tools_list_expectation
        end
        @tools.each { |tool| expectations << build_tools_call_expectation(tool) }

        if @resources_capability || !@resources.empty?
          expectations << build_resources_list_expectation
        end
        @resources.each { |resource| expectations << build_resources_read_expectation(resource) }

        if @prompts_capability || !@prompts.empty?
          expectations << build_prompts_list_expectation
        end
        @prompts.each { |prompt| expectations << build_prompts_get_expectation(prompt) }

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
            'template' => MCP.velocity_json_rpc_response(result_json),
            'templateType' => 'VELOCITY'
          }
        }
      end

      def build_initialize_expectation
        caps_parts = []
        if @tools_capability || !@tools.empty?
          caps_parts << '"tools": {"listChanged": false}'
        end
        if @resources_capability || !@resources.empty?
          caps_parts << '"resources": {"subscribe": false, "listChanged": false}'
        end
        if @prompts_capability || !@prompts.empty?
          caps_parts << '"prompts": {"listChanged": false}'
        end
        caps = '{' + caps_parts.join(', ') + '}'

        result_json =
          '{"protocolVersion": "' + esc(@protocol_version) + '", ' \
          '"capabilities": ' + caps + ', ' \
          '"serverInfo": {"name": "' + esc(@server_name) + '", "version": "' + esc(@server_version) + '"}}'

        velocity_template_response_expectation(json_rpc_request('initialize'), result_json)
      end

      def build_ping_expectation
        velocity_template_response_expectation(json_rpc_request('ping'), '{}')
      end

      def build_notifications_initialized_expectation
        {
          'httpRequest' => json_rpc_request('notifications/initialized'),
          'httpResponse' => {
            'statusCode' => 200,
            'headers' => [{ 'name' => 'Content-Type', 'values' => ['application/json'] }],
            'body' => '{}'
          }
        }
      end

      def build_tools_list_expectation
        items = @tools.map do |tool|
          parts = ['{"name": "' + esc(tool.name) + '"']
          parts << ', "description": "' + esc(tool.description) + '"' unless tool.description.nil?
          unless tool.input_schema.nil?
            parts << ', "inputSchema": ' + MCP.escape_velocity(MCP.validate_and_serialize_json(tool.input_schema))
          end
          parts << '}'
          parts.join
        end
        tools_json = '[' + items.join(', ') + ']'
        velocity_template_response_expectation(json_rpc_request('tools/list'), '{"tools": ' + tools_json + '}')
      end

      def build_tools_call_expectation(tool)
        json_path = "$[?(@.method == 'tools/call' && @.params.name == '" + MCP.escape_json_path(tool.name) + "')]"
        content = tool.response_content.nil? ? '' : esc(tool.response_content)
        is_error = tool.response_is_error ? 'true' : 'false'
        result_json = '{"content": [{"type": "text", "text": "' + content + '"}], "isError": ' + is_error + '}'
        velocity_template_response_expectation(json_path_request(json_path), result_json)
      end

      def build_resources_list_expectation
        items = @resources.map do |resource|
          parts = ['{"uri": "' + esc(resource.uri) + '"']
          parts << ', "name": "' + esc(resource.name) + '"' unless resource.name.nil?
          parts << ', "description": "' + esc(resource.description) + '"' unless resource.description.nil?
          parts << ', "mimeType": "' + esc(resource.mime_type) + '"' unless resource.mime_type.nil?
          parts << '}'
          parts.join
        end
        resources_json = '[' + items.join(', ') + ']'
        velocity_template_response_expectation(json_rpc_request('resources/list'), '{"resources": ' + resources_json + '}')
      end

      def build_resources_read_expectation(resource)
        json_path = "$[?(@.method == 'resources/read' && @.params.uri == '" + MCP.escape_json_path(resource.uri) + "')]"
        content = resource.content.nil? ? '' : esc(resource.content)
        mime_type = resource.mime_type.nil? ? 'application/json' : resource.mime_type
        result_json =
          '{"contents": [{"uri": "' + esc(resource.uri) + '", ' \
          '"mimeType": "' + esc(mime_type) + '", ' \
          '"text": "' + content + '"}]}'
        velocity_template_response_expectation(json_path_request(json_path), result_json)
      end

      def build_prompts_list_expectation
        items = @prompts.map do |prompt|
          parts = ['{"name": "' + esc(prompt.name) + '"']
          parts << ', "description": "' + esc(prompt.description) + '"' unless prompt.description.nil?
          unless prompt.arguments.empty?
            arg_items = prompt.arguments.map do |arg|
              arg_parts = ['{"name": "' + esc(arg.name) + '"']
              arg_parts << ', "description": "' + esc(arg.description) + '"' unless arg.description.nil?
              arg_parts << ', "required": ' + (arg.required ? 'true' : 'false')
              arg_parts << '}'
              arg_parts.join
            end
            parts << ', "arguments": [' + arg_items.join(', ') + ']'
          end
          parts << '}'
          parts.join
        end
        prompts_json = '[' + items.join(', ') + ']'
        velocity_template_response_expectation(json_rpc_request('prompts/list'), '{"prompts": ' + prompts_json + '}')
      end

      def build_prompts_get_expectation(prompt)
        json_path = "$[?(@.method == 'prompts/get' && @.params.name == '" + MCP.escape_json_path(prompt.name) + "')]"
        msg_items = prompt.messages.map do |msg|
          '{"role": "' + esc(msg.role) + '", ' \
          '"content": {"type": "text", "text": "' + esc(msg.text) + '"}}'
        end
        messages_json = '[' + msg_items.join(', ') + ']'
        result_json = '{"messages": ' + messages_json + '}'
        velocity_template_response_expectation(json_path_request(json_path), result_json)
      end

      # Compose JSON-escape then Velocity-escape, matching the reference clients.
      def esc(value)
        MCP.escape_velocity(MCP.escape_json(value))
      end
    end

    class McpToolBuilder
      def initialize(parent, name)
        @parent = parent
        @tool = ToolDef.new(name, nil, nil, nil, false)
      end

      # @return [self]
      def with_description(description)
        @tool.description = description
        self
      end

      # @return [self]
      def with_input_schema(json_schema)
        @tool.input_schema = json_schema
        self
      end

      # @return [self]
      def responding_with(text_content, is_error = false)
        @tool.response_content = text_content
        @tool.response_is_error = is_error
        self
      end

      # Commit the tool and return to the root builder.
      # @return [McpMockBuilder]
      def and_then
        @parent.add_tool(@tool)
        @parent
      end
      alias and_ and_then
    end

    class McpResourceBuilder
      def initialize(parent, uri)
        @parent = parent
        @resource = ResourceDef.new(uri, nil, nil, 'application/json', nil)
      end

      # @return [self]
      def with_name(name)
        @resource.name = name
        self
      end

      # @return [self]
      def with_description(description)
        @resource.description = description
        self
      end

      # @return [self]
      def with_mime_type(mime_type)
        @resource.mime_type = mime_type
        self
      end

      # @return [self]
      def with_content(content)
        @resource.content = content
        self
      end

      # @return [McpMockBuilder]
      def and_then
        @parent.add_resource(@resource)
        @parent
      end
      alias and_ and_then
    end

    class McpPromptBuilder
      def initialize(parent, name)
        @parent = parent
        @prompt = PromptDef.new(name, nil, [], [])
      end

      # @return [self]
      def with_description(description)
        @prompt.description = description
        self
      end

      # @return [self]
      def with_argument(name, description, required)
        @prompt.arguments << PromptArg.new(name, description, required)
        self
      end

      # @return [self]
      def responding_with(role, text_content)
        @prompt.messages << PromptMessage.new(role, text_content)
        self
      end

      # @return [McpMockBuilder]
      def and_then
        @parent.add_prompt(@prompt)
        @parent
      end
      alias and_ and_then
    end

    # Create a new MCP mock builder. +path+ defaults to +/mcp+.
    # @return [McpMockBuilder]
    def self.mcp_mock(path = '/mcp')
      McpMockBuilder.new(path)
    end
  end
end
