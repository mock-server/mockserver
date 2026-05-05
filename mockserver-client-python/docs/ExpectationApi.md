# mockserver.ExpectationApi

All URIs are relative to *http://localhost:1080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**mockserver_expectation_put**](ExpectationApi.md#mockserver_expectation_put) | **PUT** /mockserver/expectation | create expectation
[**mockserver_openapi_put**](ExpectationApi.md#mockserver_openapi_put) | **PUT** /mockserver/openapi | create expectations from OpenAPI or Swagger


# **mockserver_expectation_put**
> List[Expectation] mockserver_expectation_put(expectations)

create expectation

### Example


```python
import mockserver
from mockserver.models.expectation import Expectation
from mockserver.models.expectations import Expectations
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
    api_instance = mockserver.ExpectationApi(api_client)
    expectations = mockserver.Expectations() # Expectations | expectation(s) to create

    try:
        # create expectation
        api_response = api_instance.mockserver_expectation_put(expectations)
        print("The response of ExpectationApi->mockserver_expectation_put:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling ExpectationApi->mockserver_expectation_put: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **expectations** | [**Expectations**](Expectations.md)| expectation(s) to create | 

### Return type

[**List[Expectation]**](Expectation.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**201** | expectations created |  -  |
**400** | incorrect request format |  -  |
**406** | invalid expectation |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **mockserver_openapi_put**
> List[Expectation] mockserver_openapi_put(open_api_expectations)

create expectations from OpenAPI or Swagger

### Example


```python
import mockserver
from mockserver.models.expectation import Expectation
from mockserver.models.open_api_expectations import OpenAPIExpectations
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
    api_instance = mockserver.ExpectationApi(api_client)
    open_api_expectations = mockserver.OpenAPIExpectations() # OpenAPIExpectations | expectation(s) to create

    try:
        # create expectations from OpenAPI or Swagger
        api_response = api_instance.mockserver_openapi_put(open_api_expectations)
        print("The response of ExpectationApi->mockserver_openapi_put:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling ExpectationApi->mockserver_openapi_put: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **open_api_expectations** | [**OpenAPIExpectations**](OpenAPIExpectations.md)| expectation(s) to create | 

### Return type

[**List[Expectation]**](Expectation.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**201** | expectations created |  -  |
**400** | incorrect request format |  -  |
**406** | invalid expectation |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

