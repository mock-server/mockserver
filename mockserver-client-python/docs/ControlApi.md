# mockserver.ControlApi

All URIs are relative to *http://localhost:1080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**mockserver_bind_put**](ControlApi.md#mockserver_bind_put) | **PUT** /mockserver/bind | bind additional listening ports
[**mockserver_clear_put**](ControlApi.md#mockserver_clear_put) | **PUT** /mockserver/clear | clears expectations and recorded requests that match the request matcher
[**mockserver_reset_put**](ControlApi.md#mockserver_reset_put) | **PUT** /mockserver/reset | clears all expectations and recorded requests
[**mockserver_retrieve_put**](ControlApi.md#mockserver_retrieve_put) | **PUT** /mockserver/retrieve | retrieve recorded requests, active expectations, recorded expectations or log messages
[**mockserver_status_put**](ControlApi.md#mockserver_status_put) | **PUT** /mockserver/status | return listening ports
[**mockserver_stop_put**](ControlApi.md#mockserver_stop_put) | **PUT** /mockserver/stop | stop running process


# **mockserver_bind_put**
> Ports mockserver_bind_put(ports)

bind additional listening ports

only supported on Netty version

### Example


```python
import mockserver
from mockserver.models.ports import Ports
from mockserver.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost:1080
# See configuration.py for a list of all supported configuration parameters.
configuration = mockserver.Configuration(
    host = "http://localhost:1080"
)


# Enter a context with an instance of the API client
with mockserver.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = mockserver.ControlApi(api_client)
    ports = mockserver.Ports() # Ports | list of ports to bind to, where 0 indicates dynamically bind to any available port

    try:
        # bind additional listening ports
        api_response = api_instance.mockserver_bind_put(ports)
        print("The response of ControlApi->mockserver_bind_put:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling ControlApi->mockserver_bind_put: %s\n" % e)
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

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | listening on additional requested ports, note: the response ony contains ports added for the request, to list all ports use /status |  -  |
**400** | incorrect request format |  -  |
**406** | unable to bind to ports (i.e. already bound or JVM process doesn&#39;t have permission) |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **mockserver_clear_put**
> mockserver_clear_put(type=type, mockserver_clear_put_request=mockserver_clear_put_request)

clears expectations and recorded requests that match the request matcher

### Example


```python
import mockserver
from mockserver.models.mockserver_clear_put_request import MockserverClearPutRequest
from mockserver.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost:1080
# See configuration.py for a list of all supported configuration parameters.
configuration = mockserver.Configuration(
    host = "http://localhost:1080"
)


# Enter a context with an instance of the API client
with mockserver.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = mockserver.ControlApi(api_client)
    type = all # str | specifies the type of information to clear, default if not specified is \"all\", supported values are \"all\", \"log\", \"expectations\" (optional) (default to all)
    mockserver_clear_put_request = mockserver.MockserverClearPutRequest() # MockserverClearPutRequest | request used to match expectations and record requests to clear (optional)

    try:
        # clears expectations and recorded requests that match the request matcher
        api_instance.mockserver_clear_put(type=type, mockserver_clear_put_request=mockserver_clear_put_request)
    except Exception as e:
        print("Exception when calling ControlApi->mockserver_clear_put: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **type** | **str**| specifies the type of information to clear, default if not specified is \&quot;all\&quot;, supported values are \&quot;all\&quot;, \&quot;log\&quot;, \&quot;expectations\&quot; | [optional] [default to all]
 **mockserver_clear_put_request** | [**MockserverClearPutRequest**](MockserverClearPutRequest.md)| request used to match expectations and record requests to clear | [optional] 

### Return type

void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: Not defined

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | expectations and recorded requests cleared |  -  |
**400** | incorrect request format |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **mockserver_reset_put**
> mockserver_reset_put()

clears all expectations and recorded requests

### Example


```python
import mockserver
from mockserver.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost:1080
# See configuration.py for a list of all supported configuration parameters.
configuration = mockserver.Configuration(
    host = "http://localhost:1080"
)


# Enter a context with an instance of the API client
with mockserver.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = mockserver.ControlApi(api_client)

    try:
        # clears all expectations and recorded requests
        api_instance.mockserver_reset_put()
    except Exception as e:
        print("Exception when calling ControlApi->mockserver_reset_put: %s\n" % e)
```



### Parameters

This endpoint does not need any parameter.

### Return type

void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | expectations and recorded requests cleared |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **mockserver_retrieve_put**
> MockserverRetrievePut200Response mockserver_retrieve_put(format=format, type=type, request_definition=request_definition)

retrieve recorded requests, active expectations, recorded expectations or log messages

### Example


```python
import mockserver
from mockserver.models.mockserver_retrieve_put200_response import MockserverRetrievePut200Response
from mockserver.models.request_definition import RequestDefinition
from mockserver.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost:1080
# See configuration.py for a list of all supported configuration parameters.
configuration = mockserver.Configuration(
    host = "http://localhost:1080"
)


# Enter a context with an instance of the API client
with mockserver.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = mockserver.ControlApi(api_client)
    format = 'format_example' # str | changes response format, default if not specified is \"json\", supported values are \"java\", \"json\", \"log_entries\" (optional)
    type = 'type_example' # str | specifies the type of object that is retrieve, default if not specified is \"requests\", supported values are \"logs\", \"requests\", \"recorded_expectations\", \"active_expectations\" (optional)
    request_definition = mockserver.RequestDefinition() # RequestDefinition | request used to match which recorded requests, expectations or log messages to return, an empty body matches all requests, expectations or log messages (optional)

    try:
        # retrieve recorded requests, active expectations, recorded expectations or log messages
        api_response = api_instance.mockserver_retrieve_put(format=format, type=type, request_definition=request_definition)
        print("The response of ControlApi->mockserver_retrieve_put:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling ControlApi->mockserver_retrieve_put: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **format** | **str**| changes response format, default if not specified is \&quot;json\&quot;, supported values are \&quot;java\&quot;, \&quot;json\&quot;, \&quot;log_entries\&quot; | [optional] 
 **type** | **str**| specifies the type of object that is retrieve, default if not specified is \&quot;requests\&quot;, supported values are \&quot;logs\&quot;, \&quot;requests\&quot;, \&quot;recorded_expectations\&quot;, \&quot;active_expectations\&quot; | [optional] 
 **request_definition** | [**RequestDefinition**](RequestDefinition.md)| request used to match which recorded requests, expectations or log messages to return, an empty body matches all requests, expectations or log messages | [optional] 

### Return type

[**MockserverRetrievePut200Response**](MockserverRetrievePut200Response.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json, application/java, text/plain

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | recorded requests or active expectations returned |  -  |
**400** | incorrect request format |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **mockserver_status_put**
> Ports mockserver_status_put()

return listening ports

### Example


```python
import mockserver
from mockserver.models.ports import Ports
from mockserver.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost:1080
# See configuration.py for a list of all supported configuration parameters.
configuration = mockserver.Configuration(
    host = "http://localhost:1080"
)


# Enter a context with an instance of the API client
with mockserver.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = mockserver.ControlApi(api_client)

    try:
        # return listening ports
        api_response = api_instance.mockserver_status_put()
        print("The response of ControlApi->mockserver_status_put:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling ControlApi->mockserver_status_put: %s\n" % e)
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

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | MockServer is running and listening on the listed ports |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **mockserver_stop_put**
> mockserver_stop_put()

stop running process

only supported on Netty version

### Example


```python
import mockserver
from mockserver.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost:1080
# See configuration.py for a list of all supported configuration parameters.
configuration = mockserver.Configuration(
    host = "http://localhost:1080"
)


# Enter a context with an instance of the API client
with mockserver.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = mockserver.ControlApi(api_client)

    try:
        # stop running process
        api_instance.mockserver_stop_put()
    except Exception as e:
        print("Exception when calling ControlApi->mockserver_stop_put: %s\n" % e)
```



### Parameters

This endpoint does not need any parameter.

### Return type

void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | MockServer process is stopping |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

