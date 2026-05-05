# BodyAnyOf8

xml schema body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**xml_schema** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body_any_of8 import BodyAnyOf8

# TODO update the JSON string below
json = "{}"
# create an instance of BodyAnyOf8 from a JSON string
body_any_of8_instance = BodyAnyOf8.from_json(json)
# print the JSON string representation of the object
print(BodyAnyOf8.to_json())

# convert the object into a dict
body_any_of8_dict = body_any_of8_instance.to_dict()
# create an instance of BodyAnyOf8 from a dict
body_any_of8_from_dict = BodyAnyOf8.from_dict(body_any_of8_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


