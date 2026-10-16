# frozen_string_literal: true

require 'stringio'
require 'tmpdir'
require_relative 'check_website_examples'

# Unit tests for the website example check's parsing and normalisation; the
# check itself runs as a script (check_website_examples.rb).
RSpec.describe 'website example check' do
  describe WebsiteExamples::Extractor do
    let(:page) do
      <<~HTML
        <button id="button_one" class="accordion">first</button>
        <div class="panel">
            <button class="accordion inner">Ruby</button>
            <div class="panel">
                <pre class="prettyprint lang-ruby code"><code class="code">x = { 'a' =&gt; 1 }{% raw %}{% endraw %}</code></pre>
            </div>
            <button class="accordion inner">REST API</button>
            <div class="panel">
                <pre class="prettyprint code"><code class="code">curl -X PUT "http://localhost:1080/mockserver/clear"</code></pre>
            </div>
        </div>
        <pre class="prettyprint lang-ruby code"><code class="code">loose</code></pre>
      HTML
    end

    it 'groups each accordion tab under its outer accordion and keeps loose blocks apart' do
      path = File.join(Dir.mktmpdir, 'page.html')
      File.write(path, page)
      groups, loose = described_class.extract(path)
      expect(groups.map(&:title)).to eq(['first'])
      expect(groups.first.tabs['Ruby'].map(&:code)).to eq(["x = { 'a' => 1 }"])
      expect(groups.first.tabs['REST API'].length).to eq(1)
      expect(loose.map(&:code)).to eq(['loose'])
    end
  end

  describe 'language tabs outside any accordion' do
    it 'groups them, starting a new group when a tab title repeats' do
      tab = lambda do |title, code|
        %(<button class="accordion inner">#{title}</button>\n<div class="panel"><pre class="prettyprint lang-ruby code"><code class="code">#{code}</code></pre></div>\n)
      end
      path = File.join(Dir.mktmpdir, 'page.html')
      File.write(path, tab.call('Ruby', 'a') + tab.call('REST API', 'b') + tab.call('Ruby', 'c'))
      groups, loose = WebsiteExamples::Extractor.extract(path)
      expect(groups.map { |g| g.key.split('#').last }).to eq(%w[tabs-1 tabs-2])
      expect(groups.map { |g| g.tabs.keys }).to eq([['Ruby', 'REST API'], ['Ruby']])
      expect(loose).to be_empty
    end
  end

  describe WebsiteExamples::Rest do
    it 'reads method, path, query and the body as written' do
      call = described_class.parse_curl(%(curl -v -X PUT "http://localhost:1080/mockserver/retrieve?type=REQUESTS" -d '{"path": "/a"}'))
      expect(call).to include('method' => 'PUT', 'path' => '/mockserver/retrieve', 'query' => 'type=REQUESTS',
                              'body' => '{"path": "/a"}', 'quoting' => false)
    end

    it "flags a body whose single quote the shell would strip, and keeps the author's text" do
      call = described_class.parse_curl(%(curl -X PUT "http://localhost:1080/mockserver/expectation" -d '{"reasonPhrase": "I'm a teapot"}'))
      expect(call['body']).to eq(%({"reasonPhrase": "I'm a teapot"}))
      expect(call['quoting']).to be(true)
    end

    it "reads an escaped '\\'' as a single quote without flagging it" do
      call = described_class.parse_curl(%(curl -X PUT "http://localhost:1080/mockserver/expectation" -d '{"r": "I'\\''m"}'))
      expect(call['body']).to eq(%({"r": "I'm"}))
      expect(call['quoting']).to be(false)
    end

    it 'names a -d @file body after the file' do
      call = described_class.parse_curl(%(curl -X PUT "http://localhost:1080/mockserver/pact/verify" -d @contract.json))
      expect(call['body']).to eq('@file:contract.json')
    end

    it 'ignores calls outside the control plane and splits several commands' do
      calls = described_class.calls(<<~SH)
        # set up
        curl -X PUT "http://localhost:1080/mockserver/reset"
        curl http://localhost:1080/some/path
        curl -s http://localhost:1080/mockserver/loadScenario
      SH
      expect(calls.map { |c| [c['method'], c['path']] }).to eq([%w[PUT /mockserver/reset], %w[GET /mockserver/loadScenario]])
    end
  end

  describe WebsiteExamples::Normalise do
    def normalised(path, body, query = nil)
      described_class.call('method' => 'PUT', 'path' => path, 'query' => query, 'body' => JSON.generate(body))
    end

    it 'treats the array and object forms of headers and cookies as one' do
      array = { 'httpRequest' => { 'headers' => [{ 'name' => 'A', 'values' => ['b'] }],
                                   'cookies' => [{ 'name' => 's', 'value' => '1' }] } }
      object = { 'httpRequest' => { 'headers' => { 'A' => ['b'] }, 'cookies' => { 's' => '1' } } }
      expect(normalised('/mockserver/expectation', array)).to eq(normalised('/mockserver/expectation', object))
    end

    it 'keeps every value of a repeated name' do
      repeated = { 'httpRequest' => { 'headers' => [{ 'name' => 'A', 'values' => ['1'] }, { 'name' => 'A', 'values' => ['2'] }],
                                      'cookies' => [{ 'name' => 'c', 'value' => 'x' }, { 'name' => 'c', 'value' => 'y' }] } }
      once = { 'httpRequest' => { 'headers' => [{ 'name' => 'A', 'values' => ['2'] }], 'cookies' => { 'c' => 'y' } } }
      merged = { 'httpRequest' => { 'headers' => { 'A' => %w[1 2] } } }
      expect(normalised('/mockserver/expectation', repeated)).not_to eq(normalised('/mockserver/expectation', once))
      expect(normalised('/mockserver/expectation', { 'httpRequest' => repeated['httpRequest'].slice('headers') }))
        .to eq(normalised('/mockserver/expectation', merged))
    end

    it 'keeps keyMatchStyle apart from header names' do
      with = { 'httpRequest' => { 'headers' => { 'keyMatchStyle' => 'MATCHING_KEY', 'A' => ['b'] } } }
      as_header = { 'httpRequest' => { 'headers' => [{ 'name' => 'keyMatchStyle', 'values' => ['MATCHING_KEY'] },
                                                     { 'name' => 'A', 'values' => ['b'] }] } }
      expect(normalised('/mockserver/expectation', with)).not_to eq(normalised('/mockserver/expectation', as_header))
    end

    it 'applies the server defaults for times, timeToLive, priority, httpRequest and delay' do
      explicit = { 'httpRequest' => {}, 'httpResponse' => { 'delay' => { 'timeUnit' => 'MILLISECONDS', 'value' => 0,
                                                                         'template' => '$d' } },
                   'times' => { 'unlimited' => true }, 'timeToLive' => { 'unlimited' => true }, 'priority' => 0 }
      implicit = { 'httpResponse' => { 'delay' => { 'template' => '$d' } } }
      expect(normalised('/mockserver/expectation', explicit)).to eq(normalised('/mockserver/expectation', implicit))
    end

    it 'reads unlimited false beside a count as the count alone' do
      explicit = { 'times' => { 'remainingTimes' => 1, 'unlimited' => false },
                   'timeToLive' => { 'timeUnit' => 'SECONDS', 'timeToLive' => 60, 'unlimited' => false } }
      implicit = { 'times' => { 'remainingTimes' => 1 }, 'timeToLive' => { 'timeUnit' => 'SECONDS', 'timeToLive' => 60 } }
      expect(normalised('/mockserver/expectation', explicit)).to eq(normalised('/mockserver/expectation', implicit))
    end

    it 'still tells a limited times apart from an unlimited one' do
      once = { 'httpRequest' => { 'path' => '/a' }, 'times' => { 'remainingTimes' => 1, 'unlimited' => false } }
      always = { 'httpRequest' => { 'path' => '/a' } }
      expect(normalised('/mockserver/expectation', once)).not_to eq(normalised('/mockserver/expectation', always))
    end

    it 'splits an array of expectations into one entry each' do
      two = [{ 'httpRequest' => { 'path' => '/a' } }, { 'httpRequest' => { 'path' => '/b' } }]
      expect(normalised('/mockserver/expectation', two).length).to eq(2)
    end

    it 'reads a missing retrieve format as JSON but keeps other formats' do
      expect(normalised('/mockserver/retrieve', nil, 'type=REQUESTS&format=json'))
        .to eq(normalised('/mockserver/retrieve', nil, 'type=REQUESTS'))
      expect(normalised('/mockserver/retrieve', nil, 'type=REQUESTS&format=JAVA'))
        .not_to eq(normalised('/mockserver/retrieve', nil, 'type=REQUESTS'))
    end

    it 'compares a JSON response body by value, not by its whitespace' do
      spaced = { 'httpResponse' => { 'body' => '{"a": 1}' } }
      compact = { 'httpResponse' => { 'body' => '{"a":1}' } }
      expect(normalised('/mockserver/expectation', spaced)).to eq(normalised('/mockserver/expectation', compact))
    end

    it 'reads a verification bound of -1, or an atLeast of 0, as no bound' do
      explicit = { 'httpRequest' => { 'path' => '/a' }, 'times' => { 'atLeast' => 0, 'atMost' => -1 } }
      expect(normalised('/mockserver/verify', explicit)).to eq(normalised('/mockserver/verify', explicit.merge('times' => {})))
      bounded = explicit.merge('times' => { 'atLeast' => 2, 'atMost' => 0 })
      expect(normalised('/mockserver/verify', bounded)).not_to eq(normalised('/mockserver/verify', explicit))
    end

    it 'reads a response without a status code as 200 OK' do
      explicit = { 'httpResponse' => { 'statusCode' => 200, 'reasonPhrase' => 'OK', 'body' => 'b' } }
      expect(normalised('/mockserver/expectation', explicit))
        .to eq(normalised('/mockserver/expectation', { 'httpResponse' => { 'body' => 'b' } }))
      teapot = { 'httpResponse' => { 'statusCode' => 418, 'reasonPhrase' => 'OK' } }
      expect(normalised('/mockserver/expectation', teapot).first['body']['httpResponse']).to eq(teapot['httpResponse'])
    end

    it 'reads the default HTTP scheme, a clear of type ALL and an empty retrieve body as left out' do
      address = ->(a) { { 'httpForward' => { 'socketAddress' => a } } }
      expect(normalised('/mockserver/expectation', address.call('host' => 'h', 'scheme' => 'HTTP')))
        .to eq(normalised('/mockserver/expectation', address.call('host' => 'h')))
      expect(described_class.call('method' => 'PUT', 'path' => '/mockserver/clear', 'query' => 'type=ALL', 'body' => nil))
        .to eq(described_class.call('method' => 'PUT', 'path' => '/mockserver/clear', 'query' => nil, 'body' => nil))
      expect(normalised('/mockserver/retrieve', {}))
        .to eq(described_class.call('method' => 'PUT', 'path' => '/mockserver/retrieve', 'query' => nil, 'body' => nil))
    end

    it 'reads an override\'s httpRequest and httpResponse as its requestOverride and responseOverride' do
      old = { 'httpOverrideForwardedRequest' => { 'httpRequest' => { 'path' => '/b' }, 'httpResponse' => { 'body' => 'c' } } }
      new = { 'httpOverrideForwardedRequest' => { 'requestOverride' => { 'path' => '/b' }, 'responseOverride' => { 'body' => 'c' } } }
      expect(normalised('/mockserver/expectation', old)).to eq(normalised('/mockserver/expectation', new))
    end

    it 'reads {"name": x} and {"names": [x]} as the same load scenario' do
      expect(normalised('/mockserver/loadScenario/start', { 'name' => 'a' }))
        .to eq(normalised('/mockserver/loadScenario/start', { 'names' => ['a'] }))
    end
  end

  describe WebsiteExamples::Check do
    def page(ruby, rest, rest_title: 'REST API')
      path = File.join(Dir.mktmpdir, 'page.html')
      File.write(path, <<~HTML)
        <button id="button_x" class="accordion">x</button>
        <div class="panel">
            <button class="accordion inner">Ruby</button>
            <div class="panel"><pre class="prettyprint lang-ruby code"><code class="code">#{ruby}</code></pre></div>
            <button class="accordion inner">#{rest_title}</button>
            <div class="panel"><pre class="prettyprint code"><code class="code">#{rest}</code></pre></div>
        </div>
      HTML
      path
    end

    let(:ruby) { "require 'mockserver-client'\nMockServer::Client.new('localhost', 1080).reset" }
    let(:rest) { 'curl -v -X PUT "http://localhost:1080/mockserver/reset"' }
    let(:other_rest) { 'curl -v -X PUT "http://localhost:1080/mockserver/clear"' }

    def run_check(path, allow, argv = [])
      exit_code = nil
      output = capture_output { exit_code = described_class.run(argv, files: [path], allow: allow) }
      [exit_code, output]
    end

    def capture_output
      original = $stdout
      $stdout = StringIO.new
      yield
      $stdout.string
    ensure
      $stdout = original
    end

    def digest_of(path, allow = {})
      run_check(path, allow).last[/\[digest (\h{12})\]/, 1]
    end

    it 'passes a Ruby block that sends what the REST API tab sends' do
      expect(run_check(page(ruby, rest), {}).first).to eq(0)
    end

    it 'reads a tab whose title only contains REST API' do
      expect(run_check(page(ruby, rest, rest_title: 'Update Configuration (REST API)'), {}).first).to eq(0)
    end

    it 'reads a REST API tab written as a raw HTTP request' do
      expect(run_check(page(ruby, "PUT /mockserver/reset HTTP/1.1\n"), {}).first).to eq(0)
    end

    it 'fails a Ruby block that sends something else' do
      expect(run_check(page(ruby, other_rest), {}).first).to eq(1)
    end

    it 'passes a difference listed with its status and digest, and fails when the block then changes' do
      path = page(ruby, other_rest)
      key = "#{path}#button_x"
      digest = digest_of(path)
      expect(run_check(path, key => { 'status' => 'differs', 'reason' => 'r', 'digest' => digest }).first).to eq(0)
      expect(run_check(path, key => { 'status' => 'raised', 'reason' => 'r', 'digest' => digest }).first).to eq(1)
      code, output = run_check(path, key => { 'status' => 'differs', 'reason' => 'r', 'digest' => '000000000000' })
      expect([code, output]).to match([1, /DIGEST_CHANGED/])
    end

    it 'fails a block that raises after sending what the REST API tab sends' do
      code, output = run_check(page("#{ruby}\nraise 'late'", rest), {})
      expect([code, output]).to match([1, /RAISED .* late/])
    end

    it 'fails a Ruby block that is not compared unless it is allowlisted' do
      path = File.join(Dir.mktmpdir, 'page.html')
      File.write(path, %(<pre class="prettyprint lang-ruby code"><code class="code">puts 1</code></pre>\n))
      code, output = run_check(path, {})
      expect([code, output]).to match([1, /NOT_COMPARED/])
      key = output[/NOT_COMPARED\s+(\S+)/, 1]
      allow = { key => { 'status' => 'not_compared', 'reason' => 'r', 'digest' => digest_of(path) } }
      expect(run_check(path, allow).first).to eq(0)
    end

    it 'fails a REST API curl call to /mockserver/ it cannot read' do
      code, output = run_check(page(ruby, 'curl -X PUT "http://localhost:1080/mockserver/re set"'), {})
      expect([code, output]).to match([1, /REST_UNPARSEABLE/])
      code, output = run_check(page(ruby, 'curl -X PUT localhost:1080/mockserver/reset'), {})
      expect([code, output]).to match([1, /REST_UNPARSEABLE/])
    end

    it 'fails when the pages hold Ruby blocks the extractor did not find' do
      path = page(ruby, rest)
      File.write(path, File.read(path) + %(<pre class="prettyprint lang-ruby code">no code element\n))
      code, output = run_check(path, {})
      expect([code, output]).to match([1, /BLOCK_COUNT/])
    end

    it 'fails an allowlist entry that is no longer needed or no longer found' do
      path = page(ruby, rest)
      entry = { 'status' => 'differs', 'reason' => 'r', 'digest' => '000000000000' }
      expect(run_check(path, "#{path}#button_x" => entry).first).to eq(1)
      expect(run_check(path, 'gone#button_y' => entry).first).to eq(1)
    end

    it 'fails --only with a key it does not know' do
      code, output = run_check(page(ruby, rest), {}, ['--only', 'nope#button_z'])
      expect([code, output]).to match([1, /UNKNOWN_KEY/])
    end

    describe 'what a release rewrites' do
      # Split so scripts/release/go_module_major.py does not rewrite it.
      go = %w[github.com mock-server mockserver-monorepo mockserver-client-go].join('/')

      it 'hashes the release version, the API version and the Go module major as placeholders' do
        text = lambda do |version, api, major|
          %(MockServer::new("mockserver-#{version}") image:#{version} docs/#{api} "#{go}/v#{major}" #{go}/v#{major}/internal)
        end
        expect(described_class.release_neutral(text.call('9.0.0', '9.0.x', 9), %w[9.0.0 9.0.x]))
          .to eq(described_class.release_neutral(text.call('10.0.0', '10.0.x', 10), %w[10.0.0 10.0.x]))
      end

      it 'leaves other numbers alone' do
        text = "19.0.0 9.0.01 9.0.0.1 1.0.0 9.0.1 #{go}/v8.1.0 example.com/mockserver-client-go/v9"
        expect(described_class.release_neutral(text, %w[9.0.0 9.0.x])).to eq(text)
      end

      it 'keeps a loose block key when a release bumps the version in its code' do
        key = lambda do |version|
          allow(described_class).to receive(:release_versions).and_return([version, '9.0.x'])
          path = File.join(Dir.mktmpdir, 'page.html')
          File.write(path, %(<pre class="prettyprint lang-ruby code"><code class="code">puts 'mockserver-#{version}'</code></pre>\n))
          run_check(path, {}).last[/NOT_COMPARED\s+\S+#(loose-\h+)/, 1]
        end
        expect(key.call('9.0.0')).to eq(key.call('9.0.1'))
      end
    end

    it 'rejects an allowlist entry without a reason or a digest' do
      path = File.join(Dir.mktmpdir, 'allow.yml')
      File.write(path, "k:\n  status: differs\n  digest: 0123456789ab\n")
      expect { described_class.load_allowlist(path) }.to raise_error(ArgumentError, /reason/)
      File.write(path, "k:\n  status: differs\n  reason: r\n")
      expect { described_class.load_allowlist(path) }.to raise_error(ArgumentError, /digest/)
    end
  end

  describe WebsiteExamples::Runner do
    before(:all) { described_class.preload }

    it 'records what an example sends without touching the network' do
      result = described_class.run(<<~RUBY)
        require 'mockserver-client'
        client = MockServer::Client.new('localhost', 1080)
        client.when(MockServer::HttpRequest.new(path: '/a')).respond(MockServer::HttpResponse.new(body: 'b'))
        Net::HTTP.get(URI('http://example.com/'))
      RUBY
      expect(result['calls'].map { |c| c['path'] }).to eq(['/mockserver/expectation', '/'])
      expect(JSON.parse(result['calls'].first['body'])).to eq([{ 'httpRequest' => { 'path' => '/a' },
                                                                 'httpResponse' => { 'body' => 'b' } }])
    end

    it 'runs an example in a temporary directory it removes, so no file the example writes is left' do
      name = 'website-example-leftover.txt'
      result = described_class.run(<<~RUBY)
        File.write('#{name}', 'x')
        Net::HTTP.get(URI("http://example.com/?" + URI.encode_www_form(dir: Dir.pwd)))
      RUBY
      dir = URI.decode_www_form(result['calls'].last['query']).to_h['dir']
      expect(result['error']).to be_nil
      expect(File.exist?(File.join(WebsiteExamples::CLIENT_ROOT, name))).to be(false)
      expect(File.exist?(name)).to be(false)
      expect(File.exist?(dir)).to be(false)
    end

    it 'reports an exception the example raises' do
      expect(described_class.run("raise ArgumentError, 'boom'")['error']).to eq('ArgumentError: boom')
    end
  end
end
