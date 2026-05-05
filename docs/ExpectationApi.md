# MockServer::ExpectationApi

All URIs are relative to *http://localhost:1080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**expectation_put**](ExpectationApi.md#expectation_put) | **PUT** /expectation | create expectation


# **expectation_put**
> expectation_put(expectations)

create expectation

### Example
```ruby
# load the gem
require 'mockserver-client'

api_instance = MockServer::ExpectationApi.new
expectations = nil # Array<Expectations> | expectation to create

begin
  #create expectation
  api_instance.expectation_put(expectations)
rescue MockServer::ApiError => e
  puts "Exception when calling ExpectationApi->expectation_put: #{e}"
end
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **expectations** | [**Array&lt;Expectations&gt;**](Array.md)| expectation to create | 

### Return type

nil (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: Not defined



