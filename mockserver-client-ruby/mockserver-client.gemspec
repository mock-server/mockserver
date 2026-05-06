# frozen_string_literal: true

lib = File.expand_path('lib', __dir__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)
require 'mockserver/version'

Gem::Specification.new do |spec|
  spec.name          = 'mockserver-client'
  spec.version       = MockServer::VERSION
  spec.authors       = ['James Bloom']
  spec.email         = ['jamesdbloom@gmail.com']
  spec.summary       = 'Ruby client for MockServer'
  spec.description   = 'Hand-written Ruby client for MockServer with full REST API, ' \
                        'fluent builder DSL, and WebSocket callback support.'
  spec.homepage      = 'https://www.mock-server.com'
  spec.license       = 'Apache-2.0'

  spec.required_ruby_version = '>= 3.0'

  spec.files         = Dir['lib/**/*.rb'] + %w[README.md Gemfile mockserver-client.gemspec]
  spec.require_paths = ['lib']

  spec.add_dependency 'logger', '>= 1.0'
  spec.add_dependency 'websocket-client-simple', '~> 0.8'

  spec.add_development_dependency 'rspec', '~> 3.12'
  spec.add_development_dependency 'rspec_junit_formatter', '~> 0.6'
  spec.add_development_dependency 'webmock', '~> 3.19'
end
