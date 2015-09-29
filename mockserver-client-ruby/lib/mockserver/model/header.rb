# encoding: UTF-8
require 'hashie'
require_relative './array_of'

#
# A class to model headers in payloads.
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::Model
  # A class that only stores strings
  class Strings < ArrayOf
    def child_class
      String
    end
  end

  # Model for header
  class Header < Hashie::Dash
    include Hashie::Extensions::MethodAccess
    include Hashie::Extensions::IgnoreUndeclared
    include Hashie::Extensions::Coercion

    property :name, required: true
    property :values, default: Strings.new([])

    coerce_key :name, String
    coerce_key :values, Strings
  end

  # A collection that only stores headers
  class Headers < ArrayOf
    def child_class
      Header
    end
  end

  # DSL methods for header
  module DSL
    def header(key, *value)
      Header.new(name: key, values: value)
    end
  end
end
