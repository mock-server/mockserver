# MockServer::ControlApi

All URIs are relative to *http://localhost:1080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**bind_put**](ControlApi.md#bind_put) | **PUT** /bind | bind additional listening ports
[**clear_put**](ControlApi.md#clear_put) | **PUT** /clear | clears expectations and recorded requests that match the request matcher
[**reset_put**](ControlApi.md#reset_put) | **PUT** /reset | clears all expectations and recorded requests
[**retrieve_put**](ControlApi.md#retrieve_put) | **PUT** /retrieve | retrieve recorded requests, active expectations, recorded expectations or log messages
[**status_put**](ControlApi.md#status_put) | **PUT** /status | return listening ports
[**stop_put**](ControlApi.md#stop_put) | **PUT** /stop | stop running process


# **bind_put**
> Ports bind_put(ports)

bind additional listening ports

only supported on Netty version

### Example
```ruby
# load the gem
require 'mockserver-client'

api_instance = MockServer::ControlApi.new
ports = MockServer::Ports.new # Ports | list of ports to bind to, where 0 indicates dynamically bind to any available port

begin
  #bind additional listening ports
  result = api_instance.bind_put(ports)
  p result
rescue MockServer::ApiError => e
  puts "Exception when calling ControlApi->bind_put: #{e}"
end
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ports** | [**Ports**](Ports.md)| list of ports to bind to, where 0 indicates dynamically bind to any available port | 

### Return type

[**Ports**](Ports.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json



# **clear_put**
> clear_put(opts)

clears expectations and recorded requests that match the request matcher

### Example
```ruby
# load the gem
require 'mockserver-client'

api_instance = MockServer::ControlApi.new
opts = {
  http_request: MockServer::HttpRequest.new # HttpRequest | request used to match expectations and recored requests to clear
}

begin
  #clears expectations and recorded requests that match the request matcher
  api_instance.clear_put(opts)
rescue MockServer::ApiError => e
  puts "Exception when calling ControlApi->clear_put: #{e}"
end
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **http_request** | [**HttpRequest**](HttpRequest.md)| request used to match expectations and recored requests to clear | [optional] 

### Return type

nil (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: Not defined



# **reset_put**
> reset_put

clears all expectations and recorded requests

### Example
```ruby
# load the gem
require 'mockserver-client'

api_instance = MockServer::ControlApi.new

begin
  #clears all expectations and recorded requests
  api_instance.reset_put
rescue MockServer::ApiError => e
  puts "Exception when calling ControlApi->reset_put: #{e}"
end
```

### Parameters
This endpoint does not need any parameter.

### Return type

nil (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined



# **retrieve_put**
> Object retrieve_put(opts)

retrieve recorded requests, active expectations, recorded expectations or log messages

### Example
```ruby
# load the gem
require 'mockserver-client'

api_instance = MockServer::ControlApi.new
opts = {
  format: 'format_example', # String | changes response format, default if not specificed is \"json\", supported values are \"java\", \"json\"
  type: 'type_example', # String | specifies the type of object that is retrieve, default if not specified is \"requests\", supported values are \"logs\", \"requests\", \"recorded_expectations\", \"active_expectations\"
  http_request: MockServer::HttpRequest.new # HttpRequest | request used to match which recorded requests, expectations or log messages to return, an empty body matches all requests, expectations or log messages
}

begin
  #retrieve recorded requests, active expectations, recorded expectations or log messages
  result = api_instance.retrieve_put(opts)
  p result
rescue MockServer::ApiError => e
  puts "Exception when calling ControlApi->retrieve_put: #{e}"
end
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **format** | **String**| changes response format, default if not specificed is \&quot;json\&quot;, supported values are \&quot;java\&quot;, \&quot;json\&quot; | [optional] 
 **type** | **String**| specifies the type of object that is retrieve, default if not specified is \&quot;requests\&quot;, supported values are \&quot;logs\&quot;, \&quot;requests\&quot;, \&quot;recorded_expectations\&quot;, \&quot;active_expectations\&quot; | [optional] 
 **http_request** | [**HttpRequest**](HttpRequest.md)| request used to match which recorded requests, expectations or log messages to return, an empty body matches all requests, expectations or log messages | [optional] 

### Return type

**Object**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json, application/java, text/plain



# **status_put**
> Ports status_put

return listening ports

### Example
```ruby
# load the gem
require 'mockserver-client'

api_instance = MockServer::ControlApi.new

begin
  #return listening ports
  result = api_instance.status_put
  p result
rescue MockServer::ApiError => e
  puts "Exception when calling ControlApi->status_put: #{e}"
end
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**Ports**](Ports.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json



# **stop_put**
> stop_put

stop running process

only supported on Netty version

### Example
```ruby
# load the gem
require 'mockserver-client'

api_instance = MockServer::ControlApi.new

begin
  #stop running process
  api_instance.stop_put
rescue MockServer::ApiError => e
  puts "Exception when calling ControlApi->stop_put: #{e}"
end
```

### Parameters
This endpoint does not need any parameter.

### Return type

nil (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined



