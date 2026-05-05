# BodyAnyOf4

parameter body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**parameters** | [**KeyToMultiValue**](KeyToMultiValue.md) |  | [optional] 

## Example

```python
from mockserver.models.body_any_of4 import BodyAnyOf4

# TODO update the JSON string below
json = "{}"
# create an instance of BodyAnyOf4 from a JSON string
body_any_of4_instance = BodyAnyOf4.from_json(json)
# print the JSON string representation of the object
print(BodyAnyOf4.to_json())

# convert the object into a dict
body_any_of4_dict = body_any_of4_instance.to_dict()
# create an instance of BodyAnyOf4 from a dict
body_any_of4_from_dict = BodyAnyOf4.from_dict(body_any_of4_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


