# frozen_string_literal: true

RSpec.describe 'MockServer error hierarchy' do
  describe MockServer::Error do
    it 'inherits from StandardError' do
      expect(MockServer::Error).to be < StandardError
    end

    it 'can be raised and rescued' do
      expect { raise MockServer::Error, 'test' }.to raise_error(MockServer::Error, 'test')
    end
  end

  describe MockServer::ConnectionError do
    it 'inherits from MockServer::Error' do
      expect(MockServer::ConnectionError).to be < MockServer::Error
    end

    it 'can be rescued as MockServer::Error' do
      expect { raise MockServer::ConnectionError, 'no connection' }
        .to raise_error(MockServer::Error)
    end
  end

  describe MockServer::VerificationError do
    it 'inherits from MockServer::Error' do
      expect(MockServer::VerificationError).to be < MockServer::Error
    end

    it 'can be rescued as MockServer::Error' do
      expect { raise MockServer::VerificationError, 'verify failed' }
        .to raise_error(MockServer::Error)
    end
  end

  describe MockServer::CallbackError do
    it 'inherits from MockServer::Error' do
      expect(MockServer::CallbackError).to be < MockServer::Error
    end
  end

  describe MockServer::WebSocketError do
    it 'inherits from MockServer::Error' do
      expect(MockServer::WebSocketError).to be < MockServer::Error
    end
  end
end
