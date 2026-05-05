# MockServer::VerifyApi

All URIs are relative to *http://localhost:1080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**verify_put**](VerifyApi.md#verify_put) | **PUT** /verify | verify a request has been received a specific number of times
[**verify_sequence_put**](VerifyApi.md#verify_sequence_put) | **PUT** /verifySequence | verify a sequence of request has been received in the specific order


# **verify_put**
> verify_put(verification)

verify a request has been received a specific number of times

### Example
```ruby
# load the gem
require 'mockserver-client'

api_instance = MockServer::VerifyApi.new
verification = MockServer::Verification.new # Verification | request matcher and the number of times to match

begin
  #verify a request has been received a specific number of times
  api_instance.verify_put(verification)
rescue MockServer::ApiError => e
  puts "Exception when calling VerifyApi->verify_put: #{e}"
end
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **verification** | [**Verification**](Verification.md)| request matcher and the number of times to match | 

### Return type

nil (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: text/plain



# **verify_sequence_put**
> verify_sequence_put(verification_sequence)

verify a sequence of request has been received in the specific order

### Example
```ruby
# load the gem
require 'mockserver-client'

api_instance = MockServer::VerifyApi.new
verification_sequence = MockServer::VerificationSequence.new # VerificationSequence | the sequence of requests matchers

begin
  #verify a sequence of request has been received in the specific order
  api_instance.verify_sequence_put(verification_sequence)
rescue MockServer::ApiError => e
  puts "Exception when calling VerifyApi->verify_sequence_put: #{e}"
end
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **verification_sequence** | [**VerificationSequence**](VerificationSequence.md)| the sequence of requests matchers | 

### Return type

nil (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: text/plain



