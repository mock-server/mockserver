# mockserver.ControlApi

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
```python
from __future__ import print_function
import time
import mockserver
from mockserver.rest import ApiException
from pprint import pprint

# create an instance of the API class
api_instance = mockserver.ControlApi()
ports = mockserver.Ports() # Ports | list of ports to bind to, where 0 indicates dynamically bind to any available port

try:
    # bind additional listening ports
    api_response = api_instance.bind_put(ports)
    pprint(api_response)
except ApiException as e:
    print("Exception when calling ControlApi->bind_put: %s\n" % e)
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

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **clear_put**
> clear_put(http_request=http_request)

clears expectations and recorded requests that match the request matcher

### Example
```python
from __future__ import print_function
import time
import mockserver
from mockserver.rest import ApiException
from pprint import pprint

# create an instance of the API class
api_instance = mockserver.ControlApi()
http_request = mockserver.HttpRequest() # HttpRequest | request used to match expectations and recored requests to clear (optional)

try:
    # clears expectations and recorded requests that match the request matcher
    api_instance.clear_put(http_request=http_request)
except ApiException as e:
    print("Exception when calling ControlApi->clear_put: %s\n" % e)
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **http_request** | [**HttpRequest**](HttpRequest.md)| request used to match expectations and recored requests to clear | [optional] 

### Return type

void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: Not defined

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **reset_put**
> reset_put()

clears all expectations and recorded requests

### Example
```python
from __future__ import print_function
import time
import mockserver
from mockserver.rest import ApiException
from pprint import pprint

# create an instance of the API class
api_instance = mockserver.ControlApi()

try:
    # clears all expectations and recorded requests
    api_instance.reset_put()
except ApiException as e:
    print("Exception when calling ControlApi->reset_put: %s\n" % e)
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

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **retrieve_put**
> object retrieve_put(format=format, type=type, http_request=http_request)

retrieve recorded requests, active expectations, recorded expectations or log messages

### Example
```python
from __future__ import print_function
import time
import mockserver
from mockserver.rest import ApiException
from pprint import pprint

# create an instance of the API class
api_instance = mockserver.ControlApi()
format = 'format_example' # str | changes response format, default if not specificed is \"json\", supported values are \"java\", \"json\" (optional)
type = 'type_example' # str | specifies the type of object that is retrieve, default if not specified is \"requests\", supported values are \"logs\", \"requests\", \"recorded_expectations\", \"active_expectations\" (optional)
http_request = mockserver.HttpRequest() # HttpRequest | request used to match which recorded requests, expectations or log messages to return, an empty body matches all requests, expectations or log messages (optional)

try:
    # retrieve recorded requests, active expectations, recorded expectations or log messages
    api_response = api_instance.retrieve_put(format=format, type=type, http_request=http_request)
    pprint(api_response)
except ApiException as e:
    print("Exception when calling ControlApi->retrieve_put: %s\n" % e)
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **format** | **str**| changes response format, default if not specificed is \&quot;json\&quot;, supported values are \&quot;java\&quot;, \&quot;json\&quot; | [optional] 
 **type** | **str**| specifies the type of object that is retrieve, default if not specified is \&quot;requests\&quot;, supported values are \&quot;logs\&quot;, \&quot;requests\&quot;, \&quot;recorded_expectations\&quot;, \&quot;active_expectations\&quot; | [optional] 
 **http_request** | [**HttpRequest**](HttpRequest.md)| request used to match which recorded requests, expectations or log messages to return, an empty body matches all requests, expectations or log messages | [optional] 

### Return type

**object**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json, application/java, text/plain

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **status_put**
> Ports status_put()

return listening ports

### Example
```python
from __future__ import print_function
import time
import mockserver
from mockserver.rest import ApiException
from pprint import pprint

# create an instance of the API class
api_instance = mockserver.ControlApi()

try:
    # return listening ports
    api_response = api_instance.status_put()
    pprint(api_response)
except ApiException as e:
    print("Exception when calling ControlApi->status_put: %s\n" % e)
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

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **stop_put**
> stop_put()

stop running process

only supported on Netty version

### Example
```python
from __future__ import print_function
import time
import mockserver
from mockserver.rest import ApiException
from pprint import pprint

# create an instance of the API class
api_instance = mockserver.ControlApi()

try:
    # stop running process
    api_instance.stop_put()
except ApiException as e:
    print("Exception when calling ControlApi->stop_put: %s\n" % e)
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

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

