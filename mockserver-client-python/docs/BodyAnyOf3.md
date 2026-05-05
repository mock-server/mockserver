# BodyAnyOf3

JSON path body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**json_path** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body_any_of3 import BodyAnyOf3

# TODO update the JSON string below
json = "{}"
# create an instance of BodyAnyOf3 from a JSON string
body_any_of3_instance = BodyAnyOf3.from_json(json)
# print the JSON string representation of the object
print(BodyAnyOf3.to_json())

# convert the object into a dict
body_any_of3_dict = body_any_of3_instance.to_dict()
# create an instance of BodyAnyOf3 from a dict
body_any_of3_from_dict = BodyAnyOf3.from_dict(body_any_of3_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


