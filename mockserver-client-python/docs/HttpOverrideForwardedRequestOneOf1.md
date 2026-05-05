# HttpOverrideForwardedRequestOneOf1


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**delay** | [**Delay**](Delay.md) |  | [optional] 
**http_request** | [**HttpRequest**](HttpRequest.md) |  | [optional] 
**http_response** | [**HttpResponse**](HttpResponse.md) |  | [optional] 

## Example

```python
from mockserver.models.http_override_forwarded_request_one_of1 import HttpOverrideForwardedRequestOneOf1

# TODO update the JSON string below
json = "{}"
# create an instance of HttpOverrideForwardedRequestOneOf1 from a JSON string
http_override_forwarded_request_one_of1_instance = HttpOverrideForwardedRequestOneOf1.from_json(json)
# print the JSON string representation of the object
print(HttpOverrideForwardedRequestOneOf1.to_json())

# convert the object into a dict
http_override_forwarded_request_one_of1_dict = http_override_forwarded_request_one_of1_instance.to_dict()
# create an instance of HttpOverrideForwardedRequestOneOf1 from a dict
http_override_forwarded_request_one_of1_from_dict = HttpOverrideForwardedRequestOneOf1.from_dict(http_override_forwarded_request_one_of1_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


