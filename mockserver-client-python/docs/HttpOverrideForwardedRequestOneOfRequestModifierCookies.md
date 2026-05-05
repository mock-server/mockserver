# HttpOverrideForwardedRequestOneOfRequestModifierCookies


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**add** | [**KeyToValue**](KeyToValue.md) |  | [optional] 
**replace** | [**KeyToValue**](KeyToValue.md) |  | [optional] 
**remove** | **List[str]** |  | [optional] 

## Example

```python
from mockserver.models.http_override_forwarded_request_one_of_request_modifier_cookies import HttpOverrideForwardedRequestOneOfRequestModifierCookies

# TODO update the JSON string below
json = "{}"
# create an instance of HttpOverrideForwardedRequestOneOfRequestModifierCookies from a JSON string
http_override_forwarded_request_one_of_request_modifier_cookies_instance = HttpOverrideForwardedRequestOneOfRequestModifierCookies.from_json(json)
# print the JSON string representation of the object
print(HttpOverrideForwardedRequestOneOfRequestModifierCookies.to_json())

# convert the object into a dict
http_override_forwarded_request_one_of_request_modifier_cookies_dict = http_override_forwarded_request_one_of_request_modifier_cookies_instance.to_dict()
# create an instance of HttpOverrideForwardedRequestOneOfRequestModifierCookies from a dict
http_override_forwarded_request_one_of_request_modifier_cookies_from_dict = HttpOverrideForwardedRequestOneOfRequestModifierCookies.from_dict(http_override_forwarded_request_one_of_request_modifier_cookies_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


