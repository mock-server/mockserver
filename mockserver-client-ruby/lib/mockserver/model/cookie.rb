# encoding: UTF-8
require 'hashie'
require_relative './array_of'

#
# A class to model cookies in payloads.
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::Model
  # Model for cookie
  class Cookie < Hashie::Dash
    include Hashie::Extensions::MethodAccess
    include Hashie::Extensions::IgnoreUndeclared
    include Hashie::Extensions::Coercion

    property :name, required: true
    property :value, required: true

    coerce_key :name, String
    coerce_key :value, String
  end

  # A collection that only stores cookies
  class Cookies < ArrayOf
    def child_class
      Cookie
    end
  end

  # DSL methods for cookie
  module DSL
    def cookie(key, value)
      Cookie.new(name: key, value: value)
    end
  end
end
