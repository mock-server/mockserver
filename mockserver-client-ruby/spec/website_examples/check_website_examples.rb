# frozen_string_literal: true

# Checks that every Ruby example on the website sends what the REST API tab of
# the same accordion sends. Each Ruby block runs through the real client in a
# forked child with all HTTP intercepted (WebMock, no network); the JSON the
# client sends to /mockserver/* is compared, normalised, with the curl bodies of
# the REST API tab. Differences not listed in allowlist.yml fail the check, and
# so does an allowlist entry that now matches. With --lang, the Python, Go,
# .NET, Rust or PHP blocks run instead (see languages.rb), each against
# allowlist-<lang>.yml.
#
#   ruby spec/website_examples/check_website_examples.rb [--lang L] [--verbose] [--only KEY]

require 'cgi'
require 'digest'
require 'json'
require 'yaml'
require 'shellwords'
require 'tmpdir'
require 'uri'

module WebsiteExamples
  CLIENT_ROOT = File.expand_path('../..', __dir__)
  SITE_ROOT = File.expand_path('../jekyll-www.mock-server.com', CLIENT_ROOT)
  ALLOWLIST = File.join(__dir__, 'allowlist.yml')
  BLOCK_TIMEOUT_SECONDS = 10

  Block = Struct.new(:file, :line, :lang, :code, keyword_init: true)
  Group = Struct.new(:file, :line, :key, :title, :tabs, keyword_init: true)

  # ---------------------------------------------------------------------
  # Extraction: accordions are <button class="accordion ..."> followed by a
  # <div class="panel">; a group's tabs are its "accordion inner" buttons.
  # ---------------------------------------------------------------------
  module Extractor
    TOKEN = %r{<button\b([^>]*)>(.*?)</button>|<pre\b([^>]*)>\s*(?:<code\b[^>]*>)?(.*?)(?:</code>\s*)?</pre>|<div\b|</div>}m.freeze

    module_function

    def site_files
      Dir[File.join(SITE_ROOT, '**', '*.html')]
        .reject { |f| f.include?('/_site/') || f.include?('/_unused_plugins/') }
        .sort
    end

    def extract(path)
      html = File.read(path, encoding: 'UTF-8')
      rel = path.sub("#{SITE_ROOT}/", '')
      groups = []
      loose = []
      stack = []
      pending = nil
      keys = Hash.new(0)
      @orphan = nil
      @orphans = 0
      @start_orphan = lambda do |panel|
        @orphans += 1
        @orphan = { kind: :group, key: "#{rel}#tabs-#{@orphans}", title: "tabs #{@orphans}", line: panel[:line],
                    depth: panel[:depth], tabs: {}, titles: [] }
        groups << @orphan
        @orphan
      end
      html.scan(TOKEN) do
        m = Regexp.last_match
        line = html[0, m.begin(0)].count("\n") + 1
        if m[0].start_with?('<button')
          attrs = m[1]
          next unless attrs =~ /class="accordion([^"]*)"/

          classes = Regexp.last_match(1)
          title = m[2].dup
          title = title.gsub(/<[^>]*>/, '') while title.match?(/<[^>]*>/)
          title = CGI.unescapeHTML(title).delete('<>').strip
          pending = { kind: classes.include?('inner') ? :inner : :group, title: title,
                      id: attrs[/id="([^"]+)"/, 1], line: line }
        elsif m[0].start_with?('<pre')
          lang = m[3][/lang-([a-z]+)/, 1] || (m[3].include?('prettyprint') ? 'plain' : 'other')
          code = CGI.unescapeHTML(m[4]).gsub(/\{%-?\s*(?:end)?raw\s*-?%\}/, '')
          block = Block.new(file: rel, line: line, lang: lang, code: code)
          tab = stack.reverse.find { |p| p[:kind] == :inner }
          if tab
            tab[:group][:tabs][tab[:title]] ||= []
            tab[:group][:tabs][tab[:title]] << block
          else
            loose << block
          end
        elsif m[0] == '<div'
          if pending
            panel = pending.merge(depth: stack.length)
            if panel[:kind] == :inner
              panel[:group] = stack.reverse.find { |p| p[:kind] == :group } || orphan_group(panel)
            else
              @orphan = nil
              base = panel[:id] || panel[:title].downcase.gsub(/[^a-z0-9]+/, '_')
              keys[base] += 1
              panel[:key] = "#{rel}##{base}#{keys[base] > 1 ? "-#{keys[base]}" : ''}"
              panel[:tabs] = {}
              groups << panel
            end
            stack << panel
            pending = nil
          else
            stack << { kind: :none }
          end
        else
          stack.pop
        end
      end
      [groups.map { |g| Group.new(file: rel, line: g[:line], key: g[:key], title: g[:title], tabs: g[:tabs]) }, loose]
    end

    # Language tabs with no enclosing accordion form a group of their own,
    # keyed by their order on the page; a repeated tab title starts the next.
    def orphan_group(panel)
      group = @orphan
      group = nil if group && (group[:titles].include?(panel[:title]) || group[:depth] != panel[:depth])
      group ||= @start_orphan.call(panel)
      group[:titles] << panel[:title]
      group
    end
  end

  # ---------------------------------------------------------------------
  # REST API tab: one control-plane call per curl command.
  # ---------------------------------------------------------------------
  module Rest
    DATA_FLAG = /(?:^|\s)(?:-d|--data|--data-raw|--data-binary)\s+/.freeze

    module_function

    REST_TAB = /REST API/.freeze

    # Every tab titled like "REST API" ("REST API inline json", "Retrieve
    # Configuration (REST API)") counts; each of its <pre> blocks is one
    # alternative list of calls.
    def blocks(tabs)
      tabs.select { |title, _| title =~ REST_TAB }.values.flatten
    end

    def alternatives(blocks)
      blocks.map { |b| calls(b.code) }.reject(&:empty?)
    end

    RAW_REQUEST = %r{^(GET|PUT|POST|DELETE) (/mockserver/\S*) HTTP/1\.1$}.freeze

    def calls(code)
      return raw_http_calls(code) if code =~ RAW_REQUEST

      text = code.gsub(/\\\n/, ' ').lines.reject { |l| l =~ /\A\s*#/ }.join
      text.split(/(?=^\s*curl\b)/).select { |c| c =~ /\A\s*curl\b/ }.map { |c| parse_curl(c) }.compact
    end

    # A tab written as a raw HTTP request: request line, headers, blank line, body.
    def raw_http_calls(code)
      code.split(/(?=#{RAW_REQUEST})/).filter_map do |request|
        next unless (m = RAW_REQUEST.match(request))

        uri = URI.parse(m[2])
        body = request.split(/\r?\n\r?\n/, 2)[1]&.strip
        { 'method' => m[1], 'path' => uri.path, 'query' => uri.query, 'body' => body.to_s.empty? ? nil : body,
          'quoting' => false }
      end
    end

    # nil for a curl that does not call the control plane; a call marked
    # unparseable for one that does but cannot be read.
    def parse_curl(command)
      return nil unless command.include?('/mockserver/')

      flags = flag_words(command)
      url = flags[:url]
      return { 'unparseable' => true, 'text' => command.strip } unless url&.include?('/mockserver/')

      body, damaged = body_of(command)
      uri = URI.parse(url)
      { 'method' => (flags[:method] || (body ? 'POST' : 'GET')).upcase,
        'path' => uri.path.sub(%r{.*?(/mockserver/)}, '\\1'), 'query' => uri.query, 'body' => body,
        'quoting' => damaged }
    rescue URI::InvalidURIError
      { 'unparseable' => true, 'text' => command.strip }
    end

    # The flags before the body; the body itself is read raw by body_of.
    def flag_words(command)
      head = command.split(DATA_FLAG, 2).first
      words = begin
        Shellwords.split(head)
      rescue ArgumentError
        head.split(/\s+/)
      end
      out = {}
      words.each_with_index do |w, i|
        out[:method] = words[i + 1] if %w[-X --request].include?(w)
        out[:url] ||= w if w =~ %r{\Ahttps?://}
      end
      out[:url] ||= command[%r{https?://[^\s'"]+}]
      out
    end

    # The body as the author wrote it between -d '...'. A body that itself holds a
    # single quote is cut short by the shell, so it is reported as damaged.
    def body_of(command)
      m = DATA_FLAG.match(command)
      return [nil, false] unless m

      rest = command[m.end(0)..]
      return ["@file:#{File.basename(rest[/\A@(\S+)/, 1])}", false] if rest.start_with?('@')
      return [Shellwords.split(rest).first, false] unless rest.start_with?("'")

      ends = (1...rest.length).select { |i| rest[i] == "'" }.reverse
      ends.each do |e|
        raw = rest[1...e].gsub("'\\''", "'")
        next unless json?(raw)

        shell = begin
          Shellwords.split(rest[0..e]).join(' ')
        rescue ArgumentError
          nil
        end
        return [raw, shell != raw]
      end
      [rest[1...(ends.last || rest.length)], false]
    end

    def json?(text)
      JSON.parse(text)
      true
    rescue JSON::ParserError
      false
    end
  end

  # ---------------------------------------------------------------------
  # Normalisation: both sides become one canonical form, so two encodings the
  # server reads identically compare equal. Every rule is a server default or
  # an alternative wire form the server's deserialiser accepts.
  # ---------------------------------------------------------------------
  module Normalise
    MULTI_VALUE_KEYS = %w[headers queryStringParameters pathParameters trailers].freeze
    MODIFIER_KEYS = %w[add replace remove].freeze

    module_function

    def call(call)
      body = call['body']
      parsed = body.nil? || body.to_s.strip.empty? ? nil : begin
        JSON.parse(body)
      rescue JSON::ParserError
        body.to_s.strip
      end
      items = call['path'] == '/mockserver/expectation' && parsed.is_a?(Array) ? parsed : [parsed]
      items.map do |item|
        item = value(item)
        item = expectation(item) if call['path'] == '/mockserver/expectation' && item.is_a?(Hash)
        item = load_names(item) if call['path'] =~ %r{/mockserver/loadScenario/(start|stop)\z}
        item = item.reject { |k, v| k == 'startDelayMillis' && v == 0 } if call['path'] == '/mockserver/loadScenario' && item.is_a?(Hash)
        item = verification(item) if call['path'] == '/mockserver/verify' && item.is_a?(Hash)
        item = nil if item == {} && call['path'] =~ %r{/mockserver/(retrieve|clear)\z} # matches every request either way
        { 'method' => call['method'], 'path' => call['path'], 'query' => query(call['path'], call['query']),
          'body' => item }
      end
    end

    # type and format are read case-insensitively; /mockserver/retrieve reads a
    # missing format as JSON.
    def query(path, q)
      return nil if q.nil? || q.empty?

      params = URI.decode_www_form(q).map { |k, v| [k, %w[type format].include?(k) ? v.upcase : v] }.sort.to_h
      params.delete('format') if path == '/mockserver/retrieve' && params['format'] == 'JSON'
      params.delete('type') if path == '/mockserver/clear' && params['type'] == 'ALL'
      params.empty? ? nil : params
    end

    # An absent or empty httpRequest both match every request; times and
    # timeToLive default to unlimited and priority to 0.
    def expectation(e)
      e = e.dup
      e.delete('httpRequest') if e['httpRequest'] == {}
      %w[times timeToLive].each { |t| e.delete(t) if e[t] == { 'unlimited' => true } }
      e.delete('priority') if e['priority'] == 0
      e['httpResponse'] = response(e['httpResponse']) if e['httpResponse'].is_a?(Hash)
      e['httpResponses'] = e['httpResponses'].map { |r| r.is_a?(Hash) ? response(r) : r } if e['httpResponses'].is_a?(Array)
      e
    end

    # A missing atLeast or atMost is read as -1 (no bound), and no count is
    # below 0, so atLeast 0 bounds nothing either.
    def verification(v)
      return v unless v['times'].is_a?(Hash)

      times = v['times'].reject { |k, n| (k == 'atLeast' && [0, -1].include?(n)) || (k == 'atMost' && n == -1) }
      v.merge('times' => times)
    end

    # {"name": x} and {"names": [x]} start or stop the same scenario; a load
    # scenario's startDelayMillis defaults to 0.
    def load_names(b)
      b.is_a?(Hash) && b.keys == ['name'] ? { 'names' => [b['name']] } : b
    end

    # A response body string holding JSON is compared as JSON, so whitespace
    # differences in a hand-written literal do not count. A response without
    # a status code is sent as 200 OK.
    def response(r)
      if r['statusCode'].nil? || r['statusCode'] == 200
        r = r.reject { |k, v| (k == 'statusCode' && v == 200) || (k == 'reasonPhrase' && v == 'OK') }
      end
      return r unless r['body'].is_a?(String)

      parsed = begin
        JSON.parse(r['body'])
      rescue JSON::ParserError
        nil
      end
      parsed.is_a?(Hash) || parsed.is_a?(Array) ? r.merge('body' => { 'json-string' => parsed }) : r
    end

    def value(v, key = nil)
      case v
      when Array then v.map { |x| value(x) }
      when Hash then hash(v, key)
      else v
      end
    end

    def hash(h, key)
      out = {}
      h.each do |k, x|
        out[k] = if MULTI_VALUE_KEYS.include?(k) then multi(x)
                 elsif k == 'cookies' then cookies(x)
                 elsif k == 'body' then body(x)
                 else value(x, k)
                 end
      end
      out.delete('unlimited') if out['unlimited'] == false && (out.key?('remainingTimes') || out.key?('timeToLive'))
      out.delete('scheme') if key == 'socketAddress' && out['scheme'] == 'HTTP' # the default scheme
      override_names(out) if key == 'httpOverrideForwardedRequest'
      delay(out) if key == 'delay' || key == 'thinkTime'
      out
    end

    # httpRequest and httpResponse are the older names the server still reads
    # for an override's requestOverride and responseOverride.
    def override_names(o)
      { 'httpRequest' => 'requestOverride', 'httpResponse' => 'responseOverride' }.each do |old, new|
        o[new] = o.delete(old) if o.key?(old) && !o.key?(new)
      end
    end

    # A delay's value defaults to 0, and its time unit only scales value and
    # distribution, so a unit with neither is inert.
    def delay(d)
      d.delete('value') if d['value'] == 0
      d.delete('timeUnit') unless d.key?('value') || d.key?('distribution')
    end

    def multi(x)
      if x.is_a?(Array) && x.all? { |e| e.is_a?(Hash) && e.key?('name') }
        x.each_with_object({}) { |e, m| (m[e['name']] ||= []).concat(value(Array(e['values']))) }
      elsif x.is_a?(Hash) && !(x.keys - MODIFIER_KEYS).empty?
        x.each_with_object({}) do |(k, v), m|
          m[k] = if k == 'keyMatchStyle' then v
                 elsif v.is_a?(Hash) then value(v)
                 else value(Array(v))
                 end
        end
      else
        value(x)
      end
    end

    def cookies(x)
      if x.is_a?(Array) && x.all? { |e| e.is_a?(Hash) && e.key?('name') }
        x.each_with_object({}) do |e, m|
          v = e.key?('value') ? e['value'] : Array(e['values']).first
          m[e['name']] = m.key?(e['name']) ? Array(m[e['name']]) + [v] : v
        end
      elsif x.is_a?(Hash) && !(x.keys - MODIFIER_KEYS).empty?
        x.transform_values { |v| v.is_a?(Array) && v.length == 1 ? v.first : value(v) }
      else
        value(x)
      end
    end

    def body(x)
      return value(x) unless x.is_a?(Hash)

      b = value(x)
      return b['string'] if b['type'] == 'STRING' && (b.keys - %w[type string]).empty?

      if b['type'] == 'JSON' && b['json'].is_a?(String)
        begin
          b['json'] = JSON.parse(b['json'])
        rescue JSON::ParserError
          nil
        end
      end
      b
    end
  end

  # ---------------------------------------------------------------------
  # Runner: each Ruby block runs in a forked child so examples cannot affect
  # one another; the child reports the control-plane calls it made.
  # ---------------------------------------------------------------------
  module Runner
    # A fragment (no require of the client) continues an earlier example on
    # its page, which created +client+ and may have included MockServer.
    FRAGMENT_PRELUDE = "include MockServer\nclient = MockServer::Client.new('localhost', 1080)\n"

    # Stands in for the breakpoint WebSocket: a fixed client id, handlers ignored.
    class FakeWebSocket
      def client_id
        'website-example-client-id'
      end

      def method_missing(name, *_args)
        name.to_s.start_with?('set_') ? nil : super
      end

      def respond_to_missing?(name, include_private = false)
        name.to_s.start_with?('set_') || super
      end
    end

    module_function

    # Loads the client once, before forking. WebMock is switched off in this
    # process only when this call loaded it, so a test suite keeps its own setup.
    def preload
      $LOAD_PATH.unshift(File.join(CLIENT_ROOT, 'lib'))
      loaded = defined?(WebMock)
      require 'webmock'
      require 'mockserver-client'
      WebMock.disable! unless loaded
    end

    # The child runs in a temporary directory, so a file an example writes
    # (load_injection.html writes load-report.xml) is removed afterwards.
    def run(code)
      Dir.mktmpdir('website-example') { |dir| run_in(dir, code) }
    end

    def run_in(dir, code)
      reader, writer = IO.pipe
      pid = fork do
        reader.close
        Dir.chdir(dir)
        result = child(code)
        writer.write(JSON.generate(result))
        writer.close
        exit!(0)
      end
      writer.close
      output = nil
      waiter = Thread.new { output = reader.read }
      unless waiter.join(BLOCK_TIMEOUT_SECONDS)
        Process.kill('KILL', pid)
        waiter.join
      end
      Process.wait(pid)
      reader.close
      output.nil? || output.empty? ? { 'calls' => [], 'error' => 'timed out or crashed' } : JSON.parse(output)
    end

    def child(code)
      $stdout.reopen(File::NULL)
      $stderr.reopen(File::NULL)
      calls = []
      install_interceptors(calls)
      error = nil
      source = code.include?("require 'mockserver-client'") ? code : FRAGMENT_PRELUDE + code
      begin
        TOPLEVEL_BINDING.dup.eval(source, '(example)', 1)
      rescue Exception => e # rubocop:disable Lint/RescueException
        error = "#{e.class}: #{e.message.lines.first&.strip}"
      end
      { 'calls' => calls, 'error' => error }
    end

    def install_interceptors(calls)
      WebMock.enable!
      WebMock.disable_net_connect!
      WebMock::API.stub_request(:any, /.*/).to_return do |req|
        uri = req.uri
        calls << { 'method' => req.method.to_s.upcase, 'path' => uri.path, 'query' => uri.query, 'body' => req.body }
        reply(uri.path, req.body)
      end
      Kernel.module_eval { define_method(:sleep) { |*_| 0 } }
      # Random ids would make an allowlist digest change on every run.
      require 'securerandom'
      SecureRandom.singleton_class.prepend(Module.new do
        define_method(:uuid) { '00000000-0000-4000-8000-000000000000' }
        define_method(:hex) { |n = 16| '0' * (n * 2) }
      end)
      srand(0)
      # A file an example reads stands for the file a REST API tab sends with -d @file.
      File.singleton_class.prepend(Module.new do
        %i[read binread].each do |name|
          define_method(name) do |path, *args, **kw|
            File.exist?(path) ? super(path, *args, **kw) : "@file:#{File.basename(path.to_s)}"
          end
        end
      end)
      MockServer::Client.prepend(Module.new do
        define_method(:register_websocket_callback) { |*_| 'website-example-client-id' }
        define_method(:ensure_breakpoint_websocket) { |*_| FakeWebSocket.new }
      end)
      MockServer::BinaryLauncher.singleton_class.prepend(Module.new do
        define_method(:start) { |*_args, **_kw| nil }
      end)
      [TCPSocket, Socket].each do |klass|
        klass.singleton_class.prepend(Module.new do
          %i[new open tcp].each do |name|
            define_method(name) { |*_args, **_kw| raise IOError, 'network disabled in website example check' }
          end
        end)
      end
    end

    def reply(path, body)
      case path
      when %r{/mockserver/expectation\z}, %r{/mockserver/openapi\z}
        { status: 201, body: body.to_s.strip.start_with?('[') ? body : "[#{body}]" }
      when %r{/mockserver/verify(Sequence)?\z} then { status: 202, body: '' }
      when %r{/mockserver/status\z}, %r{/mockserver/bind\z} then { status: 200, body: '{"ports":[1080]}' }
      when %r{/mockserver/(clear|reset|stop)\z} then { status: 200, body: '' }
      when %r{/mockserver/scenario/}, %r{/mockserver/pact/verify\z} then { status: 200, body: '{}' }
      when %r{/mockserver/(explain|explainUnmatched|debugMismatch)\z} then { status: 200, body: '{}' }
      when %r{/mockserver/contractTest\z} then { status: 200, body: '{"allPassed":true,"results":[]}' }
      when %r{/mockserver/pact\z} then { status: 200, body: '{"interactions":[]}' }
      when %r{/mockserver/breakpoint/matcher\z} then { status: 201, body: '{"id":"website-example-breakpoint-id"}' }
      else { status: 200, body: '[]' }
      end
    end
  end

  # The Ruby blocks run in this process's forked children (Runner above).
  module RubyRunner
    module_function

    def prepare(_units)
      Runner.preload
    end

    def run(unit)
      Runner.run(unit[:blocks].map(&:code).join("\n"))
    end
  end

  # ---------------------------------------------------------------------
  # Check
  # ---------------------------------------------------------------------
  module Check
    STATUSES = %i[not_compared differs sent_nothing raised does_not_compile rest_unparseable rest_quoting
                  rest_invalid_json].freeze
    FAILURES = STATUSES + %i[digest_changed stale_allowlist unknown_key block_count]

    module_function

    def control_plane(calls)
      calls.select { |c| c['path'].to_s.start_with?('/mockserver/') }
    end

    def normalised(calls)
      calls.flat_map { |c| Normalise.call(c) }
    end

    def digest(value)
      Digest::SHA256.hexdigest(JSON.generate(value))[0, 12]
    end

    def load_allowlist(path = ALLOWLIST)
      allow = File.exist?(path) ? (YAML.safe_load(File.read(path, encoding: 'UTF-8')) || {}) : {}
      bad = allow.reject do |_k, v|
        v.is_a?(Hash) && STATUSES.include?(v['status'].to_s.to_sym) && !v['reason'].to_s.strip.empty? &&
          v['digest'].to_s =~ /\A\h{12}\z/
      end
      raise ArgumentError, "allowlist entries need a known status, a reason and a digest: #{bad.keys.join(', ')}" unless bad.empty?

      allow
    end

    # Every block of the language on the site is one unit: an accordion's tab
    # in that language, or a block outside any accordion (keyed by its code's
    # digest). Each unit is compared with its REST API tab or must be allowlisted.
    def units(files, lang = 'ruby')
      files.flat_map do |path|
        groups, loose = Extractor.extract(path)
        rel = path.sub("#{SITE_ROOT}/", '')
        seen = Hash.new(0)
        grouped = groups.filter_map do |group|
          blocks = group.tabs.values.flatten.select { |b| b.lang == lang }
          { key: group.key, group: group, blocks: blocks } unless blocks.empty?
        end
        grouped + loose.select { |b| b.lang == lang }.map do |block|
          base = "#{rel}#loose-#{digest(block.code)}"
          seen[base] += 1
          { key: seen[base] > 1 ? "#{base}-#{seen[base]}" : base, blocks: [block] }
        end
      end
    end

    def raw_block_count(files, lang = 'ruby')
      files.sum { |path| File.read(path, encoding: 'UTF-8').scan(/lang-#{lang}\b/).length }
    end

    def allowlist_path(lang)
      lang == 'ruby' ? ALLOWLIST : File.join(__dir__, "allowlist-#{lang}.yml")
    end

    def runner_for(lang)
      return RubyRunner if lang == 'ruby'

      require_relative 'languages'
      Languages.runner(lang)
    end

    def run(argv, files: Extractor.site_files, allow: nil, lang: nil, runner: nil)
      verbose = argv.include?('--verbose')
      only = argv[argv.index('--only') + 1] if argv.include?('--only')
      lang ||= argv.include?('--lang') ? argv[argv.index('--lang') + 1] : 'ruby'
      allow ||= load_allowlist(allowlist_path(lang))
      runner ||= runner_for(lang)
      counts = Hash.new(0)
      findings = []
      all = units(files, lang)
      counts[:blocks] = all.sum { |u| u[:blocks].length }
      if counts[:blocks] != raw_block_count(files, lang)
        findings << { key: 'site', status: :block_count,
                      error: "extracted #{counts[:blocks]} #{lang} blocks, the pages hold #{raw_block_count(files, lang)}" }
      end
      selected = only ? all.select { |u| u[:key] == only } : all
      findings << { key: only, status: :unknown_key } if only && selected.empty?
      runner.prepare(selected)
      selected.each do |unit|
        finding = check_unit(unit, runner)
        entry = allow[unit[:key]]
        if finding.nil?
          counts[:ok] += 1
          findings << { key: unit[:key], status: :stale_allowlist, line: unit[:blocks].first.line } if entry
        elsif entry && entry['status'] == finding[:status].to_s && entry['digest'] == finding[:digest]
          counts[:allowlisted] += 1
          report_finding(finding.merge(status: "allowed #{finding[:status]}"), true) if verbose
        else
          if entry && entry['status'] == finding[:status].to_s
            finding = finding.merge(status: :digest_changed,
                                    error: "was #{entry['digest']}, now #{finding[:digest]}: #{finding[:error]}")
          end
          counts[finding[:status]] += 1
          findings << finding
        end
      end
      unless only
        (allow.keys - all.map { |u| u[:key] }).each { |key| findings << { key: key, status: :stale_allowlist } }
      end
      counts[:stale_allowlist] = findings.count { |f| f[:status] == :stale_allowlist }
      report(counts, findings, verbose, lang)
      findings.empty? ? 0 : 1
    ensure
      runner&.finish if runner.respond_to?(:finish)
    end

    def check_unit(unit, runner = RubyRunner)
      blocks = unit[:blocks]
      base = { key: unit[:key], line: blocks.first.line }
      code = blocks.map(&:code).join("\n")
      calls = unit[:group] ? Rest.blocks(unit[:group].tabs).flat_map { |b| Rest.calls(b.code) } : []
      if calls.empty?
        return base.merge(status: :not_compared, digest: digest(code),
                          error: unit[:group] ? 'no REST API curl call' : 'outside any accordion')
      end
      if calls.any? { |c| c['unparseable'] }
        return base.merge(status: :rest_unparseable, digest: digest(calls),
                          error: 'a REST API curl call to /mockserver/ cannot be read')
      end
      alternatives = Rest.alternatives(Rest.blocks(unit[:group].tabs))
      if calls.any? { |c| json_path?(c['path']) && !c['body'].nil? && !c['body'].start_with?('@') && !Rest.json?(c['body']) }
        return base.merge(status: :rest_invalid_json, digest: digest(calls), error: 'a REST API tab body is not valid JSON')
      end

      result = runner.run(unit)
      sent = normalised(control_plane(result['calls']))
      wants = ([alternatives.flatten] + alternatives).uniq.map { |a| normalised(a) }
      matched = wants.include?(sent)
      unless matched && result['error'].nil?
        status = if result['compile_error'] then :does_not_compile
                 elsif result['error'] then :raised
                 elsif sent.empty? then :sent_nothing
                 else :differs
                 end
        return base.merge(status: status, error: result['error'], sent: sent, want: wants.first,
                          digest: digest([sent, result['digest_error'] || result['error']]))
      end
      return nil unless calls.any? { |c| c['quoting'] }

      base.merge(status: :rest_quoting, digest: digest(sent),
                 error: "a REST API tab's -d '...' body holds a single quote the shell strips")
    end

    def json_path?(path)
      path =~ %r{/mockserver/(expectation|verify|verifySequence|retrieve|clear)\z}
    end

    def report_finding(finding, verbose)
      line = finding[:line] ? " (line #{finding[:line]})" : ''
      error = finding[:error] ? " -- #{finding[:error]}" : ''
      digest = finding[:digest] ? " [digest #{finding[:digest]}]" : ''
      puts "#{finding[:status].to_s.upcase.ljust(16)} #{finding[:key]}#{line}#{digest}#{error}"
      return unless verbose && finding[:sent]

      puts "  sent: #{JSON.generate(finding[:sent])}"
      puts "  rest: #{JSON.generate(finding[:want])}"
    end

    def report(counts, findings, verbose, lang = 'ruby')
      findings.each { |f| report_finding(f, verbose) }
      puts
      name = { 'csharp' => '.NET', 'php' => 'PHP' }.fetch(lang, lang.capitalize)
      puts "#{"#{name} blocks on the site:".ljust(29)} #{counts[:blocks]}"
      puts "Units matching REST API tab:  #{counts[:ok]}"
      puts "Allowlisted with a reason:    #{counts[:allowlisted]}"
      FAILURES.each { |f| puts "#{f.to_s.tr('_', ' ').capitalize.ljust(29)} #{counts[f]}" }
    end
  end
end

exit(WebsiteExamples::Check.run(ARGV)) if $PROGRAM_NAME == __FILE__
