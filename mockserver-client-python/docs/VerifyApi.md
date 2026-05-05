# mockserver.VerifyApi

All URIs are relative to *http://localhost:1080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**verify_put**](VerifyApi.md#verify_put) | **PUT** /verify | verify a request has been received a specific number of times
[**verify_sequence_put**](VerifyApi.md#verify_sequence_put) | **PUT** /verifySequence | verify a sequence of request has been received in the specific order


# **verify_put**
> verify_put(verification)

verify a request has been received a specific number of times

### Example
```python
from __future__ import print_function
import time
import mockserver
from mockserver.rest import ApiException
from pprint import pprint

# create an instance of the API class
api_instance = mockserver.VerifyApi()
verification = mockserver.Verification() # Verification | request matcher and the number of times to match

try:
    # verify a request has been received a specific number of times
    api_instance.verify_put(verification)
except ApiException as e:
    print("Exception when calling VerifyApi->verify_put: %s\n" % e)
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

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **verify_sequence_put**
> verify_sequence_put(verification_sequence)

verify a sequence of request has been received in the specific order

### Example
```python
from __future__ import print_function
import time
import mockserver
from mockserver.rest import ApiException
from pprint import pprint

# create an instance of the API class
api_instance = mockserver.VerifyApi()
verification_sequence = mockserver.VerificationSequence() # VerificationSequence | the sequence of requests matchers

try:
    # verify a sequence of request has been received in the specific order
    api_instance.verify_sequence_put(verification_sequence)
except ApiException as e:
    print("Exception when calling VerifyApi->verify_sequence_put: %s\n" % e)
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

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

