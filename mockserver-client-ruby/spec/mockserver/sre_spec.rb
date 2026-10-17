# frozen_string_literal: true

RSpec.describe MockServer::Client do
  let(:host) { 'localhost' }
  let(:port) { 1080 }
  let(:base_url) { "http://#{host}:#{port}" }
  let(:client) { MockServer::Client.new(host, port) }

  after { client.close }

  # -------------------------------------------------------------------
  # verify_slo (PUT /mockserver/verifySLO)
  # -------------------------------------------------------------------
  describe '#verify_slo' do
    let(:criteria) do
      {
        'name' => 'checkout-latency',
        'window' => 'PT5M',
        'minimumSampleCount' => 100,
        'objectives' => [
          { 'sli' => 'p99_latency_ms', 'comparator' => 'LESS_THAN', 'threshold' => 250 }
        ]
      }
    end

    it 'sends PUT to /mockserver/verifySLO and returns the verdict on 200 PASS' do
      verdict = { 'name' => 'checkout-latency', 'result' => 'PASS', 'sampleCount' => 120 }
      stub_request(:put, "#{base_url}/mockserver/verifySLO")
        .to_return(status: 200, body: JSON.generate(verdict))

      result = client.verify_slo(criteria)

      expect(result).to eq(verdict)
      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/verifySLO")
        .with { |r|
          parsed = JSON.parse(r.body)
          parsed['name'] == 'checkout-latency' &&
            parsed['objectives'][0]['sli'] == 'p99_latency_ms' &&
            parsed['objectives'][0]['comparator'] == 'LESS_THAN'
        }
    end

    it 'returns the verdict on 200 INCONCLUSIVE without raising' do
      verdict = { 'result' => 'INCONCLUSIVE' }
      stub_request(:put, "#{base_url}/mockserver/verifySLO")
        .to_return(status: 200, body: JSON.generate(verdict))

      expect(client.verify_slo(criteria)).to eq(verdict)
    end

    it 'raises VerificationError on 406 FAIL' do
      body = JSON.generate({ 'result' => 'FAIL' })
      stub_request(:put, "#{base_url}/mockserver/verifySLO")
        .to_return(status: 406, body: body)

      expect { client.verify_slo(criteria) }
        .to raise_error(MockServer::VerificationError, /FAIL/)
    end

    it 'raises Error on 403 (SLO tracking disabled)' do
      stub_request(:put, "#{base_url}/mockserver/verifySLO")
        .to_return(status: 403, body: 'SLO tracking not enabled')

      expect { client.verify_slo(criteria) }
        .to raise_error(MockServer::Error, /sloTrackingEnabled=true/)
    end

    it 'raises Error naming the criteria on 400 (malformed criteria)' do
      stub_request(:put, "#{base_url}/mockserver/verifySLO")
        .to_return(status: 400, body: 'objectives must be an array')

      expect { client.verify_slo(criteria) }
        .to raise_error(MockServer::Error, /Invalid SLO criteria/)
    end

    it 'accepts an object responding to to_h' do
      to_h_double = double('criteria', to_h: criteria)
      stub_request(:put, "#{base_url}/mockserver/verifySLO")
        .to_return(status: 200, body: JSON.generate({ 'result' => 'PASS' }))

      client.verify_slo(to_h_double)

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/verifySLO")
        .with { |r| JSON.parse(r.body)['name'] == 'checkout-latency' }
    end
  end

  # -------------------------------------------------------------------
  # start_chaos_experiment (PUT /mockserver/chaosExperiment)
  # -------------------------------------------------------------------
  describe '#start_chaos_experiment' do
    let(:experiment) do
      {
        'name' => 'payments-brownout',
        'loop' => false,
        'stages' => [
          { 'durationMillis' => 60_000, 'profiles' => { 'payments.svc' => 'LATENCY' } }
        ]
      }
    end

    it 'sends PUT to /mockserver/chaosExperiment and returns the started status' do
      started = { 'status' => 'started', 'name' => 'payments-brownout' }
      stub_request(:put, "#{base_url}/mockserver/chaosExperiment")
        .to_return(status: 200, body: JSON.generate(started))

      result = client.start_chaos_experiment(experiment)

      expect(result).to eq(started)
      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/chaosExperiment")
        .with { |r|
          parsed = JSON.parse(r.body)
          parsed['name'] == 'payments-brownout' &&
            parsed['stages'][0]['durationMillis'] == 60_000 &&
            parsed['stages'][0]['profiles']['payments.svc'] == 'LATENCY'
        }
    end

    it 'raises Error on 403 (control-plane authorization denied)' do
      stub_request(:put, "#{base_url}/mockserver/chaosExperiment")
        .to_return(status: 403, body: 'Forbidden for control plane')

      expect { client.start_chaos_experiment(experiment) }
        .to raise_error(MockServer::Error, /control-plane authorization denied: Forbidden for control plane/)
    end

    it 'raises Error on 400 (invalid definition)' do
      stub_request(:put, "#{base_url}/mockserver/chaosExperiment")
        .to_return(status: 400, body: 'invalid')

      expect { client.start_chaos_experiment(experiment) }
        .to raise_error(MockServer::Error, /status=400/)
    end

    it 'accepts an object responding to to_h' do
      to_h_double = double('experiment', to_h: experiment)
      stub_request(:put, "#{base_url}/mockserver/chaosExperiment")
        .to_return(status: 200, body: JSON.generate({ 'status' => 'started' }))

      client.start_chaos_experiment(to_h_double)

      expect(WebMock).to have_requested(:put, "#{base_url}/mockserver/chaosExperiment")
        .with { |r| JSON.parse(r.body)['name'] == 'payments-brownout' }
    end
  end
end
