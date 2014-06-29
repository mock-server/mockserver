# encoding: UTF-8
require_relative './request'
require_relative './response'
require_relative './forward'
require_relative './times'
require_relative '../utility_methods'
require_relative './mock'
#
# A class to model an expectation sent to the Mockserver instance.
# See http://www.mock-server.com/#create-expectations for details.
#
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer
  HTTP_REQUEST = 'httpRequest'
  HTTP_RESPONSE = 'httpResponse'
  HTTP_FORWARD = 'httpForward'
  HTTP_TIMES = 'times'

  # Models
  module Model
    # Expectation model
    class Expectation < Mock
    end

    # DSL method for creating expectation
    module DSL
      def expectation(&_)
        expectation = Expectation.new
        yield expectation if block_given?
        expectation
      end
    end
  end
end
