# frozen_string_literal: true

# Runs the Python, Go, .NET, Rust and PHP blocks of the website for
# check_website_examples.rb --lang L. A local server on port 1080 records
# every request and answers as MockServer would (Runner.reply), so the real
# client and any raw HTTP call in a block are both captured with no code
# rewritten. Each block runs as its own process; the compiled languages first
# build every block into one program, and a block that does not build is
# reported as does_not_compile.

require 'fileutils'
require 'socket'
require 'tmpdir'

module WebsiteExamples
  module Languages
    REPO_ROOT = File.expand_path('..', CLIENT_ROOT)
    WORK_ROOT = ENV.fetch('WEBSITE_EXAMPLES_WORK_DIR', File.join(REPO_ROOT, '.tmp', 'website-examples'))
    PORT = 1080
    # A request for anything but localhost goes to a closed port, so a block
    # that downloads (a jar, a container image) fails fast instead of waiting.
    BLOCK_ENV = %w[HTTP_PROXY HTTPS_PROXY http_proxy https_proxy].to_h { |k| [k, 'http://127.0.0.1:9'] }
                                                               .merge('NO_PROXY' => 'localhost,127.0.0.1,::1',
                                                                      'no_proxy' => 'localhost,127.0.0.1,::1')

    module_function

    def runner(lang)
      name = { 'python' => :Python, 'go' => :Go, 'csharp' => :CSharp, 'rust' => :Rust, 'php' => :Php }[lang]
      adapter = const_get(name) if name && const_defined?(name)
      raise ArgumentError, "no runner for #{lang}; use ruby, python, go, csharp, rust or php" unless adapter

      ProcessRunner.new(adapter)
    end

    # Stable per unit, so a compile error's file name (part of its digest) does
    # not change when another block is added.
    def block_id(unit)
      "b_#{Check.digest(unit[:key])}"
    end

    def code(unit)
      unit[:blocks].map(&:code).join("\n")
    end

    def sh(env, *cmd, chdir:)
      out = IO.popen(env, cmd, chdir: chdir, err: %i[child out], &:read)
      [out, $?.success?]
    end

    # Builds a program from every unit (the block yields the units to build
    # and returns [output, success]), dropping each unit whose code breaks the
    # build until the rest builds. Returns { key => its first build error }.
    def build_until_clean(units, parse)
      broken = {}
      ids = units.to_h { |u| [block_id(u), u[:key]] }
      10.times do
        output, ok = yield(units.reject { |u| broken.key?(u[:key]) })
        return broken if ok

        found = parse.call(output)
        next if found.nil? # the adapter changed the sources and wants another build

        found = found.select { |id, _| ids.key?(id) && !broken.key?(ids[id]) }
        raise "build failed with no error the check can attribute to a block:\n#{output[-3000..] || output}" if found.empty?

        found.each { |id, message| broken[ids[id]] = message }
      end
      raise 'build still failing after dropping blocks ten times'
    end

    # -------------------------------------------------------------------
    # Records requests to localhost:1080 and answers like MockServer.
    # -------------------------------------------------------------------
    class CaptureServer
      REASONS = { 200 => 'OK', 201 => 'Created', 202 => 'Accepted' }.freeze

      def initialize(port = PORT)
        @calls = []
        @lock = Mutex.new
        @servers = [TCPServer.new('127.0.0.1', port)]
        begin
          @servers << TCPServer.new('::1', port)
        rescue Errno::EADDRNOTAVAIL, Errno::EAFNOSUPPORT
          nil # no IPv6 loopback: clients resolving localhost use 127.0.0.1
        end
        @threads = @servers.map { |s| Thread.new { accept_loop(s) } }
      end

      def drain
        @lock.synchronize { @calls.slice!(0..) }
      end

      def close
        @servers.each { |s| s.close rescue nil } # rubocop:disable Style/RescueModifier
        @threads.each(&:kill)
      end

      private

      def accept_loop(server)
        loop { Thread.new(server.accept) { |socket| serve(socket) } }
      rescue IOError, SystemCallError
        nil
      end

      # A TLS handshake (first byte 0x16) is closed at once: waiting for a
      # request line would leave a TLS client hanging until its block times out.
      def serve(socket)
        return if socket.recv(1, Socket::MSG_PEEK) == "\x16"

        while (line = socket.gets)
          method, target = line.split(' ')
          headers = read_headers(socket)
          socket.write("HTTP/1.1 100 Continue\r\n\r\n") if headers['expect'].to_s.casecmp?('100-continue')
          body = read_body(socket, headers)
          uri = URI.parse(target)
          @lock.synchronize do
            @calls << { 'method' => method, 'path' => uri.path, 'query' => uri.query, 'body' => body }
          end
          reply = typed_reply(method, uri.path, body) || Runner.reply(uri.path, body)
          text = reply[:body].to_s
          socket.write("HTTP/1.1 #{reply[:status]} #{REASONS.fetch(reply[:status], 'OK')}\r\n" \
                       "Content-Type: application/json\r\nContent-Length: #{text.bytesize}\r\n\r\n#{text}")
          break if headers['connection'].to_s.casecmp?('close')
        end
      rescue IOError, SystemCallError, URI::InvalidURIError
        nil
      ensure
        socket.close rescue nil # rubocop:disable Style/RescueModifier
      end

      # Typed clients parse these replies, so they take MockServer's shape.
      def typed_reply(method, path, body)
        name = (JSON.parse(body.to_s)['name'] rescue nil) || 'scenario' # rubocop:disable Style/RescueModifier
        case path
        when %r{/mockserver/pact/verify\z} then { status: 202, body: '{"verified":true,"interactions":[]}' }
        when %r{/mockserver/loadScenario\z}
          { status: method == 'PUT' ? 201 : 200,
            body: method == 'PUT' ? JSON.generate('name' => name) : '{"scenarios":[]}' }
        when %r{/mockserver/loadScenario/start\z} then { status: 200, body: '{"started":[],"status":"started"}' }
        when %r{/mockserver/loadScenario/stop\z} then { status: 200, body: '{"stopped":[],"status":"stopped"}' }
        when %r{/mockserver/loadScenario/([^/]+)\z}
          { status: 200, body: JSON.generate('name' => Regexp.last_match(1), 'state' => 'COMPLETED') }
        when %r{/mockserver/verifySLO\z} then { status: 200, body: '{"result":"PASS"}' }
        when %r{/mockserver/explain\z}
          { status: 200, body: '{"recomputed":true,"skippedExpectations":[],"shadowedExpectations":[]}' }
        when %r{/mockserver/debugMismatch\z} then { status: 200, body: '{"totalExpectations":0,"results":[]}' }
        when %r{/mockserver/explainUnmatched\z}
          { status: 200, body: '{"unmatchedRequestCount":0,"truncated":false,"unmatchedRequests":[]}' }
        when %r{/mockserver/scenario\z} then { status: 200, body: '{"scenarios":[]}' }
        when %r{/mockserver/scenario/([^/]+)\z}
          { status: 200, body: JSON.generate('scenarioName' => Regexp.last_match(1), 'currentState' => 'Started') }
        end
      end

      def read_headers(socket)
        headers = {}
        while (h = socket.gets) && !h.strip.empty?
          name, value = h.split(':', 2)
          headers[name.strip.downcase] = value.to_s.strip
        end
        headers
      end

      def read_body(socket, headers)
        body = if headers['transfer-encoding'].to_s.include?('chunked')
                 chunks = +''
                 while (size = socket.gets.to_s.strip.to_i(16)).positive?
                   chunks << socket.read(size)
                   socket.gets
                 end
                 socket.gets
                 chunks
               else
                 socket.read(headers['content-length'].to_i)
               end
        body.to_s.empty? ? nil : body.force_encoding('UTF-8')
      end
    end

    # -------------------------------------------------------------------
    # Runs each unit's block as a process against the capture server.
    # -------------------------------------------------------------------
    class ProcessRunner
      # A block's random ids (the Ruby runner stubs SecureRandom) and a
      # client's hash ordering must not change its digest from run to run.
      UUID = /\h{8}-\h{4}-4\h{3}-[89ab]\h{3}-\h{12}/.freeze
      HEX_ID = /\b\h{32}\b/.freeze

      def initialize(adapter, work: File.join(WORK_ROOT, adapter::NAME))
        @adapter = adapter
        @work = work
      end

      def prepare(units)
        @server = CaptureServer.new
        FileUtils.mkdir_p(@work)
        @broken = @adapter.build(units, @work)
      end

      def run(unit)
        if (message = @broken[unit[:key]])
          return { 'calls' => [], 'error' => message, 'compile_error' => true }
        end

        @server.drain
        error = Dir.mktmpdir('website-example') do |dir|
          stage_files(unit, dir)
          @adapter.stage(dir) if @adapter.respond_to?(:stage)
          execute(@adapter.command(unit, @work, dir), dir)
        end
        code = Languages.code(unit).downcase
        { 'calls' => @server.drain.each { |c| c['body'] = stable(c['body'], code) }, 'error' => error,
          'digest_error' => error && error_class(error) }
      end

      def finish
        @server&.close
      end

      private

      # A file the REST API tab sends with -d @name is in the block's directory,
      # holding the text the REST side compares, so a block reading that file
      # sends the same thing (the Ruby runner does this by stubbing File.read).
      def stage_files(unit, dir)
        return unless unit[:group]

        Rest.blocks(unit[:group].tabs).flat_map { |b| Rest.calls(b.code) }.each do |call|
          body = call['body'].to_s
          File.write(File.join(dir, body.delete_prefix('@file:')), body) if body.start_with?('@file:')
        end
      end

      def execute(command, dir)
        log = File.join(dir, '.website-example-output')
        # HOME is the block's directory, so a launcher finds no cached server
        # to start and every machine sees the same first run.
        env = BLOCK_ENV.merge('HOME' => dir, 'XDG_CACHE_HOME' => File.join(dir, '.cache')).merge(@adapter.env(@work))
        pid = Process.spawn(env, *command, chdir: dir, in: File::NULL,
                            %i[out err] => [log, 'w'], pgroup: true)
        waiter = Process.detach(pid)
        unless waiter.join(BLOCK_TIMEOUT_SECONDS)
          Process.kill('KILL', -pid) rescue nil # rubocop:disable Style/RescueModifier
          waiter.join
          return 'timed out'
        end
        return nil if waiter.value.success?

        scrub(@adapter.error(File.read(log, encoding: 'UTF-8').scrub) || "exit #{waiter.value.exitstatus}", dir)
      end

      # Only ids the block's code does not spell out are random.
      def stable(body, code)
        return body if body.nil?

        body = body.gsub(UUID) { |id| code.include?(id.downcase) ? id : '00000000-0000-4000-8000-000000000000' }
                   .gsub(HEX_ID) { |id| code.include?(id.downcase) ? id : '0' * 32 }
        JSON.generate(sorted(JSON.parse(body)))
      rescue JSON::ParserError
        body
      end

      def sorted(value)
        case value
        when Hash then value.sort.to_h { |k, v| [k, sorted(v)] }
        when Array then value.map { |v| sorted(v) }
        else value
        end
      end

      # The digest keeps an error's type and drops the detail an operating
      # system words its own way ("(0x80004005)", an OpenSSL or Security
      # framework message), so CI and a laptop agree on it.
      # A download URL names the platform (darwin-aarch64 locally, linux-x86_64 in CI) and version.
      def error_class(error)
        error.gsub(/(?:darwin|linux|windows)-(?:x86_64|aarch64|arm64|amd64)/, 'PLATFORM')
             .gsub(/\b\d+\.\d+\.\d+(?:-SNAPSHOT)?\b/, 'VERSION')
             .gsub('()', '').sub(/[\s:]*[(\[{<"'].*\z/m, '')
      end

      # Paths, addresses and times vary between machines; the digest must not.
      def scrub(text, dir)
        text.to_s.gsub(dir, '<dir>').gsub(File.realpath(dir), '<dir>').gsub(@work, '<work>')
            .gsub(/0x\h+/, '0x?').gsub(%r{\d{4}/\d\d/\d\d \d\d:\d\d:\d\d }, '')
            .gsub(/\[Errno \d+\]|os error \d+|errno=\d+/, '<errno>').strip[0, 300]
      end
    end

    # The first line of output that names an error, else the last line.
    def first_error_line(output, pattern)
      lines = output.lines.map(&:strip).reject(&:empty?)
      lines.find { |l| l =~ pattern } || lines.last
    end

    # -------------------------------------------------------------------
    # Python: each block is a script run by python3 with the client's
    # source tree on PYTHONPATH.
    # -------------------------------------------------------------------
    module Python
      NAME = 'python'
      IMPORTS = "from mockserver import *\nfrom mockserver.models import *\n"
      CLIENT = "client = MockServerClient(\"localhost\", 1080)\n"

      module_function

      # A block with no imports, or using a client it never creates, continues
      # an earlier example on its page.
      def build(units, work)
        units.each do |u|
          code = Languages.code(u)
          if code =~ /\bclient\./ && code !~ /\bclient\s*=(?!=)|\bas\s+client\b/
            code = IMPORTS + CLIENT + code
          elsif code !~ /^(from|import)\s/
            code = IMPORTS + code
          end
          File.write(File.join(work, "#{Languages.block_id(u)}.py"), code)
        end
        {}
      end

      def command(unit, work, _dir)
        [ENV.fetch('PYTHON', 'python3'), File.join(work, "#{Languages.block_id(unit)}.py")]
      end

      # The user's site-packages stay importable although HOME moves.
      def env(_work)
        @user_base ||= IO.popen([ENV.fetch('PYTHON', 'python3'), '-c', 'import site; print(site.getuserbase())'], &:read).strip
        { 'PYTHONPATH' => File.join(REPO_ROOT, 'mockserver-client-python'), 'PYTHONDONTWRITEBYTECODE' => '1',
          'PYTHONUSERBASE' => @user_base }
      end

      def error(output)
        lines = output.lines.map(&:strip).reject(&:empty?)
        output.include?('Traceback') ? lines.last : lines.first
      end
    end

    # -------------------------------------------------------------------
    # Go: each block becomes a package (a snippet's statements go into its
    # Main function, a program's main is renamed Main); one main program
    # runs the package named by its argument.
    # -------------------------------------------------------------------
    module Go
      NAME = 'go'
      MODULE = 'github.com/mock-server/mockserver-monorepo/mockserver-client-go/v7'
      IMPORT = /^import\s*(\([^)]*\)|[^\n]*)\n?/m.freeze

      module_function

      def build(units, work)
        client = File.join(REPO_ROOT, 'mockserver-client-go')
        FileUtils.rm_rf(File.join(work, 'blocks'))
        File.write(File.join(work, 'go.mod'), <<~MOD)
          module websiteexamples

          go 1.21

          require #{MODULE} v7.0.0
          #{File.read(File.join(client, 'go.mod'))[/^require (?!\().*$/]}
          replace #{MODULE} => #{client}
        MOD
        FileUtils.cp(File.join(client, 'go.sum'), File.join(work, 'go.sum'))
        @work = work
        @codes = units.to_h { |u| [Languages.block_id(u), Languages.code(u)] }
        @unused = Hash.new { |h, k| h[k] = [] }
        @codes.each_key { |id| write_block(id) }
        Languages.build_until_clean(units, method(:errors)) do |ok_units|
          write_main(work, ok_units)
          Languages.sh({}, 'go', 'build', '-gcflags=-e', '-o', 'runner', '.', chdir: work)
        end
      end

      def write_block(id)
        dir = File.join(@work, 'blocks', id)
        FileUtils.mkdir_p(dir)
        File.write(File.join(dir, 'main.go'), source(@codes[id], id, @unused[id]))
      end

      # A snippet's variables become locals of Main, where Go rejects one that is
      # never read; a reader pastes the snippet into code that reads it, so the
      # check reads it too (+unused+). A whole program must compile as written.
      def source(code, id, unused = [])
        if code =~ /^package main\b/
          code = code.sub(/^package main\b/, "package #{id}")
          return code.sub(/^func main\(\)/, 'func Main()') if code =~ /^func main\(\)/

          # A program that only defines its example function: Main calls it.
          return "#{code}\n\nfunc Main() {\n#{code.scan(/^func (\w+)\(\)/).map { |f| "\t#{f[0]}()\n" }.join}}\n"
        end

        imports = code.scan(IMPORT).map { |m| "import #{m[0].strip}" }
        rest = code.gsub(IMPORT, '')
        decls, body = split_declarations(rest)
        body = "client := mockserver.New(\"localhost\", 1080)\n#{body}" if body =~ /\bclient\./ && body !~ /\bclient\s*:?=/
        if body =~ /\bmockserver\./ && imports.none? { |i| i.include?(MODULE) }
          imports << "import mockserver \"#{MODULE}\""
        end
        reads = unused.map { |v| "\t_ = #{v}\n" }.join
        "package #{id}\n\n#{imports.join("\n")}\n\nfunc Main() {\n#{body}\n#{reads}}\n\n#{decls}"
      end

      # Top-level func and type declarations stay outside Main.
      def split_declarations(code)
        decls = +''
        body = +''
        inside = false
        code.each_line do |line|
          inside = true if line =~ /^(func|type)\s/
          (inside ? decls : body) << line
          inside = false if inside && line =~ /^[})]/
        end
        [decls, body]
      end

      def write_main(work, units)
        imports = units.map { |u| "\t#{Languages.block_id(u)} \"websiteexamples/blocks/#{Languages.block_id(u)}\"" }
        entries = units.map { |u| "\t\"#{Languages.block_id(u)}\": #{Languages.block_id(u)}.Main," }
        File.write(File.join(work, 'main.go'), <<~GO)
          package main

          import (
          \t"os"
          #{imports.join("\n")}
          )

          var blocks = map[string]func(){
          #{entries.join("\n")}
          }

          func main() { blocks[os.Args[1]]() }
        GO
      end

      # "blocks/b_x/main.go:3:2: undefined: fmt" -> { "b_x" => "main.go:3:2: undefined: fmt" };
      # nil when only a snippet's unread variables failed (they are read next time).
      def errors(output)
        found = {}
        rewritten = false
        output.lines.each do |line|
          next unless line =~ %r{blocks/(b_\h+)/(main\.go:\d+:\d+: (.*))}

          id = Regexp.last_match(1)
          message = Regexp.last_match(2).strip
          variable = Regexp.last_match(3)[/\Adeclared and not used: (\w+)\z/, 1]
          if variable && @codes[id] !~ /^package main\b/ && !@unused[id].include?(variable)
            @unused[id] << variable
            rewritten = true
          else
            found[id] ||= message
          end
        end
        found.each_key { |id| @unused.delete(id) }
        @unused.each_key { |id| write_block(id) } if rewritten
        found.empty? && rewritten ? nil : found
      end

      def command(unit, work, _dir)
        [File.join(work, 'runner'), Languages.block_id(unit)]
      end

      def env(_work)
        {}
      end

      def error(output)
        Languages.first_error_line(output, /^panic: |error|Error|refused|failed/)
      end
    end

    # -------------------------------------------------------------------
    # Rust: each block becomes a module of one crate; a snippet's statements
    # form its run function, and a block of items has its no-argument
    # functions called. The crate pins dependency versions to the client's.
    # -------------------------------------------------------------------
    module Rust
      NAME = 'rust'
      RESULT = 'std::result::Result<(), Box<dyn std::error::Error>>'

      module_function

      def build(units, work)
        client = File.join(REPO_ROOT, 'mockserver-client-rust')
        FileUtils.mkdir_p(File.join(work, 'src'))
        Dir[File.join(work, 'src', 'b_*.rs')].each { |f| File.delete(f) }
        File.write(File.join(work, 'Cargo.toml'), <<~TOML)
          [package]
          name = "website-examples"
          version = "0.0.0"
          edition = "2021"
          publish = false

          [dependencies]
          mockserver-client = { path = "#{client}" }
          reqwest = { version = "0.12", features = ["blocking", "json"] }
          serde_json = "1"
          uuid = { version = "1", features = ["v4"] }

          [workspace]
        TOML
        FileUtils.cp(File.join(client, 'Cargo.lock'), File.join(work, 'Cargo.lock')) unless File.exist?(File.join(work, 'Cargo.lock'))
        units.each { |u| File.write(File.join(work, 'src', "#{Languages.block_id(u)}.rs"), source(Languages.code(u))) }
        Languages.build_until_clean(units, method(:errors)) do |ok_units|
          write_main(work, ok_units)
          Languages.sh(env(work), 'cargo', 'build', '--quiet', '--color', 'never', chdir: work)
        end
      end

      def source(code)
        if code =~ /^(?:pub\s+)?fn\s/
          calls = code.scan(/^(?:pub\s+)?fn (\w+)\(\)/).map { |f| "    let _ = #{f[0]}();\n" }.join
          return "#{code.gsub(/^#\[(test|ignore[^\]]*)\]\n/, '')}\n\npub fn run() -> #{RESULT} {\n#{calls}    Ok(())\n}\n"
        end

        if code =~ /\bclient\./ && code !~ /let\s+(mut\s+)?client\b/
          code = "use mockserver_client::*;\nlet client = ClientBuilder::new(\"localhost\", 1080).build().unwrap();\n#{code}"
        end
        "pub fn run() -> #{RESULT} {\n#{code}\n;\nOk(())\n}\n"
      end

      def write_main(work, units)
        ids = units.map { |u| Languages.block_id(u) }
        File.write(File.join(work, 'src', 'main.rs'), <<~RUST)
          #![allow(warnings)]
          #{ids.map { |id| "mod #{id};" }.join("\n")}

          fn main() {
              let name = std::env::args().nth(1).expect("block name");
              let result: #{RESULT} = match name.as_str() {
          #{ids.map { |id| "        \"#{id}\" => #{id}::run()," }.join("\n")}
                  _ => panic!("unknown block {name}"),
              };
              if let Err(e) = result {
                  eprintln!("error: {e}");
                  std::process::exit(1);
              }
          }
        RUST
      end

      # "error[E0425]: cannot find value `x`" then " --> src/b_x.rs:5:9"
      def errors(output)
        found = {}
        message = nil
        output.lines.each do |line|
          if line =~ /^error(\[E\d+\])?: (.*)/
            message = line.strip
          elsif message && line =~ %r{--> src/(b_\h+)\.rs:(\d+:\d+)}
            found[Regexp.last_match(1)] ||= "#{Regexp.last_match(1)}.rs:#{Regexp.last_match(2)}: #{message}"
            message = nil
          end
        end
        found
      end

      def command(unit, work, _dir)
        [File.join(target(work), 'debug', 'website-examples'), Languages.block_id(unit)]
      end

      def target(work)
        ENV.fetch('CARGO_TARGET_DIR', File.join(work, 'target'))
      end

      def env(work)
        { 'CARGO_TARGET_DIR' => target(work), 'RUST_BACKTRACE' => '0' }
      end

      def error(output)
        lines = output.lines.map(&:strip).reject(&:empty?)
        at = lines.index { |l| l =~ /panicked at|^error: / }
        return lines.last unless at

        lines[at] =~ /panicked at/ ? "panicked: #{lines[at + 1]}" : lines[at]
      end
    end

    # -------------------------------------------------------------------
    # .NET: each block becomes a class in its own namespace of one console
    # project (a new console project's implicit usings apply); its using
    # directives stay at the top and its statements form an async Run method.
    # -------------------------------------------------------------------
    module CSharp
      NAME = 'csharp'
      USING = /^using\s+(?:static\s+)?[\w.]+(?:\s*=\s*[\w.<>]+)?\s*;[ \t]*\n?/.freeze
      TYPE = /^(?:public\s+|internal\s+)?(?:static\s+|sealed\s+|abstract\s+)*(?:class|record|struct|interface|enum)\s/.freeze

      module_function

      def build(units, work)
        FileUtils.rm_rf(File.join(work, 'blocks'))
        FileUtils.mkdir_p(File.join(work, 'blocks'))
        units.each do |u|
          id = Languages.block_id(u)
          File.write(File.join(work, 'blocks', "#{id}.cs"), source(Languages.code(u), id))
        end
        File.write(File.join(work, 'Program.cs'), <<~CS)
          var type = System.Reflection.Assembly.GetExecutingAssembly().GetType("WebsiteExamples." + args[0] + ".Block");
          await (System.Threading.Tasks.Task)type.GetMethod("Run").Invoke(null, null);
        CS
        Languages.build_until_clean(units, method(:errors)) do |ok_units|
          write_project(work, ok_units)
          Languages.sh(env(work), 'dotnet', 'build', '-nologo', '-v:q', '-clp:NoSummary', '-o', 'out', chdir: work)
        end
      end

      def source(code, id)
        if code =~ /\bclient\./ && code !~ /\bclient\s*=(?!=)/
          code = "using MockServer.Client;\nusing MockServer.Client.Models;\n" \
                 "using var client = new MockServerClient(\"localhost\", 1080);\n#{code}"
        end
        usings = code.scan(USING).map(&:strip)
        body = code.gsub(USING, '')
        types = (at = body =~ TYPE) ? body[at..] : ''
        body = body[0...at] if at
        "#{usings.join("\n")}\n\nnamespace WebsiteExamples.#{id}\n{\n" \
          "public static class Block\n{\npublic static async System.Threading.Tasks.Task Run()\n{\n" \
          "#{body}\n}\n}\n\n#{types}\n}\n"
      end

      def write_project(work, units)
        framework = "net#{`dotnet --version`.to_i}.0"
        includes = units.map { |u| "    <Compile Include=\"blocks/#{Languages.block_id(u)}.cs\" />" }
        File.write(File.join(work, 'WebsiteExamples.csproj'), <<~XML)
          <Project Sdk="Microsoft.NET.Sdk">
            <PropertyGroup>
              <OutputType>Exe</OutputType>
              <TargetFramework>#{framework}</TargetFramework>
              <ImplicitUsings>enable</ImplicitUsings>
              <Nullable>disable</Nullable>
              <EnableDefaultCompileItems>false</EnableDefaultCompileItems>
              <TreatWarningsAsErrors>false</TreatWarningsAsErrors>
              <NoWarn>$(NoWarn);CS1998;CS0168;CS0219;CS4014;CS8321</NoWarn>
            </PropertyGroup>
            <ItemGroup>
              <Compile Include="Program.cs" />
          #{includes.join("\n")}
              <ProjectReference Include="#{File.join(REPO_ROOT, 'mockserver-client-dotnet', 'src', 'MockServer.Client', 'MockServer.Client.csproj')}" />
            </ItemGroup>
          </Project>
        XML
      end

      # "/w/blocks/b_x.cs(12,5): error CS0103: The name 'x' ... [/w/WebsiteExamples.csproj]"
      def errors(output)
        output.lines.each_with_object({}) do |line, out|
          next unless line =~ /(b_\h+)\.cs\((\d+),(\d+)\): error (CS\d+: .*?)(?: \[[^\]]*\])?$/

          m = Regexp.last_match
          out[m[1]] ||= "#{m[1]}.cs(#{m[2]},#{m[3]}): #{m[4].strip}"
        end
      end

      def command(unit, work, _dir)
        ['dotnet', File.join(work, 'out', 'WebsiteExamples.dll'), Languages.block_id(unit)]
      end

      def env(_work)
        { 'DOTNET_CLI_TELEMETRY_OPTOUT' => '1', 'DOTNET_NOLOGO' => '1' }
      end

      def error(output)
        Languages.first_error_line(output, /^Unhandled exception\./)
      end
    end

    # -------------------------------------------------------------------
    # PHP: each block is a script that loads the client's Composer autoloader
    # (composer install in mockserver-client-php first).
    # -------------------------------------------------------------------
    module Php
      NAME = 'php'
      CLIENT = File.join(REPO_ROOT, 'mockserver-client-php')

      module_function

      def build(units, work)
        autoload = File.join(CLIENT, 'vendor', 'autoload.php')
        raise "run composer install in #{CLIENT} first" unless File.exist?(autoload)

        units.each do |u|
          code = Languages.code(u).sub(/\A\s*<\?php\s*/, '')
          code = fragment_prelude(code) + code if code =~ /\$client->/ && code !~ /\$client\s*=(?!=)/
          File.write(File.join(work, "#{Languages.block_id(u)}.php"), "<?php\nrequire '#{autoload}';\n#{code}\n")
        end
        {}
      end

      # A fragment continues an earlier example on its page, which imported
      # the client's classes and created $client.
      def fragment_prelude(code)
        classes = Dir[File.join(CLIENT, 'src', '*.php')].map { |f| File.basename(f, '.php') }.sort
        uses = classes.reject { |c| code =~ /^use\s+MockServer\\#{c}\s*;/ }
                      .map { |c| "use MockServer\\#{c};\n" }.join
        "#{uses}$client = new MockServerClient('localhost', 1080);\n"
      end

      # A block that loads vendor/autoload.php itself finds the client's.
      def stage(dir)
        File.symlink(File.join(CLIENT, 'vendor'), File.join(dir, 'vendor'))
      end

      def command(unit, work, _dir)
        ['php', '-d', 'display_errors=stderr', File.join(work, "#{Languages.block_id(unit)}.php")]
      end

      def env(_work)
        {}
      end

      def error(output)
        Languages.first_error_line(output, /Fatal error|Uncaught/)
      end
    end
  end
end
