require 'spec_helper'

RSpec.describe 'MockServer::Model::DSL' do
  let(:header) { MockServer::Model::Header }
  let(:headers) do
    [
      header.new(name: 'User-Agent', values: ['curl/7.22.0 (x86_64-pc-linux-gnu)']),
      header.new(name: 'Host', values: ['localhost:2000']),
      header.new(name: 'Accept', values: ['*/*']),
      header.new(name: 'Content-Length', values: ['26']),
      header.new(name: 'Content-Type', values: ['application/x-www-form-urlencoded'])
    ]
  end

  describe '#request_from_json' do
    let(:body_content) { 'Hello this is a message' }
    let(:request_json) do
      {
        "method"=>"POST", "path"=>"/message",
        "headers"=>[{ "name"=>"User-Agent", "values"=>["curl/7.22.0 (x86_64-pc-linux-gnu)"] },
        {"name"=>"Host", "values"=>["localhost:2000"]}, {"name"=>"Accept", "values"=>["*/*"]},
        {"name"=>"Content-Length", "values"=>["26"]},
        {"name"=>"Content-Type", "values"=>["application/x-www-form-urlencoded"]}],
        "keepAlive"=>true, "secure"=>false
      }
    end

    it 'correctly builds the request with no body' do
      request = request_from_json(request_json)

      expect(request.headers).to eq headers
      expect(request.body).to eq nil
    end

    it 'correctly builds the request with a body' do
      request_with_body = request_json.merge('body' => body_content)
      request = request_from_json(request_with_body)

      expect(request.headers).to eq headers
      expect(request.body.type.to_s).to eq 'STRING'
      expect(request.body.value).to eq body_content
    end
  end
end
