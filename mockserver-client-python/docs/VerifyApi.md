# mockserver.VerifyApi

All URIs are relative to *http://localhost:1080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**mockserver_verify_put**](VerifyApi.md#mockserver_verify_put) | **PUT** /mockserver/verify | verify a request has been received a specific number of times
[**mockserver_verify_sequence_put**](VerifyApi.md#mockserver_verify_sequence_put) | **PUT** /mockserver/verifySequence | verify a sequence of request has been received in the specific order


# **mockserver_verify_put**
> mockserver_verify_put(verification)

verify a request has been received a specific number of times

### Example


```python
import mockserver
from mockserver.models.verification import Verification
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
    api_instance = mockserver.VerifyApi(api_client)
    verification = mockserver.Verification() # Verification | request matcher and the number of times to match

    try:
        # verify a request has been received a specific number of times
        api_instance.mockserver_verify_put(verification)
    except Exception as e:
        print("Exception when calling VerifyApi->mockserver_verify_put: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **verification** | [**Verification**](Verification.md)| request matcher and the number of times to match | 

### Return type

void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: text/plain

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**202** | matching request has been received specified number of times |  -  |
**400** | incorrect request format |  -  |
**406** | request has not been received specified numbers of times |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **mockserver_verify_sequence_put**
> mockserver_verify_sequence_put(verification_sequence)

verify a sequence of request has been received in the specific order

### Example


```python
import mockserver
from mockserver.models.verification_sequence import VerificationSequence
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
    api_instance = mockserver.VerifyApi(api_client)
    verification_sequence = mockserver.VerificationSequence() # VerificationSequence | the sequence of requests matchers

    try:
        # verify a sequence of request has been received in the specific order
        api_instance.mockserver_verify_sequence_put(verification_sequence)
    except Exception as e:
        print("Exception when calling VerifyApi->mockserver_verify_sequence_put: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **verification_sequence** | [**VerificationSequence**](VerificationSequence.md)| the sequence of requests matchers | 

### Return type

void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: text/plain

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**202** | request sequence has been received in specified order |  -  |
**400** | incorrect request format |  -  |
**406** | request sequence has not been received in specified order |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

