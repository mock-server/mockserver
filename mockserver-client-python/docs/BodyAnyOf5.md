# BodyAnyOf5

regex body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**regex** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body_any_of5 import BodyAnyOf5

# TODO update the JSON string below
json = "{}"
# create an instance of BodyAnyOf5 from a JSON string
body_any_of5_instance = BodyAnyOf5.from_json(json)
# print the JSON string representation of the object
print(BodyAnyOf5.to_json())

# convert the object into a dict
body_any_of5_dict = body_any_of5_instance.to_dict()
# create an instance of BodyAnyOf5 from a dict
body_any_of5_from_dict = BodyAnyOf5.from_dict(body_any_of5_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


