# HttpOverrideForwardedRequestOneOfResponseModifier


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**headers** | [**HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters**](HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters.md) |  | [optional] 
**cookies** | [**HttpOverrideForwardedRequestOneOfRequestModifierCookies**](HttpOverrideForwardedRequestOneOfRequestModifierCookies.md) |  | [optional] 

## Example

```python
from mockserver.models.http_override_forwarded_request_one_of_response_modifier import HttpOverrideForwardedRequestOneOfResponseModifier

# TODO update the JSON string below
json = "{}"
# create an instance of HttpOverrideForwardedRequestOneOfResponseModifier from a JSON string
http_override_forwarded_request_one_of_response_modifier_instance = HttpOverrideForwardedRequestOneOfResponseModifier.from_json(json)
# print the JSON string representation of the object
print(HttpOverrideForwardedRequestOneOfResponseModifier.to_json())

# convert the object into a dict
http_override_forwarded_request_one_of_response_modifier_dict = http_override_forwarded_request_one_of_response_modifier_instance.to_dict()
# create an instance of HttpOverrideForwardedRequestOneOfResponseModifier from a dict
http_override_forwarded_request_one_of_response_modifier_from_dict = HttpOverrideForwardedRequestOneOfResponseModifier.from_dict(http_override_forwarded_request_one_of_response_modifier_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


