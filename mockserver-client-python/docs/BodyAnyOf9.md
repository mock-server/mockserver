# BodyAnyOf9

xpath body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**xpath** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body_any_of9 import BodyAnyOf9

# TODO update the JSON string below
json = "{}"
# create an instance of BodyAnyOf9 from a JSON string
body_any_of9_instance = BodyAnyOf9.from_json(json)
# print the JSON string representation of the object
print(BodyAnyOf9.to_json())

# convert the object into a dict
body_any_of9_dict = body_any_of9_instance.to_dict()
# create an instance of BodyAnyOf9 from a dict
body_any_of9_from_dict = BodyAnyOf9.from_dict(body_any_of9_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


