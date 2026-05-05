# BodyAnyOf6

string body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**string** | **str** |  | [optional] 
**content_type** | **str** |  | [optional] 
**sub_string** | **bool** |  | [optional] 

## Example

```python
from mockserver.models.body_any_of6 import BodyAnyOf6

# TODO update the JSON string below
json = "{}"
# create an instance of BodyAnyOf6 from a JSON string
body_any_of6_instance = BodyAnyOf6.from_json(json)
# print the JSON string representation of the object
print(BodyAnyOf6.to_json())

# convert the object into a dict
body_any_of6_dict = body_any_of6_instance.to_dict()
# create an instance of BodyAnyOf6 from a dict
body_any_of6_from_dict = BodyAnyOf6.from_dict(body_any_of6_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


