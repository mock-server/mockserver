# BodyAnyOf1

json body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**var_json** | **str** |  | [optional] 
**content_type** | **str** |  | [optional] 
**match_type** | **str** |  | [optional] 
**match_numbers_as_strings** | **bool** |  | [optional] 

## Example

```python
from mockserver.models.body_any_of1 import BodyAnyOf1

# TODO update the JSON string below
json = "{}"
# create an instance of BodyAnyOf1 from a JSON string
body_any_of1_instance = BodyAnyOf1.from_json(json)
# print the JSON string representation of the object
print(BodyAnyOf1.to_json())

# convert the object into a dict
body_any_of1_dict = body_any_of1_instance.to_dict()
# create an instance of BodyAnyOf1 from a dict
body_any_of1_from_dict = BodyAnyOf1.from_dict(body_any_of1_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


