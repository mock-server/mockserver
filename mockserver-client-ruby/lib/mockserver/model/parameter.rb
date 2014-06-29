# encoding: UTF-8
require 'hashie'
require_relative './array_of'

#
# A class to model parameters in payloads.
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::Model
  # A class that only stores strings
  class Strings < ArrayOf
    def child_class
      String
    end
  end

  # Model for parameter
  class Parameter < Hashie::Dash
    include Hashie::Extensions::MethodAccess
    include Hashie::Extensions::IgnoreUndeclared
    include Hashie::Extensions::Coercion

    property :name, required: true
    property :values, default: Strings.new([])

    coerce_key :name, String
    coerce_key :values, Strings
  end

  # A collection that only stores parameters
  class Parameters < ArrayOf
    def child_class
      Parameter
    end
  end

  # DSL methods for parameter
  module DSL
    def parameter(key, *value)
      Parameter.new(name: key, values: value)
    end

    alias_method :cookie, :parameter
    alias_method :header, :parameter
  end
end
