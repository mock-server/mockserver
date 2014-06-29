# encoding: UTF-8
require 'active_support/inflector'
require 'json'

#
# A module that has common utility methods used by this project
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::UtilityMethods
  # Does the following filter/transform operations on a hash
  # - exclude null or empty valued keys from the hash
  # - camelize the keys of the hash
  # @param obj [Object] an object which will be used to create the hash. Must support :to_hash method
  # @return [Hash] the transformed hash
  # rubocop:disable Style/MethodLength
  # rubocop:disable Style/CyclomaticComplexity
  def camelized_hash(obj)
    obj = obj && obj.respond_to?(:to_hash) ? obj.to_hash : obj

    if obj.is_a?(Hash)
      obj.each_with_object({}) do |(k, v), acc|
        is_empty         = v.nil? || (v.respond_to?(:empty?) ? v.empty? : false)
        acc[camelize(k)] = camelized_hash(v) unless is_empty
      end
    elsif obj.respond_to?(:map)
      obj.map { |element| camelized_hash(element) }
    else
      obj
    end
  end

  # Converts keys to symbols
  # @param hash [Hash] a hash
  # @return [Hash] a copy of the hash where keys are symbols
  def symbolize_keys(hash)
    if hash.is_a?(Hash)
      Hash[hash.map { |k, v| [k.to_s.underscore.to_sym, symbolize_keys(v)] }]
    elsif hash.respond_to?(:map)
      hash.map { |obj| symbolize_keys(obj) }
    else
      hash
    end
  end

  # @param str [Object] an object to camelize the string representation of
  # @return [String] the string converted to camelcase with first letter in lower case
  def camelize(str)
    str.to_s.camelize(:lower)
  end

  # Parse string response into JSON
  # @param response [Response] from RestClient response
  # @return [Hash] the parsed response or the object unmodified if parsing is not possible
  def parse_string_to_json(response)
    JSON.parse(response)
  rescue JSON::ParserError
    response
  end
end
