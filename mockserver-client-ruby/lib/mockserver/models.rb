# frozen_string_literal: true

require 'base64'
require 'json'
require 'set'

module MockServer
  # Explicit mapping from Ruby snake_case field names to the camelCase
  # keys expected by the MockServer JSON protocol.
  FIELD_MAP = {
    'status_code'                    => 'statusCode',
    'reason_phrase'                  => 'reasonPhrase',
    'keep_alive'                     => 'keepAlive',
    'query_string_parameters'        => 'queryStringParameters',
    'path_parameters'                => 'pathParameters',
    'socket_address'                 => 'socketAddress',
    'time_unit'                      => 'timeUnit',
    'time_to_live'                   => 'timeToLive',
    'remaining_times'                => 'remainingTimes',
    'close_socket'                   => 'closeSocket',
    'close_socket_delay'             => 'closeSocketDelay',
    'suppress_content_length_header' => 'suppressContentLengthHeader',
    'content_length_header_override' => 'contentLengthHeaderOverride',
    'suppress_connection_header'     => 'suppressConnectionHeader',
    'keep_alive_override'            => 'keepAliveOverride',
    'connection_options'             => 'connectionOptions',
    'callback_class'                 => 'callbackClass',
    'client_id'                      => 'clientId',
    'response_callback'              => 'responseCallback',
    'drop_connection'                => 'dropConnection',
    'response_bytes'                 => 'responseBytes',
    'http_request'                   => 'httpRequest',
    'http_response'                  => 'httpResponse',
    'http_response_template'         => 'httpResponseTemplate',
    'http_response_class_callback'   => 'httpResponseClassCallback',
    'http_response_object_callback'  => 'httpResponseObjectCallback',
    'http_forward'                   => 'httpForward',
    'http_forward_template'          => 'httpForwardTemplate',
    'http_forward_class_callback'    => 'httpForwardClassCallback',
    'http_forward_object_callback'   => 'httpForwardObjectCallback',
    'http_override_forwarded_request' => 'httpOverrideForwardedRequest',
    'http_error'                     => 'httpError',
    'http_sse_response'              => 'httpSseResponse',
    'http_websocket_response'        => 'httpWebSocketResponse',
    'template_type'                  => 'templateType',
    'base64_bytes'                   => 'base64Bytes',
    'not_body'                       => 'not',
    'content_type'                   => 'contentType',
    'at_least'                       => 'atLeast',
    'at_most'                        => 'atMost',
    'expectation_id'                 => 'expectationId',
    'expectation_ids'                => 'expectationIds',
    'http_requests'                  => 'httpRequests',
    'spec_url_or_payload'            => 'specUrlOrPayload',
    'operations_and_responses'       => 'operationsAndResponses',
    'operation_id'                   => 'operationId',
    'request_modifier'               => 'requestModifier',
    'response_modifier'              => 'responseModifier',
    'maximum_number_of_request_to_return_in_verification_failure' => 'maximumNumberOfRequestToReturnInVerificationFailure'
  }.freeze

  REVERSE_FIELD_MAP = FIELD_MAP.invert.freeze

  # Known Body type strings used to distinguish Body objects from plain hashes
  # during deserialization.
  BODY_TYPES = Set.new(%w[
    STRING JSON REGEX XML BINARY JSON_SCHEMA JSON_PATH XPATH XML_SCHEMA JSON_RPC GRAPHQL
  ]).freeze

  # -------------------------------------------------------------------
  # Helper functions
  # -------------------------------------------------------------------

  # @api private
  def self.to_camel(snake_str)
    return FIELD_MAP[snake_str] if FIELD_MAP.key?(snake_str)

    parts = snake_str.split('_')
    parts[0] + parts[1..].map(&:capitalize).join
  end

  # @api private
  def self.from_camel(camel_str)
    return REVERSE_FIELD_MAP[camel_str] if REVERSE_FIELD_MAP.key?(camel_str)

    camel_str.gsub(/([A-Z])/) { "_#{$1.downcase}" }
  end

  # @api private
  def self.strip_none(hash)
    hash.reject { |_k, v| v.nil? }
  end

  # @api private
  def self.serialize_value(value)
    case value
    when ->(v) { v.respond_to?(:to_h) && v.class.ancestors.any? { |a| a.to_s.start_with?('MockServer::') } }
      value.to_h
    when Array
      value.map { |item| serialize_value(item) }
    else
      value
    end
  end

  # @api private
  def self.serialize_body(body)
    return nil if body.nil?
    return body if body.is_a?(String)
    return body if body.is_a?(Hash)
    return body.to_h if body.is_a?(Body)
    return body.to_h if body.is_a?(JsonRpcBody)
    return body.to_h if body.is_a?(GraphQLBody)

    body
  end

  # @api private
  def self.deserialize_body(data)
    return nil if data.nil?
    return data if data.is_a?(String)

    if data.is_a?(Hash)
      if data['type'] == 'JSON_RPC'
        return JsonRpcBody.from_hash(data)
      end
      if data['type'] == 'GRAPHQL'
        return GraphQLBody.from_hash(data)
      end
      return Body.from_hash(data) if BODY_TYPES.include?(data['type'])

      return data
    end
    data
  end

  # @api private
  def self.serialize_key_multi_values(items)
    return nil if items.nil?

    items.map(&:to_h)
  end

  # @api private
  def self.deserialize_key_multi_values(data)
    return nil if data.nil?

    if data.is_a?(Hash)
      return data.map { |k, v| KeyToMultiValue.new(name: k, values: v.is_a?(Array) ? v : [v]) }
    end

    data.map do |item|
      if item.is_a?(Hash)
        KeyToMultiValue.from_hash(item)
      elsif item.is_a?(String)
        KeyToMultiValue.new(name: item, values: [])
      else
        KeyToMultiValue.from_hash(item)
      end
    end
  end

  # -------------------------------------------------------------------
  # Model classes
  # -------------------------------------------------------------------

  class DelayDistribution
    attr_accessor :type, :min, :max, :median, :p99, :mean, :std_dev

    def initialize(type: nil, min: nil, max: nil, median: nil, p99: nil, mean: nil, std_dev: nil)
      @type = type
      @min = min
      @max = max
      @median = median
      @p99 = p99
      @mean = mean
      @std_dev = std_dev
    end

    def to_h
      MockServer.strip_none({
        'type'   => @type,
        'min'    => @min,
        'max'    => @max,
        'median' => @median,
        'p99'    => @p99,
        'mean'   => @mean,
        'stdDev' => @std_dev
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        type:    data['type'],
        min:     data['min'],
        max:     data['max'],
        median:  data['median'],
        p99:     data['p99'],
        mean:    data['mean'],
        std_dev: data['stdDev']
      )
    end
  end

  class Delay
    attr_accessor :time_unit, :value, :distribution

    def initialize(time_unit: 'MILLISECONDS', value: 0, distribution: nil)
      @time_unit = time_unit
      @value = value
      @distribution = distribution
    end

    def to_h
      MockServer.strip_none({
        'timeUnit'     => @time_unit,
        'value'        => @value,
        'distribution' => @distribution&.to_h
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      dist_data = data['distribution']
      new(
        time_unit:    data.fetch('timeUnit', 'MILLISECONDS'),
        value:        data.fetch('value', 0),
        distribution: dist_data ? DelayDistribution.from_hash(dist_data) : nil
      )
    end
  end

  class Times
    attr_accessor :remaining_times, :unlimited

    def initialize(remaining_times: nil, unlimited: nil)
      @remaining_times = remaining_times
      @unlimited = unlimited
    end

    def to_h
      MockServer.strip_none({
        'remainingTimes' => @remaining_times,
        'unlimited'      => @unlimited
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        remaining_times: data['remainingTimes'],
        unlimited:       data['unlimited']
      )
    end

    def self.unlimited
      new(unlimited: true)
    end

    def self.exactly(count)
      new(remaining_times: count, unlimited: false)
    end
  end

  class TimeToLive
    attr_accessor :time_unit, :time_to_live, :unlimited

    def initialize(time_unit: nil, time_to_live: nil, unlimited: nil)
      @time_unit = time_unit
      @time_to_live = time_to_live
      @unlimited = unlimited
    end

    def to_h
      MockServer.strip_none({
        'timeUnit'   => @time_unit,
        'timeToLive' => @time_to_live,
        'unlimited'  => @unlimited
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        time_unit:    data['timeUnit'],
        time_to_live: data['timeToLive'],
        unlimited:    data['unlimited']
      )
    end

    def self.unlimited
      new(unlimited: true)
    end

    def self.exactly(time_to_live, time_unit)
      new(
        time_unit:    time_unit,
        time_to_live: time_to_live,
        unlimited:    false
      )
    end
  end

  class KeyToMultiValue
    attr_accessor :name, :values

    def initialize(name: '', values: [])
      @name = name
      @values = values
    end

    # name and values are always emitted (not stripped via strip_none) because
    # the MockServer protocol requires both fields on every header/cookie/parameter.
    def to_h
      {
        'name'   => @name,
        'values' => @values
      }
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        name:   data.fetch('name', ''),
        values: data.fetch('values', [])
      )
    end
  end

  class Body
    attr_accessor :type, :string, :json, :base64_bytes, :not_body, :content_type, :charset

    def initialize(type: nil, string: nil, json: nil, base64_bytes: nil, not_body: nil, content_type: nil, charset: nil)
      @type = type
      @string = string
      @json = json
      @base64_bytes = base64_bytes
      @not_body = not_body
      @content_type = content_type
      @charset = charset
    end

    def to_h
      result = {}
      result['type']        = @type        unless @type.nil?
      result['string']      = @string      unless @string.nil?
      result['json']        = @json        unless @json.nil?
      result['base64Bytes'] = @base64_bytes unless @base64_bytes.nil?
      result['not']         = @not_body    unless @not_body.nil?
      result['contentType'] = @content_type unless @content_type.nil?
      result['charset']     = @charset     unless @charset.nil?
      result
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        type:         data['type'],
        string:       data['string'],
        json:         data['json'],
        base64_bytes: data['base64Bytes'],
        not_body:     data['not'],
        content_type: data['contentType'],
        charset:      data['charset']
      )
    end

    def self.string(value)
      new(type: 'STRING', string: value)
    end

    def self.json(value)
      new(type: 'JSON', json: value)
    end

    def self.regex(value)
      new(type: 'REGEX', string: value)
    end

    def self.exact(value)
      new(type: 'STRING', string: value)
    end

    def self.xml(value)
      new(type: 'XML', string: value)
    end

    def self.json_rpc(method_name, params_schema: nil)
      JsonRpcBody.new(method_name: method_name, params_schema: params_schema)
    end

    def self.graphql(query, operation_name: nil, variables_schema: nil)
      GraphQLBody.new(query: query, operation_name: operation_name, variables_schema: variables_schema)
    end
  end

  class JsonRpcBody
    attr_accessor :method_name, :params_schema, :not_body, :optional

    def initialize(method_name:, params_schema: nil, not_body: false, optional: false)
      @method_name = method_name
      @params_schema = params_schema
      @not_body = not_body
      @optional = optional
    end

    def to_h
      result = { 'type' => 'JSON_RPC', 'method' => @method_name }
      result['paramsSchema'] = @params_schema if @params_schema
      result['not'] = true if @not_body
      result['optional'] = true if @optional
      result
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        method_name:   data['method'] || '',
        params_schema: data['paramsSchema'],
        not_body:      data.fetch('not', false),
        optional:      data.fetch('optional', false)
      )
    end
  end

  class GraphQLBody
    attr_accessor :query, :operation_name, :variables_schema, :not_body, :optional

    def initialize(query:, operation_name: nil, variables_schema: nil, not_body: false, optional: false)
      @query = query
      @operation_name = operation_name
      @variables_schema = variables_schema
      @not_body = not_body
      @optional = optional
    end

    def to_h
      result = { 'type' => 'GRAPHQL', 'query' => @query }
      result['operationName'] = @operation_name if @operation_name
      result['variablesSchema'] = @variables_schema if @variables_schema
      result['not'] = true if @not_body
      result['optional'] = true if @optional
      result
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        query:            data['query'] || '',
        operation_name:   data['operationName'],
        variables_schema: data['variablesSchema'],
        not_body:         data.fetch('not', false),
        optional:         data.fetch('optional', false)
      )
    end
  end

  class SocketAddress
    attr_accessor :host, :port, :scheme

    def initialize(host: nil, port: nil, scheme: nil)
      @host = host
      @port = port
      @scheme = scheme
    end

    def to_h
      MockServer.strip_none({
        'host'   => @host,
        'port'   => @port,
        'scheme' => @scheme
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        host:   data['host'],
        port:   data['port'],
        scheme: data['scheme']
      )
    end
  end

  class HttpRequest
    attr_accessor :method, :path, :query_string_parameters, :headers,
                  :cookies, :body, :secure, :keep_alive, :path_parameters,
                  :socket_address

    def initialize(method: nil, path: nil, query_string_parameters: nil, headers: nil,
                   cookies: nil, body: nil, secure: nil, keep_alive: nil,
                   path_parameters: nil, socket_address: nil)
      @method = method
      @path = path
      @query_string_parameters = query_string_parameters
      @headers = headers
      @cookies = cookies
      @body = body
      @secure = secure
      @keep_alive = keep_alive
      @path_parameters = path_parameters
      @socket_address = socket_address
    end

    def to_h
      MockServer.strip_none({
        'method'                => @method,
        'path'                  => @path,
        'queryStringParameters' => MockServer.serialize_key_multi_values(@query_string_parameters),
        'headers'               => MockServer.serialize_key_multi_values(@headers),
        'cookies'               => MockServer.serialize_key_multi_values(@cookies),
        'body'                  => MockServer.serialize_body(@body),
        'secure'                => @secure,
        'keepAlive'             => @keep_alive,
        'pathParameters'        => MockServer.serialize_key_multi_values(@path_parameters),
        'socketAddress'         => @socket_address&.to_h
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        method:                  data['method'],
        path:                    data['path'],
        query_string_parameters: MockServer.deserialize_key_multi_values(data['queryStringParameters']),
        headers:                 MockServer.deserialize_key_multi_values(data['headers']),
        cookies:                 MockServer.deserialize_key_multi_values(data['cookies']),
        body:                    MockServer.deserialize_body(data['body']),
        secure:                  data['secure'],
        keep_alive:              data['keepAlive'],
        path_parameters:         MockServer.deserialize_key_multi_values(data['pathParameters']),
        socket_address:          SocketAddress.from_hash(data['socketAddress'])
      )
    end

    def self.request(path: nil)
      new(path: path)
    end

    def with_method(method)
      @method = method
      self
    end

    def with_path(path)
      @path = path
      self
    end

    def with_header(name, *values)
      @headers ||= []
      @headers << KeyToMultiValue.new(name: name, values: values.flatten)
      self
    end

    def with_query_param(name, *values)
      @query_string_parameters ||= []
      @query_string_parameters << KeyToMultiValue.new(name: name, values: values.flatten)
      self
    end

    def with_cookie(name, value)
      @cookies ||= []
      @cookies << KeyToMultiValue.new(name: name, values: [value])
      self
    end

    def with_body(body)
      @body = body
      self
    end

    def with_secure(secure)
      @secure = secure
      self
    end

    def with_keep_alive(keep_alive)
      @keep_alive = keep_alive
      self
    end
  end

  class ConnectionOptions
    attr_accessor :close_socket, :close_socket_delay, :suppress_content_length_header,
                  :content_length_header_override, :suppress_connection_header,
                  :chunk_size, :chunk_delay, :keep_alive_override

    def initialize(close_socket: nil, close_socket_delay: nil,
                   suppress_content_length_header: nil, content_length_header_override: nil,
                   suppress_connection_header: nil, chunk_size: nil, chunk_delay: nil,
                   keep_alive_override: nil)
      @close_socket = close_socket
      @close_socket_delay = close_socket_delay
      @suppress_content_length_header = suppress_content_length_header
      @content_length_header_override = content_length_header_override
      @suppress_connection_header = suppress_connection_header
      @chunk_size = chunk_size
      @chunk_delay = chunk_delay
      @keep_alive_override = keep_alive_override
    end

    def to_h
      MockServer.strip_none({
        'closeSocket'                 => @close_socket,
        'closeSocketDelay'            => @close_socket_delay&.to_h,
        'suppressContentLengthHeader' => @suppress_content_length_header,
        'contentLengthHeaderOverride' => @content_length_header_override,
        'suppressConnectionHeader'    => @suppress_connection_header,
        'chunkSize'                   => @chunk_size,
        'chunkDelay'                  => @chunk_delay&.to_h,
        'keepAliveOverride'           => @keep_alive_override
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        close_socket:                  data['closeSocket'],
        close_socket_delay:            Delay.from_hash(data['closeSocketDelay']),
        suppress_content_length_header: data['suppressContentLengthHeader'],
        content_length_header_override: data['contentLengthHeaderOverride'],
        suppress_connection_header:     data['suppressConnectionHeader'],
        chunk_size:                     data['chunkSize'],
        chunk_delay:                    Delay.from_hash(data['chunkDelay']),
        keep_alive_override:            data['keepAliveOverride']
      )
    end
  end

  class HttpResponse
    attr_accessor :status_code, :reason_phrase, :headers, :cookies,
                  :body, :delay, :connection_options, :primary

    def initialize(status_code: nil, reason_phrase: nil, headers: nil, cookies: nil,
                   body: nil, delay: nil, connection_options: nil, primary: nil)
      @status_code = status_code
      @reason_phrase = reason_phrase
      @headers = headers
      @cookies = cookies
      @body = body
      @delay = delay
      @connection_options = connection_options
      @primary = primary
    end

    def to_h
      MockServer.strip_none({
        'statusCode'       => @status_code,
        'reasonPhrase'     => @reason_phrase,
        'headers'          => MockServer.serialize_key_multi_values(@headers),
        'cookies'          => MockServer.serialize_key_multi_values(@cookies),
        'body'             => MockServer.serialize_body(@body),
        'delay'            => @delay&.to_h,
        'connectionOptions' => @connection_options&.to_h,
        'primary'          => @primary
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        status_code:       data['statusCode'],
        reason_phrase:     data['reasonPhrase'],
        headers:           MockServer.deserialize_key_multi_values(data['headers']),
        cookies:           MockServer.deserialize_key_multi_values(data['cookies']),
        body:              MockServer.deserialize_body(data['body']),
        delay:             Delay.from_hash(data['delay']),
        connection_options: ConnectionOptions.from_hash(data['connectionOptions']),
        primary:           data['primary']
      )
    end

    def self.response(body: nil, status_code: nil)
      resp = new
      if body
        resp.body = body
        if status_code.nil?
          resp.status_code = 200
          resp.reason_phrase = 'OK'
        else
          resp.status_code = status_code
        end
      elsif status_code
        resp.status_code = status_code
      end
      resp
    end

    def self.not_found_response
      new(status_code: 404, reason_phrase: 'Not Found')
    end

    def with_status_code(status_code)
      @status_code = status_code
      self
    end

    def with_header(name, *values)
      @headers ||= []
      @headers << KeyToMultiValue.new(name: name, values: values.flatten)
      self
    end

    def with_cookie(name, value)
      @cookies ||= []
      @cookies << KeyToMultiValue.new(name: name, values: [value])
      self
    end

    def with_body(body)
      @body = body
      self
    end

    def with_delay(delay)
      @delay = delay
      self
    end

    def with_reason_phrase(reason_phrase)
      @reason_phrase = reason_phrase
      self
    end
  end

  class HttpForward
    attr_accessor :host, :port, :scheme, :delay, :primary

    def initialize(host: nil, port: nil, scheme: nil, delay: nil, primary: nil)
      @host = host
      @port = port
      @scheme = scheme
      @delay = delay
      @primary = primary
    end

    def to_h
      MockServer.strip_none({
        'host'    => @host,
        'port'    => @port,
        'scheme'  => @scheme,
        'delay'   => @delay&.to_h,
        'primary' => @primary
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        host:    data['host'],
        port:    data['port'],
        scheme:  data['scheme'],
        delay:   Delay.from_hash(data['delay']),
        primary: data['primary']
      )
    end

    def self.forward
      new
    end
  end

  class HttpTemplate
    attr_accessor :template_type, :template, :delay, :primary

    def initialize(template_type: 'JAVASCRIPT', template: nil, delay: nil, primary: nil)
      @template_type = template_type
      @template = template
      @delay = delay
      @primary = primary
    end

    def to_h
      MockServer.strip_none({
        'templateType' => @template_type,
        'template'     => @template,
        'delay'        => @delay&.to_h,
        'primary'      => @primary
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        template_type: data.fetch('templateType', 'JAVASCRIPT'),
        template:      data['template'],
        delay:         Delay.from_hash(data['delay']),
        primary:       data['primary']
      )
    end

    def self.template(template_type, template = nil)
      new(template_type: template_type, template: template)
    end
  end

  class HttpClassCallback
    attr_accessor :callback_class, :delay, :primary

    def initialize(callback_class: nil, delay: nil, primary: nil)
      @callback_class = callback_class
      @delay = delay
      @primary = primary
    end

    def to_h
      MockServer.strip_none({
        'callbackClass' => @callback_class,
        'delay'         => @delay&.to_h,
        'primary'       => @primary
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        callback_class: data['callbackClass'],
        delay:          Delay.from_hash(data['delay']),
        primary:        data['primary']
      )
    end

    def self.callback(callback_class: nil)
      new(callback_class: callback_class)
    end
  end

  class HttpObjectCallback
    attr_accessor :client_id, :response_callback, :delay, :primary

    def initialize(client_id: nil, response_callback: nil, delay: nil, primary: nil)
      @client_id = client_id
      @response_callback = response_callback
      @delay = delay
      @primary = primary
    end

    def to_h
      MockServer.strip_none({
        'clientId'         => @client_id,
        'responseCallback' => @response_callback,
        'delay'            => @delay&.to_h,
        'primary'          => @primary
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        client_id:         data['clientId'],
        response_callback: data['responseCallback'],
        delay:             Delay.from_hash(data['delay']),
        primary:           data['primary']
      )
    end
  end

  class HttpError
    attr_accessor :drop_connection, :response_bytes, :delay, :primary

    def initialize(drop_connection: nil, response_bytes: nil, delay: nil, primary: nil)
      @drop_connection = drop_connection
      @response_bytes = response_bytes
      @delay = delay
      @primary = primary
    end

    def to_h
      MockServer.strip_none({
        'dropConnection' => @drop_connection,
        'responseBytes'  => @response_bytes,
        'delay'          => @delay&.to_h,
        'primary'        => @primary
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        drop_connection: data['dropConnection'],
        response_bytes:  data['responseBytes'],
        delay:           Delay.from_hash(data['delay']),
        primary:         data['primary']
      )
    end

    def self.error
      new
    end
  end

  class HttpOverrideForwardedRequest
    attr_accessor :http_request, :http_response, :delay,
                  :request_modifier, :response_modifier, :primary

    def initialize(http_request: nil, http_response: nil, delay: nil,
                   request_modifier: nil, response_modifier: nil, primary: nil)
      @http_request = http_request
      @http_response = http_response
      @delay = delay
      @request_modifier = request_modifier
      @response_modifier = response_modifier
      @primary = primary
    end

    def to_h
      MockServer.strip_none({
        'httpRequest'      => @http_request&.to_h,
        'httpResponse'     => @http_response&.to_h,
        'delay'            => @delay&.to_h,
        'requestModifier'  => @request_modifier,
        'responseModifier' => @response_modifier,
        'primary'          => @primary
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        http_request:      HttpRequest.from_hash(data['httpRequest']),
        http_response:     HttpResponse.from_hash(data['httpResponse']),
        delay:             Delay.from_hash(data['delay']),
        request_modifier:  data['requestModifier'],
        response_modifier: data['responseModifier'],
        primary:           data['primary']
      )
    end

    def self.forward_overridden_request(request: nil)
      new(http_request: request)
    end
  end

  class HttpRequestAndHttpResponse
    attr_accessor :http_request, :http_response

    def initialize(http_request: nil, http_response: nil)
      @http_request = http_request
      @http_response = http_response
    end

    def to_h
      MockServer.strip_none({
        'httpRequest'  => @http_request&.to_h,
        'httpResponse' => @http_response&.to_h
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        http_request:  HttpRequest.from_hash(data['httpRequest']),
        http_response: HttpResponse.from_hash(data['httpResponse'])
      )
    end
  end

  class ExpectationId
    attr_accessor :id

    def initialize(id: '')
      @id = id
    end

    def to_h
      { 'id' => @id }
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(id: data.fetch('id', ''))
    end
  end

  class SseEvent
    attr_accessor :event, :data, :id, :retry, :delay

    def initialize(event: nil, data: nil, id: nil, retry_ms: nil, delay: nil)
      @event = event
      @data = data
      @id = id
      @retry = retry_ms
      @delay = delay
    end

    def to_h
      result = {}
      result['event'] = @event unless @event.nil?
      result['data'] = @data unless @data.nil?
      result['id'] = @id unless @id.nil?
      result['retry'] = @retry unless @retry.nil?
      result['delay'] = @delay.to_h if @delay
      result
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        event:    data['event'],
        data:     data['data'],
        id:       data['id'],
        retry_ms: data['retry'],
        delay:    Delay.from_hash(data['delay'])
      )
    end
  end

  class HttpSseResponse
    attr_accessor :status_code, :headers, :events, :close_connection, :delay, :primary

    def initialize(status_code: nil, headers: nil, events: nil, close_connection: nil, delay: nil, primary: nil)
      @status_code = status_code
      @headers = headers
      @events = events
      @close_connection = close_connection
      @delay = delay
      @primary = primary
    end

    def to_h
      result = {}
      result['statusCode'] = @status_code unless @status_code.nil?
      result['headers'] = MockServer.serialize_key_multi_values(@headers) if @headers
      result['events'] = @events&.map(&:to_h) if @events
      result['closeConnection'] = @close_connection unless @close_connection.nil?
      result['delay'] = @delay.to_h if @delay
      result['primary'] = @primary unless @primary.nil?
      result
    end

    def self.from_hash(data)
      return nil if data.nil?

      events_data = data['events']
      events = events_data&.map { |e| SseEvent.from_hash(e) }
      new(
        status_code:      data['statusCode'],
        headers:          MockServer.deserialize_key_multi_values(data['headers']),
        events:           events,
        close_connection: data['closeConnection'],
        delay:            Delay.from_hash(data['delay']),
        primary:          data['primary']
      )
    end
  end

  class WebSocketMessage
    attr_accessor :text, :binary, :delay

    def initialize(text: nil, binary: nil, delay: nil)
      @text = text
      @binary = binary
      @delay = delay
    end

    def to_h
      result = {}
      result['text'] = @text unless @text.nil?
      result['binary'] = Base64.strict_encode64(@binary) unless @binary.nil?
      result['delay'] = @delay.to_h if @delay
      result
    end

    def self.from_hash(data)
      return nil if data.nil?

      binary_data = data['binary']
      binary = binary_data ? Base64.strict_decode64(binary_data) : nil
      new(
        text:   data['text'],
        binary: binary,
        delay:  Delay.from_hash(data['delay'])
      )
    end
  end

  class HttpWebSocketResponse
    attr_accessor :subprotocol, :messages, :close_connection, :delay, :primary

    def initialize(subprotocol: nil, messages: nil, close_connection: nil, delay: nil, primary: nil)
      @subprotocol = subprotocol
      @messages = messages
      @close_connection = close_connection
      @delay = delay
      @primary = primary
    end

    def to_h
      result = {}
      result['subprotocol'] = @subprotocol unless @subprotocol.nil?
      result['messages'] = @messages&.map(&:to_h) if @messages
      result['closeConnection'] = @close_connection unless @close_connection.nil?
      result['delay'] = @delay.to_h if @delay
      result['primary'] = @primary unless @primary.nil?
      result
    end

    def self.from_hash(data)
      return nil if data.nil?

      messages_data = data['messages']
      messages = messages_data&.map { |m| WebSocketMessage.from_hash(m) }
      new(
        subprotocol:      data['subprotocol'],
        messages:         messages,
        close_connection: data['closeConnection'],
        delay:            Delay.from_hash(data['delay']),
        primary:          data['primary']
      )
    end
  end

  class AfterAction
    attr_accessor :http_request, :http_class_callback, :http_object_callback, :delay

    def initialize(http_request: nil, http_class_callback: nil, http_object_callback: nil, delay: nil)
      @http_request = http_request
      @http_class_callback = http_class_callback
      @http_object_callback = http_object_callback
      @delay = delay
    end

    def to_h
      MockServer.strip_none({
        'httpRequest'        => @http_request&.to_h,
        'httpClassCallback'  => @http_class_callback&.to_h,
        'httpObjectCallback' => @http_object_callback&.to_h,
        'delay'              => @delay&.to_h
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        http_request:        HttpRequest.from_hash(data['httpRequest']),
        http_class_callback: HttpClassCallback.from_hash(data['httpClassCallback']),
        http_object_callback: HttpObjectCallback.from_hash(data['httpObjectCallback']),
        delay:               Delay.from_hash(data['delay'])
      )
    end
  end

  class Expectation
    attr_accessor :id, :priority, :percentage, :http_request, :http_response,
                  :http_response_template, :http_response_class_callback,
                  :http_response_object_callback, :http_forward,
                  :http_forward_template, :http_forward_class_callback,
                  :http_forward_object_callback, :http_override_forwarded_request,
                  :http_error, :times, :time_to_live,
                  :http_sse_response, :http_websocket_response, :after_actions,
                  :scenario_name, :scenario_state, :new_scenario_state

    def initialize(id: nil, priority: nil, percentage: nil, http_request: nil, http_response: nil,
                   http_response_template: nil, http_response_class_callback: nil,
                   http_response_object_callback: nil, http_forward: nil,
                   http_forward_template: nil, http_forward_class_callback: nil,
                   http_forward_object_callback: nil, http_override_forwarded_request: nil,
                   http_error: nil, times: nil, time_to_live: nil,
                   http_sse_response: nil, http_websocket_response: nil, after_actions: nil,
                   scenario_name: nil, scenario_state: nil, new_scenario_state: nil)
      @id = id
      @priority = priority
      @percentage = percentage
      @http_request = http_request
      @http_response = http_response
      @http_response_template = http_response_template
      @http_response_class_callback = http_response_class_callback
      @http_response_object_callback = http_response_object_callback
      @http_forward = http_forward
      @http_forward_template = http_forward_template
      @http_forward_class_callback = http_forward_class_callback
      @http_forward_object_callback = http_forward_object_callback
      @http_override_forwarded_request = http_override_forwarded_request
      @http_error = http_error
      @times = times
      @time_to_live = time_to_live
      @http_sse_response = http_sse_response
      @http_websocket_response = http_websocket_response
      @after_actions = after_actions
      @scenario_name = scenario_name
      @scenario_state = scenario_state
      @new_scenario_state = new_scenario_state
    end

    def to_h
      after_actions_h = nil
      if @after_actions.is_a?(Array)
        after_actions_h = @after_actions.map(&:to_h) unless @after_actions.empty?
      elsif @after_actions
        after_actions_h = @after_actions.to_h
      end

      MockServer.strip_none({
        'id'                           => @id,
        'priority'                     => @priority,
        'percentage'                   => @percentage,
        'httpRequest'                  => @http_request&.to_h,
        'httpResponse'                 => @http_response&.to_h,
        'httpResponseTemplate'         => @http_response_template&.to_h,
        'httpResponseClassCallback'    => @http_response_class_callback&.to_h,
        'httpResponseObjectCallback'   => @http_response_object_callback&.to_h,
        'httpForward'                  => @http_forward&.to_h,
        'httpForwardTemplate'          => @http_forward_template&.to_h,
        'httpForwardClassCallback'     => @http_forward_class_callback&.to_h,
        'httpForwardObjectCallback'    => @http_forward_object_callback&.to_h,
        'httpOverrideForwardedRequest' => @http_override_forwarded_request&.to_h,
        'httpError'                    => @http_error&.to_h,
        'httpSseResponse'              => @http_sse_response&.to_h,
        'httpWebSocketResponse'        => @http_websocket_response&.to_h,
        'afterActions'                 => after_actions_h,
        'times'                        => @times&.to_h,
        'timeToLive'                   => @time_to_live&.to_h,
        'scenarioName'                 => @scenario_name,
        'scenarioState'                => @scenario_state,
        'newScenarioState'             => @new_scenario_state
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      after_actions_data = data['afterActions']
      after_actions = if after_actions_data.is_a?(Array)
                        after_actions_data.map { |a| AfterAction.from_hash(a) }
                      elsif after_actions_data
                        AfterAction.from_hash(after_actions_data)
                      end

      new(
        id:                              data['id'],
        priority:                        data['priority'],
        percentage:                      data['percentage'],
        http_request:                    HttpRequest.from_hash(data['httpRequest']),
        http_response:                   HttpResponse.from_hash(data['httpResponse']),
        http_response_template:          HttpTemplate.from_hash(data['httpResponseTemplate']),
        http_response_class_callback:    HttpClassCallback.from_hash(data['httpResponseClassCallback']),
        http_response_object_callback:   HttpObjectCallback.from_hash(data['httpResponseObjectCallback']),
        http_forward:                    HttpForward.from_hash(data['httpForward']),
        http_forward_template:           HttpTemplate.from_hash(data['httpForwardTemplate']),
        http_forward_class_callback:     HttpClassCallback.from_hash(data['httpForwardClassCallback']),
        http_forward_object_callback:    HttpObjectCallback.from_hash(data['httpForwardObjectCallback']),
        http_override_forwarded_request: HttpOverrideForwardedRequest.from_hash(data['httpOverrideForwardedRequest']),
        http_error:                      HttpError.from_hash(data['httpError']),
        http_sse_response:               HttpSseResponse.from_hash(data['httpSseResponse']),
        http_websocket_response:         HttpWebSocketResponse.from_hash(data['httpWebSocketResponse']),
        after_actions:                   after_actions,
        times:                           Times.from_hash(data['times']),
        time_to_live:                    TimeToLive.from_hash(data['timeToLive']),
        scenario_name:                   data['scenarioName'],
        scenario_state:                  data['scenarioState'],
        new_scenario_state:              data['newScenarioState']
      )
    end
  end

  class OpenAPIDefinition
    attr_accessor :spec_url_or_payload, :operation_id

    def initialize(spec_url_or_payload: nil, operation_id: nil)
      @spec_url_or_payload = spec_url_or_payload
      @operation_id = operation_id
    end

    def to_h
      MockServer.strip_none({
        'specUrlOrPayload' => @spec_url_or_payload,
        'operationId'      => @operation_id
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        spec_url_or_payload: data['specUrlOrPayload'],
        operation_id:        data['operationId']
      )
    end
  end

  class OpenAPIExpectation
    attr_accessor :spec_url_or_payload, :operations_and_responses

    def initialize(spec_url_or_payload: nil, operations_and_responses: nil)
      @spec_url_or_payload = spec_url_or_payload
      @operations_and_responses = operations_and_responses
    end

    def to_h
      MockServer.strip_none({
        'specUrlOrPayload'      => @spec_url_or_payload,
        'operationsAndResponses' => @operations_and_responses
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        spec_url_or_payload:      data['specUrlOrPayload'],
        operations_and_responses: data['operationsAndResponses']
      )
    end
  end

  class VerificationTimes
    attr_accessor :at_least, :at_most

    def initialize(at_least: nil, at_most: nil)
      @at_least = at_least
      @at_most = at_most
    end

    def to_h
      MockServer.strip_none({
        'atLeast' => @at_least,
        'atMost'  => @at_most
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        at_least: data['atLeast'],
        at_most:  data['atMost']
      )
    end

    def self.at_least(count)
      new(at_least: count)
    end

    def self.at_most(count)
      new(at_most: count)
    end

    def self.exactly(count)
      new(at_least: count, at_most: count)
    end

    def self.once
      new(at_least: 1, at_most: 1)
    end

    def self.between(at_least, at_most)
      new(at_least: at_least, at_most: at_most)
    end
  end

  class Verification
    attr_accessor :http_request, :expectation_id, :times,
                  :maximum_number_of_request_to_return_in_verification_failure

    def initialize(http_request: nil, expectation_id: nil, times: nil,
                   maximum_number_of_request_to_return_in_verification_failure: nil)
      @http_request = http_request
      @expectation_id = expectation_id
      @times = times
      @maximum_number_of_request_to_return_in_verification_failure = maximum_number_of_request_to_return_in_verification_failure
    end

    def to_h
      MockServer.strip_none({
        'httpRequest'    => @http_request&.to_h,
        'expectationId'  => @expectation_id&.to_h,
        'times'          => @times&.to_h,
        'maximumNumberOfRequestToReturnInVerificationFailure' => @maximum_number_of_request_to_return_in_verification_failure
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(
        http_request:    HttpRequest.from_hash(data['httpRequest']),
        expectation_id:  ExpectationId.from_hash(data['expectationId']),
        times:           VerificationTimes.from_hash(data['times']),
        maximum_number_of_request_to_return_in_verification_failure: data['maximumNumberOfRequestToReturnInVerificationFailure']
      )
    end
  end

  class VerificationSequence
    attr_accessor :http_requests, :expectation_ids

    def initialize(http_requests: nil, expectation_ids: nil)
      @http_requests = http_requests
      @expectation_ids = expectation_ids
    end

    def to_h
      MockServer.strip_none({
        'httpRequests'   => @http_requests&.map(&:to_h),
        'expectationIds' => @expectation_ids&.map(&:to_h)
      })
    end

    def self.from_hash(data)
      return nil if data.nil?

      http_requests_data = data['httpRequests']
      expectation_ids_data = data['expectationIds']
      new(
        http_requests:  http_requests_data&.map { |r| HttpRequest.from_hash(r) },
        expectation_ids: expectation_ids_data&.map { |e| ExpectationId.from_hash(e) }
      )
    end
  end

  class Ports
    attr_accessor :ports

    def initialize(ports: [])
      @ports = ports
    end

    def to_h
      { 'ports' => @ports }
    end

    def self.from_hash(data)
      return nil if data.nil?

      new(ports: data.fetch('ports', []))
    end
  end

  # Alias matching the Python client
  RequestDefinition = HttpRequest
end
