# encoding: UTF-8
require_relative './mockserver/version'
require_relative './mockserver/mock_server_client'
require_relative './mockserver/proxy_client'

# Setup serialization correctly with multi_json
require 'json/pure'

# To fix serialization bugs. See: http://prettystatemachine.blogspot.com/2010/09/typeerrors-in-tojson-make-me-briefly.html
class Fixnum
  def to_json(_)
    to_s
  end
end

require 'multi_json'
MultiJson.use(:json_pure)
