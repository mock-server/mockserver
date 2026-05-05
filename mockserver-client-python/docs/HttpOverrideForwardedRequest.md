# HttpOverrideForwardedRequest

override forwarded request

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**delay** | [**Delay**](Delay.md) |  | [optional] 
**request_override** | [**HttpRequest**](HttpRequest.md) |  | [optional] 
**request_modifier** | [**HttpOverrideForwardedRequestOneOfRequestModifier**](HttpOverrideForwardedRequestOneOfRequestModifier.md) |  | [optional] 
**response_override** | [**HttpResponse**](HttpResponse.md) |  | [optional] 
**response_modifier** | [**HttpOverrideForwardedRequestOneOfResponseModifier**](HttpOverrideForwardedRequestOneOfResponseModifier.md) |  | [optional] 
**http_request** | [**HttpRequest**](HttpRequest.md) |  | [optional] 
**http_response** | [**HttpResponse**](HttpResponse.md) |  | [optional] 

## Example

```python
from mockserver.models.http_override_forwarded_request import HttpOverrideForwardedRequest

# TODO update the JSON string below
json = "{}"
# create an instance of HttpOverrideForwardedRequest from a JSON string
http_override_forwarded_request_instance = HttpOverrideForwardedRequest.from_json(json)
# print the JSON string representation of the object
print(HttpOverrideForwardedRequest.to_json())

# convert the object into a dict
http_override_forwarded_request_dict = http_override_forwarded_request_instance.to_dict()
# create an instance of HttpOverrideForwardedRequest from a dict
http_override_forwarded_request_from_dict = HttpOverrideForwardedRequest.from_dict(http_override_forwarded_request_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


