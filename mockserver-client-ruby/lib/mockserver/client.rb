# frozen_string_literal: true

require 'net/http'
require 'uri'
require 'json'
require 'openssl'
require 'base64'

module MockServer
  # Synchronous MockServer client.
  #
  # Provides the full MockServer REST API plus a fluent builder DSL and
  # WebSocket-based object callback support.
  #
  # @example Basic usage
  #   client = MockServer::Client.new('localhost', 1080)
  #   client.when(
  #     HttpRequest.request(path: '/hello')
  #   ).respond(
  #     HttpResponse.response(body: 'world')
  #   )
  #   client.close
  #
  # @example Block form (auto-close)
  #   MockServer::Client.new('localhost', 1080) do |c|
  #     c.when(HttpRequest.request(path: '/hello'))
  #      .respond(HttpResponse.response(body: 'world'))
  #   end
  class Client
    HTTP_TIMEOUT = 60 # seconds, matching Python client

    # Default for clear's +filter:+ so an explicit nil filter is rejected
    # instead of silently clearing everything.
    NO_FILTER = Object.new.freeze
    private_constant :NO_FILTER

    # @param host [String]
    # @param port [Integer]
    # @param context_path [String]
    # @param secure [Boolean]
    # @param ca_cert_path [String, nil]
    # @param tls_verify [Boolean]
    # @param control_plane_basic_auth [Array(String, String), nil] control-plane HTTP Basic
    #   +[username, password]+, sent on every control-plane request and on the callback and
    #   breakpoint WebSockets; MockServer refuses Basic credentials without TLS, so use +secure: true+
    def initialize(host, port, context_path: '', secure: false,
                   ca_cert_path: nil, tls_verify: true, control_plane_basic_auth: nil)
      @control_plane_authorization = basic_authorization(control_plane_basic_auth)
      if @control_plane_authorization && !secure
        warn 'MockServer::Client: control-plane Basic credentials are configured without secure: true, so the password ' \
             'can be read on the network, and MockServer refuses them unless controlPlaneBasicAuthenticationAllowInsecure ' \
             'is set (tests only)'
      end
      @host = host
      @port = port
      @context_path = context_path
      @secure = secure
      @ca_cert_path = ca_cert_path
      @tls_verify = tls_verify
      @websocket_clients = []
      @websocket_mutex = Mutex.new

      scheme = secure ? 'https' : 'http'
      ctx_path = ''
      if context_path && !context_path.empty?
        ctx_path = context_path.start_with?('/') ? context_path : "/#{context_path}"
      end
      @base_url = "#{scheme}://#{host}:#{port}#{ctx_path}"

      if block_given?
        begin
          yield self
        ensure
          close
        end
      end
    end

    # -------------------------------------------------------------------
    # REST API methods
    # -------------------------------------------------------------------

    # Create or update expectations.
    # @param expectations [Array<Expectation>]
    # @return [Array<Expectation>]
    def upsert(*expectations)
      body = JSON.generate(expectations.map(&:to_h))
      status, response_body = request('PUT', '/mockserver/expectation', body)
      if status == 400
        raise Error, "Invalid expectation: #{response_body}"
      end

      if status >= 400
        raise Error, "Failed to upsert expectations (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |e| Expectation.from_hash(e) } if parsed.is_a?(Array)
      end
      expectations.to_a
    end

    # Create an OpenAPI expectation.
    # @param expectation [OpenAPIExpectation]
    # @return [nil]
    def open_api_expectation(expectation)
      body = JSON.generate(expectation.to_h)
      status, response_body = request('PUT', '/mockserver/openapi', body)
      if status >= 400
        raise Error, "Failed to create OpenAPI expectation (status=#{status}): #{response_body}"
      end

      nil
    end

    # Clear expectations and/or logs.
    #
    # With +filter:+ only the expectations matching both +request+ (when
    # given) and the filter's metadata / unmatched selection are cleared; the
    # event log is left untouched. A filter must select something and must not
    # page, otherwise ArgumentError is raised and nothing is sent.
    #
    # @param request [HttpRequest, nil]
    # @param type [String, nil] "EXPECTATIONS", "LOG", or "ALL"
    # @param filter [ExpectationFilter] selection of expectations to clear
    # @return [nil]
    def clear(request = nil, type: nil, filter: NO_FILTER)
      return clear_with_filter(request, type, filter) unless filter.equal?(NO_FILTER)

      query_params = {}
      query_params['type'] = type if type
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/clear', body, query_params.empty? ? nil : query_params
      )
      if status >= 400
        raise Error, "Failed to clear (status=#{status}): #{response_body}"
      end

      nil
    end

    # Clear the expectations whose metadata has +key+ with a value equal to
    # +value+ (see {ExpectationFilter#with_metadata}).
    # @return [nil]
    def clear_by_metadata(key, value)
      clear(filter: ExpectationFilter.new.with_metadata(key, value))
    end

    # Clear the expectations whose metadata has +key+, with any value.
    # @return [nil]
    def clear_by_metadata_key(key)
      clear(filter: ExpectationFilter.new.with_metadata_key(key))
    end

    # Clear the expectations that have not matched any request.
    # @return [nil]
    def clear_unmatched_expectations
      clear(filter: ExpectationFilter.new.with_unmatched(true))
    end

    # Clear by expectation ID.
    # @param expectation_id [String]
    # @param type [String, nil]
    # @return [nil]
    def clear_by_id(expectation_id, type: nil)
      query_params = {}
      query_params['type'] = type if type
      body = JSON.generate({ 'id' => expectation_id })
      status, response_body = do_request(
        'PUT', '/mockserver/clear', body, query_params.empty? ? nil : query_params
      )
      if status >= 400
        raise Error, "Failed to clear by id (status=#{status}): #{response_body}"
      end

      nil
    end

    # Reset all expectations and logs.
    # @return [nil]
    def reset
      status, response_body = request('PUT', '/mockserver/reset')
      if status >= 400
        raise Error, "Failed to reset (status=#{status}): #{response_body}"
      end

      nil
    ensure
      close
    end

    # -------------------------------------------------------------------
    # Clock Control
    # -------------------------------------------------------------------

    # Freeze the server clock at the given ISO-8601 instant.
    # If +instant+ is nil, the clock freezes at the current real time.
    # @param instant [String, nil] ISO-8601 instant (e.g. "2025-01-15T09:30:00Z")
    # @return [Hash] response with status, currentInstant, currentEpochMillis
    def freeze_clock(instant = nil)
      payload = { 'action' => 'freeze' }
      payload['instant'] = instant if instant
      body = JSON.generate(payload)
      status, response_body = request('PUT', '/mockserver/clock', body)
      if status >= 400
        raise Error, "Failed to freeze clock (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Advance the frozen clock by +duration_millis+ milliseconds.
    # @param duration_millis [Integer]
    # @return [Hash] response with status, currentInstant, currentEpochMillis
    def advance_clock(duration_millis)
      body = JSON.generate({ 'action' => 'advance', 'durationMillis' => duration_millis })
      status, response_body = request('PUT', '/mockserver/clock', body)
      if status >= 400
        raise Error, "Failed to advance clock (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Reset the server clock to real wall-clock time.
    # @return [Hash] response with status, currentInstant, currentEpochMillis
    def reset_clock
      body = JSON.generate({ 'action' => 'reset' })
      status, response_body = request('PUT', '/mockserver/clock', body)
      if status >= 400
        raise Error, "Failed to reset clock (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Query the current clock status.
    # @return [Hash] with currentInstant, currentEpochMillis, frozen
    def clock_status
      status, response_body = request('GET', '/mockserver/clock')
      if status >= 400
        raise Error, "Failed to get clock status (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # -------------------------------------------------------------------
    # Metrics
    # -------------------------------------------------------------------

    # Retrieve the JSON metric counter snapshot
    # (PUT /mockserver/retrieve?type=METRICS).
    #
    # Returns a Hash mapping each metric name to its long value. When metrics
    # are disabled on the server the snapshot is an empty Hash.
    #
    # @return [Hash] metric name => value
    def retrieve_metrics
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', '', { 'type' => 'METRICS' }
      )
      if status >= 400
        raise Error, "Failed to retrieve metrics (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Scrape the Prometheus exposition text (GET /mockserver/metrics).
    #
    # Returns the raw Prometheus/OpenMetrics exposition text. When metrics are
    # disabled on the server this endpoint returns 404 and an {Error} is raised.
    #
    # @return [String] the Prometheus exposition text
    def scrape_metrics
      status, response_body = request('GET', '/mockserver/metrics')
      if status == 404
        raise Error, 'Failed to scrape metrics (status=404): metrics are disabled ' \
                     '(set metricsEnabled=true on the server to enable them)'
      end
      if status >= 400
        raise Error, "Failed to scrape metrics (status=#{status}): #{response_body}"
      end

      response_body || ''
    end

    # -------------------------------------------------------------------
    # Configuration
    # -------------------------------------------------------------------

    # Read the effective live configuration (GET /mockserver/configuration).
    #
    # @return [String] the serialized configuration JSON
    def retrieve_configuration
      status, response_body = request('GET', '/mockserver/configuration')
      if status >= 400
        raise Error, "Failed to retrieve configuration (status=#{status}): #{response_body}"
      end

      response_body || ''
    end

    # Update the live configuration (PUT /mockserver/configuration).
    #
    # +config_json+ is a ConfigurationDTO JSON document; only the fields present
    # are applied (partial update). A nil value is sent as an empty body.
    # Properties fixed at startup, such as forwardProxyBlockPrivateNetworks,
    # forwardProxyAllowedHosts and forwardProxyDeniedHosts, are ignored and the
    # server logs a warning.
    #
    # @param config_json [String, nil] the ConfigurationDTO JSON
    # @return [String] the serialized updated configuration JSON
    def update_configuration(config_json)
      body = config_json.nil? ? '' : config_json.to_s
      status, response_body = request('PUT', '/mockserver/configuration', body)
      if status >= 400
        raise Error, "Failed to update configuration (status=#{status}): #{response_body}"
      end

      response_body || ''
    end

    # -------------------------------------------------------------------
    # Drift detection
    # -------------------------------------------------------------------

    # Retrieve the recorded mock drift report (GET /mockserver/drift).
    #
    # Returns the parsed report, a Hash of the form
    # +{ "count" => <n>, "drifts" => [ ... ] }+, where each entry describes a
    # difference detected between a mock's configured response and the live
    # upstream response for the same request. When no drift has been recorded an
    # empty Hash is returned.
    #
    # @return [Hash] the parsed drift report
    def retrieve_drift
      status, response_body = request('GET', '/mockserver/drift')
      if status >= 400
        raise Error, "Failed to retrieve drift (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Clear all recorded mock drift (PUT /mockserver/drift/clear).
    # @return [nil]
    def clear_drift
      status, response_body = request('PUT', '/mockserver/drift/clear')
      if status >= 400
        raise Error, "Failed to clear drift (status=#{status}): #{response_body}"
      end

      nil
    end

    # -------------------------------------------------------------------
    # Pact (import / export / verify)
    # -------------------------------------------------------------------

    # Import a Pact v3 contract as expectations (PUT /mockserver/pact/import).
    #
    # @param json [String] the Pact v3 contract JSON (must not be blank)
    # @return [String] the JSON array of upserted expectations
    # @raise [ArgumentError] if +json+ is nil or blank
    def pact_import(json)
      if json.nil? || json.to_s.strip.empty?
        raise ArgumentError, 'pact JSON must not be empty'
      end

      status, response_body = request('PUT', '/mockserver/pact/import', json.to_s)
      if status >= 400
        raise Error, "Failed to import pact (status=#{status}): #{response_body}"
      end

      response_body || ''
    end

    # Export the active expectations as a Pact v3 contract
    # (PUT /mockserver/pact?consumer=&provider=).
    #
    # A query param is only added when its value is non-blank; otherwise the
    # server defaults are used.
    #
    # @param consumer [String, nil] the Pact consumer name
    # @param provider [String, nil] the Pact provider name
    # @return [String] the generated Pact v3 contract JSON
    def pact_export(consumer: nil, provider: nil)
      query_params = {}
      query_params['consumer'] = consumer if consumer && !consumer.to_s.empty?
      query_params['provider'] = provider if provider && !provider.to_s.empty?
      status, response_body = do_request(
        'PUT', '/mockserver/pact', nil, query_params.empty? ? nil : query_params
      )
      if status >= 400
        raise Error, "Failed to export pact (status=#{status}): #{response_body}"
      end

      response_body || ''
    end

    # Verify a Pact v3 contract against the active expectations
    # (PUT /mockserver/pact/verify).
    #
    # The server encodes the verdict in the HTTP status: 202 when every
    # interaction matches (PASS), 406 when verification fails (FAIL). Both
    # carry the verification report JSON, which is returned verbatim in both
    # cases (a FAIL does not raise) so callers can inspect the report.
    #
    # @param json [String] the Pact v3 contract JSON to verify (must not be blank)
    # @return [String] the verification report JSON
    # @raise [ArgumentError] if +json+ is nil or blank
    def pact_verify(json)
      if json.nil? || json.to_s.strip.empty?
        raise ArgumentError, 'pact JSON must not be empty'
      end

      status, response_body = request('PUT', '/mockserver/pact/verify', json.to_s)
      if status == 202 || status == 406
        return response_body || ''
      end
      if status >= 400
        raise Error, "Failed to verify pact (status=#{status}): #{response_body}"
      end

      response_body || ''
    end

    # -------------------------------------------------------------------
    # File store (store / retrieve / list / delete)
    # -------------------------------------------------------------------

    # Store a file in the in-memory file store (PUT /mockserver/files/store).
    #
    # @param name [String] the file name/key
    # @param content [String] the UTF-8 file content
    # @return [Hash] response of the form { "name" => ..., "size" => ... }
    def store_file(name, content)
      body = JSON.generate({ 'name' => name, 'content' => content })
      status, response_body = request('PUT', '/mockserver/files/store', body)
      if status >= 400
        raise Error, "Failed to store file (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Retrieve a file's raw content (PUT /mockserver/files/retrieve).
    #
    # Returns the raw 200 body verbatim. An unknown file yields a 404 which
    # raises an {Error} (there is no null-on-404 fallback).
    #
    # @param name [String] the file name/key
    # @return [String] the raw file content
    def retrieve_file(name)
      body = JSON.generate({ 'name' => name })
      status, response_body = request('PUT', '/mockserver/files/retrieve', body)
      if status == 404
        raise Error, "File not found (status=404): #{name}"
      end
      if status >= 400
        raise Error, "Failed to retrieve file (status=#{status}): #{response_body}"
      end

      response_body || ''
    end

    # List the names of all stored files (PUT /mockserver/files/list).
    #
    # @return [Array<String>] the file names
    def list_files
      status, response_body = request('PUT', '/mockserver/files/list')
      if status >= 400
        raise Error, "Failed to list files (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed if parsed.is_a?(Array)
      end
      []
    end

    # Delete a stored file (PUT /mockserver/files/delete).
    #
    # An unknown file yields a 404 which raises an {Error}.
    #
    # @param name [String] the file name/key
    # @return [nil]
    def delete_file(name)
      body = JSON.generate({ 'name' => name })
      status, response_body = request('PUT', '/mockserver/files/delete', body)
      if status == 404
        raise Error, "File not found (status=404): #{name}"
      end
      if status >= 400
        raise Error, "Failed to delete file (status=#{status}): #{response_body}"
      end

      nil
    end

    # -------------------------------------------------------------------
    # Import (HAR / Postman)
    # -------------------------------------------------------------------

    # Import a HAR document as expectations (PUT /mockserver/import?format=har).
    #
    # @param har_json [String] the HAR JSON document (must not be blank)
    # @return [Array<Expectation>] the upserted expectations
    # @raise [ArgumentError] if +har_json+ is nil or blank
    def import_har(har_json)
      import_document(har_json, 'har')
    end

    # Import a Postman collection as expectations
    # (PUT /mockserver/import?format=postman).
    #
    # @param collection_json [String] the Postman collection JSON (must not be blank)
    # @return [Array<Expectation>] the upserted expectations
    # @raise [ArgumentError] if +collection_json+ is nil or blank
    def import_postman_collection(collection_json)
      import_document(collection_json, 'postman')
    end

    # -------------------------------------------------------------------
    # Operating mode (SIMULATE / SPY / CAPTURE)
    # -------------------------------------------------------------------

    # Valid operating modes (parallels +org.mockserver.mock.MockMode+).
    MODE_SIMULATE = 'SIMULATE'
    MODE_SPY = 'SPY'
    MODE_CAPTURE = 'CAPTURE'

    # Set the high-level operating mode (PUT /mockserver/mode?mode=<MODE>).
    #
    # +mode+ may be a Symbol or String (case-insensitive), one of
    # +:simulate+/+:spy+/+:capture+ (or the {MODE_SIMULATE}, {MODE_SPY},
    # {MODE_CAPTURE} constants). Setting the mode also flips the
    # proxy-on-no-match behaviour on the server.
    #
    # @param mode [String, Symbol] the operating mode
    # @return [Hash] response of the form
    #   { "mode" => ..., "proxyUnmatchedRequests" => ... }
    def set_mode(mode)
      mode_value = mode.to_s.upcase
      status, response_body = do_request(
        'PUT', '/mockserver/mode', nil, { 'mode' => mode_value }
      )
      if status >= 400
        raise Error, "Failed to set mode (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Read the current operating mode (GET /mockserver/mode).
    #
    # @return [Hash] response of the form
    #   { "mode" => ..., "proxyUnmatchedRequests" => ... }
    def retrieve_mode
      status, response_body = request('GET', '/mockserver/mode')
      if status >= 400
        raise Error, "Failed to retrieve mode (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # -------------------------------------------------------------------
    # WSDL -> expectations
    # -------------------------------------------------------------------

    # Generate expectations from a WSDL document (PUT /mockserver/wsdl).
    #
    # The WSDL XML is sent as the raw request body with an XML content type.
    #
    # @param wsdl [String] the WSDL document (XML, must not be blank)
    # @return [Array<Expectation>] the generated (upserted) expectations
    # @raise [ArgumentError] if +wsdl+ is nil or blank
    def wsdl_expectation(wsdl)
      if wsdl.nil? || wsdl.to_s.strip.empty?
        raise ArgumentError, 'WSDL must not be empty'
      end

      status, response_body = request(
        'PUT', '/mockserver/wsdl', wsdl.to_s, content_type: 'application/xml'
      )
      if status >= 400
        raise Error, "Failed to generate WSDL expectations (status=#{status}): #{response_body}"
      end

      expectations_from_response(response_body)
    end

    # Register a service-scoped HTTP chaos profile for an upstream host. The profile
    # is applied to every matched forward expectation to that host that does not
    # define its own chaos (an expectation's own chaos always wins). The host is
    # matched case-insensitively, ignoring any +:port+.
    # @param host [String] the upstream host to break
    # @param chaos [HttpChaosProfile] the chaos profile to apply
    # @param ttl_millis [Integer, nil] if set, the chaos auto-reverts after this many ms
    # @return [Hash] response with status and host
    def set_service_chaos(host, chaos, ttl_millis = nil)
      payload = { 'host' => host, 'chaos' => chaos.to_h }
      payload['ttlMillis'] = ttl_millis unless ttl_millis.nil?
      body = JSON.generate(payload)
      status, response_body = request('PUT', '/mockserver/serviceChaos', body)
      if status >= 400
        raise Error, "Failed to set service chaos (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Remove the service-scoped chaos profile registered for +host+.
    # @param host [String]
    # @return [Hash]
    def remove_service_chaos(host)
      body = JSON.generate({ 'host' => host, 'remove' => true })
      status, response_body = request('PUT', '/mockserver/serviceChaos', body)
      if status >= 400
        raise Error, "Failed to remove service chaos (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Clear all service-scoped chaos profiles.
    # @return [Hash]
    def clear_service_chaos
      body = JSON.generate({ 'clear' => true })
      status, response_body = request('PUT', '/mockserver/serviceChaos', body)
      if status >= 400
        raise Error, "Failed to clear service chaos (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Query the current service-scoped chaos registrations.
    # @return [Hash] of the form { "services" => { host => profile, ... } }
    def service_chaos_status
      status, response_body = request('GET', '/mockserver/serviceChaos')
      if status >= 400
        raise Error, "Failed to get service chaos (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # -------------------------------------------------------------------
    # Load scenario registry (load injection)
    # -------------------------------------------------------------------
    #
    # The load scenario registry decouples *registering* a scenario from
    # *running* it:
    #
    #   * registering (load_scenario) only stores the definition keyed by its
    #     unique +name+ and is allowed even when +loadGenerationEnabled+ is off;
    #   * starting (start_load_scenarios) is what actually drives traffic and
    #     requires +loadGenerationEnabled+ on the server (otherwise a 403).
    #
    # Scenario states are: LOADED, PENDING, RUNNING, COMPLETED, STOPPED.

    # Register (load) a scenario into the registry without running it.
    #
    # +scenario+ may be a {LoadScenario} model (which responds to +to_h+) or a
    # plain Hash already shaped to the +LoadScenario+ JSON contract. It must
    # carry a unique +name+. Registering is permitted even when load generation
    # is disabled on the server.
    #
    # @param scenario [LoadScenario, Hash] the scenario to register
    # @return [Hash] parsed response of the form { "name" => ..., "state" => ... }
    def load_scenario(scenario)
      payload = scenario.respond_to?(:to_h) ? scenario.to_h : scenario
      body = JSON.generate(payload)
      status, response_body = request('PUT', '/mockserver/loadScenario', body)
      if status >= 400
        raise Error, "Failed to register load scenario (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # List all registered load scenarios.
    #
    # @return [Hash] parsed response of the form
    #   { "scenarios" => [ { "name" => ..., "state" => ..., "definition" => ..., "status" => ... }, ... ] }
    def load_scenarios
      status, response_body = request('GET', '/mockserver/loadScenario')
      if status >= 400
        raise Error, "Failed to list load scenarios (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Fetch a single registered load scenario by name.
    #
    # @param name [String] the unique scenario name
    # @return [Hash] parsed scenario entry { "name" => ..., "state" => ..., "definition" => ..., "status" => ... }
    # @raise [Error] if the scenario does not exist (404) or another failure occurs
    def get_load_scenario(name)
      status, response_body = request('GET', "/mockserver/loadScenario/#{encode_path_segment(name)}")
      if status == 404
        raise Error, "Load scenario not found (status=404): #{name}"
      end
      if status >= 400
        raise Error, "Failed to get load scenario (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Remove a single registered load scenario by name.
    #
    # @param name [String] the unique scenario name
    # @return [Hash] parsed response (may be empty)
    def delete_load_scenario(name)
      status, response_body = request('DELETE', "/mockserver/loadScenario/#{encode_path_segment(name)}")
      if status >= 400
        raise Error, "Failed to delete load scenario (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Clear all registered load scenarios.
    #
    # @return [Hash] parsed response (may be empty)
    def clear_load_scenarios
      status, response_body = request('DELETE', '/mockserver/loadScenario')
      if status >= 400
        raise Error, "Failed to clear load scenarios (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Start one or more registered scenarios.
    #
    # +names+ may be a single scenario name (String) or an Array of names; it is
    # always sent as { "names" => [...] }. Honours each scenario's
    # +startDelayMillis+. Requires +loadGenerationEnabled+ on the server; a 403
    # response raises a clear error explaining the feature is disabled.
    #
    # @param names [String, Array<String>] scenario name(s) to start
    # @return [Hash] parsed response of the form
    #   { "started" => [ { "name" => ..., "state" => ... }, ... ], "status" => ... }
    def start_load_scenarios(names)
      payload = { 'names' => Array(names) }
      body = JSON.generate(payload)
      status, response_body = request('PUT', '/mockserver/loadScenario/start', body)
      if status == 403
        raise Error, 'Load scenario start rejected (status=403): load generation is disabled ' \
                     '(set loadGenerationEnabled=true on the server to enable it)'
      end
      if status == 404
        raise Error, "Load scenario not found (status=404): #{response_body}"
      end
      if status >= 400
        raise Error, "Failed to start load scenarios (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Stop running scenarios.
    #
    # +names+ may be:
    #   * a single scenario name (String) -> { "names" => ["a"] }
    #   * an Array of names                -> { "names" => ["a", "b"] }
    #   * nil (the default)                -> empty body, which stops all running scenarios
    #
    # @param names [String, Array<String>, nil] scenario name(s) to stop, or nil for all
    # @return [Hash] parsed response of the form
    #   { "stopped" => [ ... ], "status" => ... }
    def stop_load_scenarios(names = nil)
      body = names.nil? ? nil : JSON.generate({ 'names' => Array(names) })
      status, response_body = request('PUT', '/mockserver/loadScenario/stop', body)
      if status >= 400
        raise Error, "Failed to stop load scenarios (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Convenience: register a scenario then immediately start it.
    #
    # Equivalent to calling {#load_scenario} followed by {#start_load_scenarios}
    # for the scenario's name. Requires +loadGenerationEnabled+ on the server for
    # the start step.
    #
    # @param scenario [LoadScenario, Hash] the scenario to register and run
    # @return [Hash] parsed response from the start call
    def run_load_scenario(scenario)
      payload = scenario.respond_to?(:to_h) ? scenario.to_h : scenario
      name = payload.respond_to?(:[]) ? (payload['name'] || payload[:name]) : nil
      if name.nil? || name.to_s.empty?
        raise ArgumentError, 'scenario must carry a non-empty name to run'
      end

      load_scenario(payload)
      start_load_scenarios(name)
    end

    # Fetch the end-of-run summary report for a load scenario run.
    #
    # Returns the report derived from the run's status snapshot (live while
    # running, or the retained terminal snapshot once finished). With no +format+
    # (or any value other than +"junit"+) the JSON report is parsed and returned
    # as a Hash carrying counts, latency percentiles, the threshold verdict and
    # per-threshold results. With +format: "junit"+ the raw JUnit-XML
    # +<testsuite>+ document is returned as a String so a load run becomes a
    # first-class CI test artifact.
    #
    # @param name [String] the unique scenario name
    # @param format [String, nil] +"junit"+ for the JUnit-XML report, otherwise JSON
    # @return [Hash, String] parsed JSON report (default) or the raw JUnit-XML String
    # @raise [Error] if the scenario never ran (404) or another failure occurs
    def get_load_scenario_report(name, format = nil)
      query_params = {}
      query_params['format'] = format if format
      status, response_body = do_request(
        'GET', "/mockserver/loadScenario/#{encode_path_segment(name)}/report", nil,
        query_params.empty? ? nil : query_params
      )
      if status == 404
        raise Error, "Load scenario report not found (status=404): #{name}"
      end
      if status >= 400
        raise Error, "Failed to get load scenario report (status=#{status}): #{response_body}"
      end

      return response_body if format == 'junit'

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Generate (and register) a load scenario from an OpenAPI specification.
    #
    # Produces an editable scenario - one step per OpenAPI operation - and loads
    # it into the registry under +name+ in the LOADED state; it generates no
    # traffic and is allowed even when load generation is disabled.
    #
    # @param name [String] the generated scenario name (the unique registry key)
    # @param spec_url_or_payload [String] OpenAPI spec as inline JSON/YAML, a URL,
    #   or a file/classpath reference
    # @param target [Hash, nil] explicit network target for every generated step
    #   (e.g. { "host" => ..., "port" => ..., "scheme" => "http" })
    # @param profile [LoadProfile, Hash, nil] optional traffic profile (a
    #   conservative default is applied when omitted)
    # @return [Hash] parsed response of the form
    #   { "status" => "loaded", "name" => ..., "state" => ..., "scenario" => {...} }
    def generate_load_scenario_from_openapi(name, spec_url_or_payload, target: nil, profile: nil)
      payload = { 'name' => name, 'specUrlOrPayload' => spec_url_or_payload }
      payload['target'] = target if target
      payload['profile'] = profile.respond_to?(:to_h) ? profile.to_h : profile if profile
      body = JSON.generate(payload)
      status, response_body = request('PUT', '/mockserver/loadScenario/generateFromOpenAPI', body)
      if status >= 400
        raise Error, "Failed to generate load scenario from OpenAPI (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Generate (and register) a load scenario from recorded proxy traffic.
    #
    # Converts requests previously recorded by MockServer in proxy/recording mode
    # into an editable scenario and loads it into the registry under +name+ in the
    # LOADED state; it generates no traffic and is allowed even when load
    # generation is disabled.
    #
    # @param name [String] the generated scenario name (the unique registry key)
    # @param mode [String, nil] +VERBATIM+ (default) or +TEMPLATIZED+
    # @param request_filter [HttpRequest, Hash, nil] optional matcher selecting
    #   which recorded requests to include (absent means all)
    # @param target [Hash, nil] explicit network target applied to every step
    # @param max_steps [Integer, nil] optional cap on the number of VERBATIM steps
    # @return [Hash] parsed response of the form
    #   { "status" => "loaded", "name" => ..., "state" => ..., "scenario" => {...} }
    def generate_load_scenario_from_recording(name, mode: nil, request_filter: nil,
                                              target: nil, max_steps: nil)
      payload = { 'name' => name }
      payload['mode'] = mode if mode
      payload['requestFilter'] = request_filter.respond_to?(:to_h) ? request_filter.to_h : request_filter if request_filter
      payload['target'] = target if target
      payload['maxSteps'] = max_steps if max_steps
      body = JSON.generate(payload)
      status, response_body = request('PUT', '/mockserver/loadScenario/generateFromRecording', body)
      if status >= 400
        raise Error, "Failed to generate load scenario from recording (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # -------------------------------------------------------------------
    # SRE control plane: SLO verification + chaos experiments
    # -------------------------------------------------------------------

    # Evaluate a set of service-level objectives (SLOs) over a window
    # (PUT /mockserver/verifySLO).
    #
    # The server encodes the verdict in the HTTP status: 200 for PASS or
    # INCONCLUSIVE, 406 for FAIL, 403 when SLO tracking is disabled
    # (+sloTrackingEnabled=false+) and 400 for malformed criteria. The decoded verdict Hash carries
    # the overall +result+ and the per-objective +objectiveResults+ so callers can
    # inspect why an SLO failed.
    #
    # +criteria+ may be any Hash already shaped to the +SloCriteria+ JSON contract
    # (+name+, +window+, +minimumSampleCount+, +upstreamHosts+,
    # +objectives+[{+sli+, +comparator+, +threshold+, +scope+}]) or an object that
    # responds to +to_h+.
    #
    # @param criteria [Hash, #to_h] the SLO criteria
    # @return [Hash] the SLO verdict (result PASS or INCONCLUSIVE)
    # @raise [VerificationError] if the verdict is FAIL (HTTP 406)
    # @raise [Error] if SLO tracking is disabled (HTTP 403) or criteria are malformed (HTTP 400),
    #   or on any other failure
    def verify_slo(criteria)
      payload = criteria.respond_to?(:to_h) ? criteria.to_h : criteria
      body = JSON.generate(payload)
      status, response_body = request('PUT', '/mockserver/verifySLO', body)
      if status == 406
        raise VerificationError, (response_body && !response_body.empty? ? response_body : 'SLO verdict: FAIL')
      end
      if status == 403
        raise Error, 'SLO tracking is disabled — set sloTrackingEnabled=true ' \
                     "on the server: #{response_body}"
      end
      if status == 400
        raise Error, "Invalid SLO criteria: #{response_body}"
      end
      if status >= 400
        raise Error, "Failed to verify SLO (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Start a scheduled multi-stage chaos experiment
    # (PUT /mockserver/chaosExperiment).
    #
    # The experiment is an ordered sequence of stages, each applying
    # service-scoped chaos profiles to one or more hosts for a duration; stages
    # progress automatically. Only one experiment may be active at a time;
    # starting a new one stops the previous one.
    #
    # +experiment+ may be any Hash already shaped to the +ChaosExperiment+ JSON
    # contract (+name+, +loop+, +stages+[{+durationMillis+, +profiles+{host:
    # profile}}]) or an object that responds to +to_h+.
    #
    # @param experiment [Hash, #to_h] the experiment definition
    # @return [Hash] the started status, e.g. { "status" => "started", "name" => ... }
    # @raise [Error] if the experiment definition is invalid (HTTP 400), if
    #   control-plane authorization rejects the request (HTTP 403), or on any other
    #   failure; there is no server setting that disables chaos experiments
    def start_chaos_experiment(experiment)
      payload = experiment.respond_to?(:to_h) ? experiment.to_h : experiment
      body = JSON.generate(payload)
      status, response_body = request('PUT', '/mockserver/chaosExperiment', body)
      if status == 403
        raise Error, "Chaos experiment rejected (status=403): control-plane authorization denied: #{response_body}"
      end
      if status >= 400
        raise Error, "Failed to start chaos experiment (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # -------------------------------------------------------------------
    # Diagnostics (explain / debug_mismatch / explain_unmatched)
    # -------------------------------------------------------------------

    # Explain how a recorded request would be served now
    # (PUT /mockserver/explain?correlationId=<id>): re-checks it against the
    # current expectations, in the order MockServer evaluates them, and returns
    # the expectation that would serve it, why each expectation ahead of that
    # one was passed over, and which lower-priority expectations it shadows.
    # Read-only: it consumes no Times and moves no scenario.
    #
    # @param correlation_id [String] the correlation id of the request's log entries
    # @return [RequestExplanation]
    # @raise [ArgumentError] if +correlation_id+ is nil or blank
    # @raise [Error] with the server's reason when no request is recorded with
    #   this id (never received, cleared or evicted) or it is not an HTTP request
    def explain(correlation_id)
      if correlation_id.nil? || correlation_id.to_s.strip.empty?
        raise ArgumentError, 'explain requires a non blank correlation_id'
      end

      RequestExplanation.from_hash(
        diagnostic_request('/mockserver/explain', nil, { 'correlationId' => correlation_id.to_s },
                           "explain failed for correlationId \"#{correlation_id}\"")
      )
    end

    # Compare a request with every active expectation
    # (PUT /mockserver/debugMismatch): for each, whether it matches and the
    # per-field differences, plus the closest match. Only an HTTP request is
    # supported.
    #
    # @param request [HttpRequest, Hash] the request to check
    # @return [DebugMismatchResult]
    # @raise [Error] with the server's reason for a non-HTTP request definition
    def debug_mismatch(request)
      payload = request.respond_to?(:to_h) ? request.to_h : request
      DebugMismatchResult.from_hash(
        diagnostic_request('/mockserver/debugMismatch', JSON.generate(payload), nil, 'debugMismatch failed')
      )
    end

    # Explain why recent requests matched no expectation
    # (PUT /mockserver/explainUnmatched): each recent unmatched request with its
    # closest expectations, closest first.
    #
    # @param limit [Integer, nil] how many recent unmatched requests to analyse (server default 10)
    # @param suggest_fixes [Boolean, nil] add a suggested JSON patch to each of
    #   the five closest non-matching expectations (nothing is applied)
    # @return [ExplainUnmatchedResult]
    def explain_unmatched(limit: nil, suggest_fixes: nil)
      options = {}
      options['limit'] = limit unless limit.nil?
      options['suggestFixes'] = suggest_fixes unless suggest_fixes.nil?
      ExplainUnmatchedResult.from_hash(
        diagnostic_request('/mockserver/explainUnmatched', options.empty? ? nil : JSON.generate(options), nil,
                           'explainUnmatched failed')
      )
    end

    # @api private
    # PUT a diagnostic request and return the parsed 200 body; any other status,
    # or a body that is not a JSON object, raises {Error} with the server's
    # +error+ or the body.
    def diagnostic_request(path, body, query_params, failure)
      status, response_body = do_request('PUT', path, body, query_params)
      parsed = begin
        response_body.nil? || response_body.empty? ? {} : JSON.parse(response_body)
      rescue JSON::ParserError
        nil
      end
      if status != 200 || !parsed.is_a?(Hash)
        reason = parsed.is_a?(Hash) ? parsed['error'] : nil
        raise Error, "#{failure} (status=#{status}): #{reason || response_body}"
      end

      parsed
    end

    # -------------------------------------------------------------------
    # Stateful scenarios (state machine control plane)
    # -------------------------------------------------------------------

    # Return a handle to the named stateful scenario, wrapping the
    # +/mockserver/scenario/{name}+ control-plane endpoints.
    #
    # @param name [String] the scenario (state-machine) name
    # @return [ScenarioHandle]
    def scenario(name)
      ScenarioHandle.new(self, name)
    end

    # List every known scenario and its current state.
    #
    # @return [Array<ScenarioState>] each with +scenario_name+ and +current_state+
    def scenarios
      result = scenario_request('GET', '/mockserver/scenario')
      list = result.is_a?(Hash) ? (result['scenarios'] || []) : []
      list.map { |s| ScenarioState.from_hash(s) }
    end

    # @api private
    # Issue a control-plane scenario request, parsing the JSON response and
    # raising {Error} on any >= 400 status. Reuses the same transport
    # (+request+) as the other +/mockserver/...+ control endpoints.
    def scenario_request(method, path, body = nil)
      status, response_body = request(method, path, body)
      if status >= 400
        raise Error, "Scenario request failed (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # -------------------------------------------------------------------
    # gRPC descriptor management
    # -------------------------------------------------------------------

    # Upload a compiled protobuf descriptor set so gRPC requests can be matched.
    #
    # +descriptor_bytes+ must be the raw bytes of a +FileDescriptorSet+ (e.g. the
    # output of +protoc --descriptor_set_out=... --include_imports+). The bytes are
    # sent verbatim as +application/octet-stream+ (NOT base64-encoded).
    #
    # @param descriptor_bytes [String] raw descriptor set bytes (binary string)
    # @return [nil]
    def upload_grpc_descriptor(descriptor_bytes)
      if descriptor_bytes.nil? || descriptor_bytes.empty?
        raise ArgumentError, 'descriptor bytes must not be empty'
      end

      status, response_body = request(
        'PUT', '/mockserver/grpc/descriptors', descriptor_bytes,
        content_type: 'application/octet-stream'
      )
      if status >= 400
        raise Error, "Failed to upload gRPC descriptor (status=#{status}): #{response_body}"
      end

      nil
    end

    # Retrieve the gRPC services registered from uploaded descriptor sets.
    #
    # Returns an array of service hashes, each with a +"name"+ and a list of
    # +"methods"+ (+"name"+, +"inputType"+, +"outputType"+, +"clientStreaming"+,
    # +"serverStreaming"+).
    #
    # @return [Array<Hash>]
    def retrieve_grpc_services
      status, response_body = request('PUT', '/mockserver/grpc/services')
      if status >= 400
        raise Error, "Failed to retrieve gRPC services (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed if parsed.is_a?(Array)
      end
      []
    end

    # Clear all uploaded gRPC descriptor sets and registered services.
    # @return [nil]
    def clear_grpc_descriptors
      status, response_body = request('PUT', '/mockserver/grpc/clear')
      if status >= 400
        raise Error, "Failed to clear gRPC descriptors (status=#{status}): #{response_body}"
      end

      nil
    end

    # Verify that a request (and optionally a response) was received.
    # @param request [HttpRequest, nil]
    # @param times [VerificationTimes, nil]
    # @param response [HttpResponse, nil]
    # @return [nil]
    # @raise [VerificationError] if verification fails (HTTP 406)
    def verify(request = nil, times: nil, response: nil)
      verification = Verification.new(http_request: request, http_response: response, times: times)
      body = JSON.generate(verification.to_h)
      status, response_body = do_request('PUT', '/mockserver/verify', body)
      if status == 406
        raise VerificationError, response_body
      end

      if status >= 400
        raise Error, "Failed to verify (status=#{status}): #{response_body}"
      end

      nil
    end

    # Verify that requests were received in sequence.
    # @param requests [Array<HttpRequest>]
    # @param responses [Array<HttpResponse>, nil] index-aligned response matchers
    # @return [nil]
    # @raise [VerificationError] if verification fails (HTTP 406)
    def verify_sequence(*requests, responses: nil)
      verification = VerificationSequence.new(
        http_requests: requests.empty? ? nil : requests.to_a,
        http_responses: responses
      )
      body = JSON.generate(verification.to_h)
      status, response_body = request('PUT', '/mockserver/verifySequence', body)
      if status == 406
        raise VerificationError, response_body
      end

      if status >= 400
        raise Error, "Failed to verify sequence (status=#{status}): #{response_body}"
      end

      nil
    end

    # Verify zero interactions.
    # @return [nil]
    def verify_zero_interactions
      verify(HttpRequest.new, times: VerificationTimes.new(at_most: 0))
    end

    # Retrieve recorded requests.
    # @param request [HttpRequest, nil]
    # @return [Array<HttpRequest>]
    def retrieve_recorded_requests(request: nil)
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'REQUESTS', 'format' => 'JSON' }
      )
      if status >= 400
        raise Error, "Failed to retrieve recorded requests (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |r| HttpRequest.from_hash(r) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve active expectations.
    # @param request [HttpRequest, nil]
    # @param filter [ExpectationFilter, nil] metadata / unmatched selection and paging
    # @return [Array<Expectation>]
    def retrieve_active_expectations(request: nil, filter: nil)
      unless filter.nil? || filter.is_a?(ExpectationFilter)
        raise ArgumentError, "filter must be a MockServer::ExpectationFilter, got #{filter.class}"
      end

      body = request ? JSON.generate(request.to_h) : ''
      query_params = { 'type' => 'ACTIVE_EXPECTATIONS', 'format' => 'JSON' }
      query_params.merge!(filter.to_query_params) if filter
      status, response_body = do_request('PUT', '/mockserver/retrieve', body, query_params)
      if status >= 400
        raise Error, "Failed to retrieve active expectations (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |e| Expectation.from_hash(e) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve active expectations whose metadata has +key+ with a value
    # equal to +value+ (see {ExpectationFilter#with_metadata}).
    # @return [Array<Expectation>]
    def retrieve_active_expectations_by_metadata(key, value)
      retrieve_active_expectations(filter: ExpectationFilter.new.with_metadata(key, value))
    end

    # Retrieve active expectations whose metadata has +key+, with any value.
    # @return [Array<Expectation>]
    def retrieve_active_expectations_by_metadata_key(key)
      retrieve_active_expectations(filter: ExpectationFilter.new.with_metadata_key(key))
    end

    # Retrieve the active expectations that have not matched any request.
    # @return [Array<Expectation>]
    def retrieve_unmatched_expectations
      retrieve_active_expectations(filter: ExpectationFilter.new.with_unmatched(true))
    end

    # Retrieve recorded expectations, optionally one page of them in the
    # order the requests were received.
    # @param request [HttpRequest, nil]
    # @param offset [Integer, nil] number to skip, zero or more
    # @param limit [Integer, nil] maximum number to return, zero or more
    # @return [Array<Expectation>]
    def retrieve_recorded_expectations(request: nil, offset: nil, limit: nil)
      paging = ExpectationFilter.new
      paging.with_offset(offset) unless offset.nil?
      paging.with_limit(limit) unless limit.nil?
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'RECORDED_EXPECTATIONS', 'format' => 'JSON' }.merge(paging.to_query_params)
      )
      if status >= 400
        raise Error, "Failed to retrieve recorded expectations (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |e| Expectation.from_hash(e) } if parsed.is_a?(Array)
      end
      []
    end

    # Send a retrieve request filtered by expectation id.
    #
    # An expectation id may be sent in place of a request matcher. For
    # "ACTIVE_EXPECTATIONS" the server returns only the expectation with that id;
    # for every other type it returns the entries matching that expectation's
    # request, exactly as verify by expectation id matches them. An unknown id is
    # rejected with a 400.
    #
    # @param expectation_id [String]
    # @param query_params [Hash]
    # @return [Array(Integer, String)] the status and response body
    # @api private
    def retrieve_by_id(expectation_id, query_params)
      do_request(
        'PUT', '/mockserver/retrieve', JSON.generate({ 'id' => expectation_id }), query_params
      )
    end
    private :retrieve_by_id

    # Retrieve the recorded requests matching the request of the expectation with the given id.
    # @param expectation_id [String]
    # @return [Array<HttpRequest>]
    def retrieve_recorded_requests_by_id(expectation_id)
      status, response_body = retrieve_by_id(
        expectation_id, { 'type' => 'REQUESTS', 'format' => 'JSON' }
      )
      if status >= 400
        raise Error, "Failed to retrieve recorded requests by id (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |r| HttpRequest.from_hash(r) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve the active expectation with the given id.
    # @param expectation_id [String]
    # @return [Array<Expectation>]
    def retrieve_active_expectations_by_id(expectation_id)
      status, response_body = retrieve_by_id(
        expectation_id, { 'type' => 'ACTIVE_EXPECTATIONS', 'format' => 'JSON' }
      )
      if status >= 400
        raise Error, "Failed to retrieve active expectations by id (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |e| Expectation.from_hash(e) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve the recorded expectations matching the request of the expectation with the given id.
    # @param expectation_id [String]
    # @return [Array<Expectation>]
    def retrieve_recorded_expectations_by_id(expectation_id)
      status, response_body = retrieve_by_id(
        expectation_id, { 'type' => 'RECORDED_EXPECTATIONS', 'format' => 'JSON' }
      )
      if status >= 400
        raise Error, "Failed to retrieve recorded expectations by id (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |e| Expectation.from_hash(e) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve the recorded requests and responses matching the request of the expectation with the given id.
    # @param expectation_id [String]
    # @return [Array<HttpRequestAndHttpResponse>]
    def retrieve_recorded_requests_and_responses_by_id(expectation_id)
      status, response_body = retrieve_by_id(
        expectation_id, { 'type' => 'REQUEST_RESPONSES', 'format' => 'JSON' }
      )
      if status >= 400
        raise Error, "Failed to retrieve request/responses by id (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |rr| HttpRequestAndHttpResponse.from_hash(rr) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve the log messages matching the request of the expectation with the given id.
    # @param expectation_id [String]
    # @return [Array<String>]
    def retrieve_log_messages_by_id(expectation_id)
      status, response_body = retrieve_by_id(expectation_id, { 'type' => 'LOGS' })
      if status >= 400
        raise Error, "Failed to retrieve log messages by id (status=#{status}): #{response_body}"
      end

      return [] unless response_body && !response_body.empty?

      begin
        parsed = JSON.parse(response_body)
        parsed.is_a?(Array) ? parsed : []
      rescue JSON::ParserError
        response_body.split("------------------------------------\n")
      end
    end

    # Retrieve the active expectations as MockServer SDK setup code (the builder
    # code that recreates the expectations) in the requested language.
    # @param format [String] one of "java", "javascript", "python", "go",
    #   "csharp", "ruby", "rust" or "php" (case-insensitive)
    # @param request [HttpRequest, nil]
    # @return [String] the generated code
    def retrieve_expectations_as_code(format: 'java', request: nil)
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'ACTIVE_EXPECTATIONS', 'format' => format.to_s.upcase }
      )
      if status >= 400
        raise Error, "Failed to retrieve expectations as code (status=#{status}): #{response_body}"
      end

      response_body || ''
    end

    # Retrieve the recorded (proxied) request/response pairs as MockServer SDK
    # setup code in the requested language.
    # @param format [String] one of "java", "javascript", "python", "go",
    #   "csharp", "ruby", "rust" or "php" (case-insensitive)
    # @param request [HttpRequest, nil]
    # @return [String] the generated code
    def retrieve_recorded_expectations_as_code(format: 'java', request: nil)
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'RECORDED_EXPECTATIONS', 'format' => format.to_s.upcase }
      )
      if status >= 400
        raise Error, "Failed to retrieve recorded expectations as code (status=#{status}): #{response_body}"
      end

      response_body || ''
    end

    # Retrieve recorded requests and responses.
    # @param request [HttpRequest, nil]
    # @return [Array<HttpRequestAndHttpResponse>]
    def retrieve_recorded_requests_and_responses(request: nil)
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'REQUEST_RESPONSES', 'format' => 'JSON' }
      )
      if status >= 400
        raise Error, "Failed to retrieve request/responses (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |rr| HttpRequestAndHttpResponse.from_hash(rr) } if parsed.is_a?(Array)
      end
      []
    end

    # Retrieve log messages.
    # @param request [HttpRequest, nil]
    # @return [Array<String>]
    def retrieve_log_messages(request: nil)
      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/retrieve', body,
        { 'type' => 'LOGS' }
      )
      if status >= 400
        raise Error, "Failed to retrieve log messages (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        begin
          parsed = JSON.parse(response_body)
          return parsed if parsed.is_a?(Array)
        rescue JSON::ParserError
          return response_body.split("------------------------------------\n")
        end
      end
      []
    end

    # Bind additional ports.
    # @param ports [Array<Integer>]
    # @return [Array<Integer>]
    def bind(*ports)
      body = JSON.generate(Ports.new(ports: ports.flatten).to_h)
      status, response_body = request('PUT', '/mockserver/bind', body)
      if status >= 400
        raise Error, "Failed to bind ports (status=#{status}): #{response_body}"
      end

      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return Ports.from_hash(parsed).ports
      end
      []
    end

    # Stop the MockServer instance.
    # @return [nil]
    def stop
      request('PUT', '/mockserver/stop')
      nil
    rescue ConnectionError
      nil
    ensure
      close
    end

    # Check if MockServer has started.
    # @param attempts [Integer]
    # @param timeout [Float] seconds between attempts
    # @return [Boolean]
    def has_started?(attempts: 10, timeout: 0.5)
      attempts.times do |i|
        begin
          status, = request('PUT', '/mockserver/status')
          return true if status == 200
        rescue ConnectionError
          # not yet started
        end
        sleep(timeout) if i < attempts - 1
      end
      false
    end

    alias has_started has_started?

    # -------------------------------------------------------------------
    # Fluent API
    # -------------------------------------------------------------------

    # Begin building an expectation via the fluent API.
    # @param request [HttpRequest]
    # @param times [Times, nil]
    # @param time_to_live [TimeToLive, nil]
    # @param priority [Integer, nil]
    # @return [ForwardChainExpectation]
    def when(request, times: nil, time_to_live: nil, priority: nil)
      expectation = Expectation.new(
        http_request: request,
        times: times,
        time_to_live: time_to_live,
        priority: priority
      )
      ForwardChainExpectation.new(self, expectation)
    end

    # -------------------------------------------------------------------
    # Breakpoint matcher management
    # -------------------------------------------------------------------

    # Register a breakpoint matcher with callback handlers.
    # The callback WebSocket is opened lazily and reused.
    #
    # @param matcher [HttpRequest] the request definition to match
    # @param phases [Array<String>] e.g. ["REQUEST", "RESPONSE"]
    # @param request_handler [Proc, nil] handler for REQUEST phase
    # @param response_handler [Proc, nil] handler for RESPONSE phase
    # @param stream_frame_handler [Proc, nil] handler for streaming phases
    # @return [String] the server-assigned breakpoint matcher id
    def add_breakpoint(matcher, phases,
                       request_handler: nil, response_handler: nil,
                       stream_frame_handler: nil)
      raise ArgumentError, 'add_breakpoint requires a non-nil matcher' if matcher.nil?
      raise ArgumentError, 'add_breakpoint requires a non-empty phases array' if phases.nil? || phases.empty?

      ws_client = ensure_breakpoint_websocket
      client_id = ws_client.client_id

      body = JSON.generate({
        'httpRequest' => matcher.to_h,
        'phases' => phases,
        'clientId' => client_id
      })
      status, response_body = request('PUT', '/mockserver/breakpoint/matcher', body)
      if status >= 400
        raise Error, "Failed to register breakpoint matcher (status=#{status}): #{response_body}"
      end

      parsed = response_body && !response_body.empty? ? JSON.parse(response_body) : {}
      breakpoint_id = parsed['id']
      raise Error, 'Server did not return a breakpoint id' unless breakpoint_id

      # Install per-breakpoint-id handlers
      ws_client.set_breakpoint_request_handler(breakpoint_id, request_handler) if request_handler
      ws_client.set_breakpoint_response_handler(breakpoint_id, response_handler) if response_handler
      ws_client.set_breakpoint_stream_frame_handler(breakpoint_id, stream_frame_handler) if stream_frame_handler

      breakpoint_id
    end

    # Convenience: register a REQUEST-only breakpoint.
    # @param matcher [HttpRequest]
    # @param request_handler [Proc]
    # @return [String]
    def add_request_breakpoint(matcher, request_handler)
      add_breakpoint(matcher, ['REQUEST'], request_handler: request_handler)
    end

    # Convenience: register a REQUEST+RESPONSE breakpoint.
    # @param matcher [HttpRequest]
    # @param request_handler [Proc]
    # @param response_handler [Proc]
    # @return [String]
    def add_request_and_response_breakpoint(matcher, request_handler, response_handler)
      add_breakpoint(matcher, %w[REQUEST RESPONSE],
                     request_handler: request_handler,
                     response_handler: response_handler)
    end

    # List all registered breakpoint matchers.
    # @return [Hash] e.g. {"matchers" => [{...}, ...]}
    def list_breakpoint_matchers
      status, response_body = request('GET', '/mockserver/breakpoint/matchers')
      if status >= 400
        raise Error, "Failed to list breakpoint matchers (status=#{status}): #{response_body}"
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Remove a breakpoint matcher by id.
    # @param breakpoint_id [String]
    # @return [Hash]
    def remove_breakpoint_matcher(breakpoint_id)
      raise ArgumentError, 'remove_breakpoint_matcher requires a non-empty id' if breakpoint_id.nil? || breakpoint_id.empty?

      body = JSON.generate({ 'id' => breakpoint_id })
      status, response_body = request('PUT', '/mockserver/breakpoint/matcher/remove', body)
      if status >= 400
        raise Error, "Failed to remove breakpoint matcher (status=#{status}): #{response_body}"
      end

      # Remove client-side handlers
      @websocket_mutex.synchronize do
        @websocket_clients.each do |ws|
          ws.remove_breakpoint_handlers(breakpoint_id) if ws.respond_to?(:remove_breakpoint_handlers)
        end
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # Clear all registered breakpoint matchers.
    # @return [Hash]
    def clear_breakpoint_matchers
      status, response_body = request('PUT', '/mockserver/breakpoint/matcher/clear')
      if status >= 400
        raise Error, "Failed to clear breakpoint matchers (status=#{status}): #{response_body}"
      end

      # Clear client-side handlers
      @websocket_mutex.synchronize do
        @websocket_clients.each do |ws|
          ws.clear_breakpoint_handlers if ws.respond_to?(:clear_breakpoint_handlers)
        end
      end

      response_body && !response_body.empty? ? JSON.parse(response_body) : {}
    end

    # -------------------------------------------------------------------
    # Callback methods
    # -------------------------------------------------------------------

    # Register a response callback via WebSocket.
    # @param request [HttpRequest]
    # @param callback [Proc]
    # @param times [Times, nil]
    # @param time_to_live [TimeToLive, nil]
    # @return [Array<Expectation>]
    def mock_with_callback(request, callback, times: nil, time_to_live: nil)
      client_id = register_websocket_callback('response', callback)
      expectation = Expectation.new(
        http_request: request,
        http_response_object_callback: HttpObjectCallback.new(client_id: client_id),
        times: times,
        time_to_live: time_to_live
      )
      upsert(expectation)
    end

    # Register a forward callback via WebSocket.
    # @param request [HttpRequest]
    # @param forward_callback [Proc]
    # @param response_callback [Proc, nil]
    # @param times [Times, nil]
    # @param time_to_live [TimeToLive, nil]
    # @return [Array<Expectation>]
    def mock_with_forward_callback(request, forward_callback, response_callback = nil,
                                    times: nil, time_to_live: nil)
      client_id = register_websocket_callback('forward', forward_callback, response_callback)
      obj_callback = HttpObjectCallback.new(client_id: client_id)
      obj_callback.response_callback = true if response_callback
      expectation = Expectation.new(
        http_request: request,
        http_forward_object_callback: obj_callback,
        times: times,
        time_to_live: time_to_live
      )
      upsert(expectation)
    end

    # Close all WebSocket connections.
    # @return [nil]
    def close
      @websocket_mutex.synchronize do
        @websocket_clients.each(&:close)
        @websocket_clients.clear
      end
      nil
    end

    private

    # @api private
    # Ensure a callback WS is connected for breakpoint use, returning it.
    def ensure_breakpoint_websocket
      # Hold the mutex for the whole check-create-append so two concurrent
      # add_breakpoint calls cannot both create a breakpoint WS (TOCTOU).
      # Breakpoint WS creation is rare, so blocking on connect under the lock
      # is acceptable.
      @websocket_mutex.synchronize do
        existing = @websocket_clients.find { |ws| ws.instance_variable_get(:@is_breakpoint_ws) }
        return existing if existing

        ws_client = WebSocketClient.new
        ws_client.connect(
          @host, @port,
          context_path: @context_path,
          secure: @secure,
          ca_cert_path: @ca_cert_path,
          tls_verify: @tls_verify,
          authorization: @control_plane_authorization
        )
        ws_client.instance_variable_set(:@is_breakpoint_ws, true)
        ws_client.listen
        @websocket_clients << ws_client
        ws_client
      end
    end

    # @api private
    def register_websocket_callback(callback_type, callback_fn, forward_response_fn = nil)
      ws_client = WebSocketClient.new
      ws_client.connect(
        @host, @port,
        context_path: @context_path,
        secure: @secure,
        ca_cert_path: @ca_cert_path,
        tls_verify: @tls_verify,
        authorization: @control_plane_authorization
      )

      case callback_type
      when 'response'
        ws_client.register_response_callback(callback_fn)
      when 'forward'
        ws_client.register_forward_callback(callback_fn, forward_response_fn)
      end

      ws_client.listen
      @websocket_mutex.synchronize { @websocket_clients << ws_client }
      ws_client.client_id
    end

    # @api private
    def clear_with_filter(request, type, filter)
      unless filter.is_a?(ExpectationFilter) && filter.selection?
        raise ArgumentError, 'clear with a filter requires a metadata or unmatched selection; ' \
                             'use clear(request) to clear without one'
      end
      raise ArgumentError, 'offset and limit are not supported for clear' if filter.paging?
      unless type.nil? || type.to_s.casecmp?('expectations')
        raise ArgumentError, "clear with a filter only clears expectations, not type #{type.inspect}"
      end

      body = request ? JSON.generate(request.to_h) : ''
      status, response_body = do_request(
        'PUT', '/mockserver/clear', body, { 'type' => 'expectations' }.merge(filter.to_query_params)
      )
      raise Error, "Failed to clear (status=#{status}): #{response_body}" if status >= 400

      nil
    end

    # Perform an HTTP request with optional query parameters.
    # @api private
    def do_request(method, path, body = nil, query_params = nil,
                   content_type: 'application/json; charset=utf-8')
      url = "#{@base_url}#{path}"
      if query_params && !query_params.empty?
        url = "#{url}?#{URI.encode_www_form(query_params)}"
      end

      uri = URI.parse(url)
      http = build_http(uri)

      req = build_request(method, uri, body, content_type)
      execute_request(http, req)
    end

    # Perform an HTTP request (no query params).
    # @api private
    def request(method, path, body = nil,
                content_type: 'application/json; charset=utf-8')
      do_request(method, path, body, nil, content_type: content_type)
    end

    # @api private
    # Percent-encode a single URL path segment (e.g. a scenario name) so that
    # spaces, slashes, and other reserved characters are transmitted safely.
    def encode_path_segment(value)
      raise ArgumentError, 'name must not be nil or empty' if value.nil? || value.to_s.empty?

      URI.encode_www_form_component(value.to_s).gsub('+', '%20')
    end

    # @api private
    # Import a document (HAR/Postman/Pact) via PUT /mockserver/import?format=<format>
    # and return the upserted expectations.
    def import_document(json, format)
      if json.nil? || json.to_s.strip.empty?
        raise ArgumentError, "import #{format} document must not be empty"
      end

      status, response_body = do_request(
        'PUT', '/mockserver/import', json.to_s, { 'format' => format }
      )
      if status >= 400
        raise Error, "Failed to import #{format} (status=#{status}): #{response_body}"
      end

      expectations_from_response(response_body)
    end

    # @api private
    # Parse a JSON array of expectations from a control-plane response body.
    def expectations_from_response(response_body)
      if response_body && !response_body.empty?
        parsed = JSON.parse(response_body)
        return parsed.map { |e| Expectation.from_hash(e) } if parsed.is_a?(Array)
      end
      []
    end

    # @api private
    def build_http(uri)
      http = Net::HTTP.new(uri.host, uri.port)
      http.read_timeout = HTTP_TIMEOUT
      http.open_timeout = HTTP_TIMEOUT

      if @secure
        http.use_ssl = true
        if @ca_cert_path
          http.ca_file = @ca_cert_path
          http.verify_mode = OpenSSL::SSL::VERIFY_PEER
        elsif !@tls_verify
          http.verify_mode = OpenSSL::SSL::VERIFY_NONE
        else
          http.verify_mode = OpenSSL::SSL::VERIFY_PEER
        end
      end

      http
    end

    # @api private
    def build_request(method, uri, body, content_type = 'application/json; charset=utf-8')
      request_path = uri.request_uri
      case method.upcase
      when 'PUT'
        req = Net::HTTP::Put.new(request_path)
      when 'GET'
        req = Net::HTTP::Get.new(request_path)
      when 'POST'
        req = Net::HTTP::Post.new(request_path)
      when 'DELETE'
        req = Net::HTTP::Delete.new(request_path)
      else
        req = Net::HTTP::Put.new(request_path)
      end
      req['Content-Type'] = content_type
      req['Authorization'] = @control_plane_authorization if @control_plane_authorization
      req.body = body if body
      req
    end

    # @api private
    def basic_authorization(credentials)
      return nil if credentials.nil?

      username, password = credentials
      if username.nil? || username.to_s.empty? || username.to_s.include?(':') || password.nil?
        raise ArgumentError, 'control_plane_basic_auth needs [username, password] with a username without ":"'
      end

      "Basic #{Base64.strict_encode64("#{username}:#{password}".encode('UTF-8'))}"
    end

    # @api private
    def execute_request(http, req)
      # Use the block form of #start so the underlying TCP/TLS connection is
      # always closed (#finish) when the request completes, rather than being
      # left open until garbage collection. All connection options (use_ssl,
      # ca_file, verify_mode, read_timeout, open_timeout) configured on +http+
      # by #build_http are preserved because #start operates on this instance.
      response = http.start { |conn| conn.request(req) }
      [response.code.to_i, response.body || '']
    rescue Net::OpenTimeout, Net::ReadTimeout => e
      raise ConnectionError, "Request to MockServer at #{@base_url} timed out: #{e.message}"
    rescue OpenSSL::SSL::SSLError => e
      raise ConnectionError, "TLS error connecting to MockServer at #{@base_url}: #{e.message}"
    rescue Errno::ECONNREFUSED, Errno::ECONNRESET, SocketError, IOError => e
      raise ConnectionError, "Failed to connect to MockServer at #{@base_url}: #{e.message}"
    end
  end
end
