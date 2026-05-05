# HttpOverrideForwardedRequestOneOf


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**delay** | [**Delay**](Delay.md) |  | [optional] 
**request_override** | [**HttpRequest**](HttpRequest.md) |  | [optional] 
**request_modifier** | [**HttpOverrideForwardedRequestOneOfRequestModifier**](HttpOverrideForwardedRequestOneOfRequestModifier.md) |  | [optional] 
**response_override** | [**HttpResponse**](HttpResponse.md) |  | [optional] 
**response_modifier** | [**HttpOverrideForwardedRequestOneOfResponseModifier**](HttpOverrideForwardedRequestOneOfResponseModifier.md) |  | [optional] 

## Example

```python
from mockserver.models.http_override_forwarded_request_one_of import HttpOverrideForwardedRequestOneOf

# TODO update the JSON string below
json = "{}"
# create an instance of HttpOverrideForwardedRequestOneOf from a JSON string
http_override_forwarded_request_one_of_instance = HttpOverrideForwardedRequestOneOf.from_json(json)
# print the JSON string representation of the object
print(HttpOverrideForwardedRequestOneOf.to_json())

# convert the object into a dict
http_override_forwarded_request_one_of_dict = http_override_forwarded_request_one_of_instance.to_dict()
# create an instance of HttpOverrideForwardedRequestOneOf from a dict
http_override_forwarded_request_one_of_from_dict = HttpOverrideForwardedRequestOneOf.from_dict(http_override_forwarded_request_one_of_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


