# encoding: UTF-8
require 'thor'
require 'colorize'
require_relative './mockserver-client'

# CLI for this gem
# @author Nayyara Samuel(nayyara.samuel@opower.com)
#
module CLIHelpers
  include MockServer

  LOGGER = LoggingFactory::DEFAULT_FACTORY.log('MockServerClient')

  # Prints out the parameters passed to it
  # @param options [Hash] a hash of parameters
  def print_parameters(options)
    puts "\nRunning with parameters:".bold
    options.each { |k, v| puts "\t#{k}: #{v}".yellow }
    puts ''
  end

  # Create a mockserver client
  # @param options [Struct] with host and port set
  # @return [MockServerClient] the mockserver client with the host and port
  def mockserver_client(options)
    client = MockServerClient.new(options.host, options.port)
    client.logger = LOGGER
    client
  end

  # Create a proxy client
  # @param options [Struct] with host and port set
  # @return [ProxyClient] the proxy client with the host and port
  def proxy_client(options)
    client = ProxyClient.new(options.host, options.port)
    client.logger = LOGGER
    client
  end

  # Convert a hash to a struct
  # @param hash [Hash] a hash
  # @return [Struct] a struct constructed from the hash
  def to_struct(hash)
    hash = symbolize_keys(hash)
    Struct.new(*hash.keys).new(*hash.values)
  end

  # Process a block using options extracted into a struct
  # @param mockserver [Boolean] true to use mockserver, false to use proxy
  # @yieldparam [AbstractClient] a mockserver or a proxy client
  # @yieldparam [Struct] a struct created from options hash
  def execute_command(mockserver = false, data_required = false, error_msg = '--data option must be provided', &_)
    print_parameters(options)
    struct_options = to_struct({ data: nil }.merge(options))
    if data_required && !options['data']
      error(error_msg)
    else
      client = mockserver ? mockserver_client(struct_options) : proxy_client(struct_options)
      yield client, struct_options if block_given?
    end
  end

  # Prints an error message
  # @param message [String] an error message
  def error(message)
    puts message.red
  end

  # Read a file
  # @param file [String] a file to read
  def read_file(file)
    YAML.load_file(file)
  end
end

# CLI for mock server and proxy clients
class MockServerCLI < Thor
  include CLIHelpers
  include MockServer::UtilityMethods
  include MockServer::Model::DSL

  class_option :host, type: :string, aliases: '-h', required: true, default: 'localhost', desc: 'The host for the MockServer client.'
  class_option :port, type: :numeric, aliases: '-p', required: true, default: 8080, desc: 'The port for the MockServer client.'
  class_option :data, type: :string, aliases: '-d', desc: 'A JSON or YAML file containing the request payload.'

  desc 'retrieve', 'Retrieve the list of requests that have been made to the mock/proxy server.'

  def retrieve
    execute_command do |client, _|
      result = options.data ? client.retrieve(read_file(options.data)) : client.retrieve
      puts "RESULT:\n".bold + "#{result.to_json}".green
    end
  end

  desc 'register', 'Register an expectation with the mock server.'

  def register
    execute_command(true, true) do |client, options|
      payload = read_file(options.data)
      mock_expectation = expectation do |expectation|
        expectation.populate_from_payload(payload)
      end
      client.register(mock_expectation)
    end
  end

  desc 'dump_log', 'Dumps the matching request to the mock server logs.'
  option :java, type: :boolean, aliases: '-j', default: false, desc: 'A switch to turn Java format for logs on/off.'

  def dump_log
    execute_command do |client, options|
      options.data ? client.dump_log(read_file(options.data), options.java) : client.dump_log(nil, options.java)
    end
  end

  desc 'clear', 'Clears all stored mock request/responses from server.'

  def clear
    error_message = 'ERROR: No request provided. HINT: Use `clear` to selectively clear requests. Use `reset` to clear all.'
    execute_command(false, true, error_message) do |client, _|
      payload = read_file(options.data)
      client.clear(payload)
    end
  end

  desc 'reset', 'Resets the server clearing all data.'

  def reset
    execute_command do |client, _|
      client.reset
    end
  end

  desc 'verify', 'Verify that a request has been made the specified number of times to the server.'

  def verify
    execute_command(false, true) do |client, _|
      payload = read_file(options.data)
      mock_request = payload[HTTP_REQUEST]
      mock_times = payload[HTTP_TIMES]

      error 'No request found for verifying against' unless mock_request
      mock_times ? client.verify(mock_request, mock_times) : client.verify(mock_request)
    end
  end
end
