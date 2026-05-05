# BodyAnyOf7

xml body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**xml** | **str** |  | [optional] 
**content_type** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body_any_of7 import BodyAnyOf7

# TODO update the JSON string below
json = "{}"
# create an instance of BodyAnyOf7 from a JSON string
body_any_of7_instance = BodyAnyOf7.from_json(json)
# print the JSON string representation of the object
print(BodyAnyOf7.to_json())

# convert the object into a dict
body_any_of7_dict = body_any_of7_instance.to_dict()
# create an instance of BodyAnyOf7 from a dict
body_any_of7_from_dict = BodyAnyOf7.from_dict(body_any_of7_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


