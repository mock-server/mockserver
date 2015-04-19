# coding: utf-8
lib = File.expand_path('../lib', __FILE__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)

Gem::Specification.new do |spec|
  spec.name          = "MockServerWebSite"
  spec.version       = '1.0.0.pre'
  spec.authors       = ["James Bloom"]
  spec.email         = ["jamesdbloom@gmail.com"]
  spec.summary       = %q{MockServer web site}
  spec.description   = %q{A Jekyll based web site for MockServer}
  spec.homepage      = "http://www.mock-server.com"
  spec.license       = "Apache 2.0"

  spec.files         = `git ls-files -z`.split("\x0")
  spec.executables   = spec.files.grep(%r{^bin/}) { |f| File.basename(f) }
  spec.test_files    = spec.files.grep(%r{^(test|spec|features)/})
  spec.require_paths = ["lib"]

  # Build
  spec.add_development_dependency "bundler", ">= 1"
  spec.add_development_dependency "rake", ">= 10"

  # JS Compression
  spec.add_dependency 'juicer', '~> 1.2'
  spec.add_dependency 'cmdparse', '~> 2'
  spec.add_dependency 'yui-compressor', '~> 0.12'
  spec.add_dependency 'jslint', '~> 1.2'
  spec.add_dependency 'therubyrhino_jar', '~> 1.7.4'

  # Jekyll
  spec.add_dependency 'jekyll', '~> 2.5'
end