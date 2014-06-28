# encoding: UTF-8
require 'simplecov'

SimpleCov.start do
  add_filter 'spec/'
  add_filter 'rdoc/'
  add_filter 'results/'
  add_filter 'coverage/'
end

require 'rspec'
require 'webmock/rspec'
require_relative '../lib/mockserver-client'

# Helper methods used by tests
module HelperMethods
  # A class to allow easy reference of files with a given base path
  class FileAccessor
    attr_accessor :base_path

    # Options should have a list of parts which will be appended together to give the base path
    def initialize(*options)
      @base_path = File.join(options.map { |f| f.to_s })
    end

    # Access specific file in the base path
    def [](file_name)
      File.join(@base_path, file_name)
    end

    # Read the given file
    def read(file_name)
      YAML.load_file(File.join(@base_path, file_name))
    end

    # Delete all files in the directory
    def delete_files(pattern = '*.log')
      FileUtils.rm_rf Dir.glob(File.join(base_path, pattern))
    end
  end

  # Reparse hash from json to make keys strings and ensure consistency of keys for tests
  def to_camelized_hash(hash)
    JSON.parse(camelized_hash(hash).to_json)
  end

  FIXTURES = FileAccessor.new(:spec, :fixtures)
end

RSpec.configure do |config|
  include HelperMethods
  include WebMock::API
  include MockServer
  include MockServer::UtilityMethods
  include MockServer::Model::DSL

  # Only accept expect syntax do not allow old should syntax
  config.expect_with :rspec do |c|
    c.syntax = :expect
  end
end
