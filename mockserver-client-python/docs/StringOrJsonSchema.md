# StringOrJsonSchema

string value that can be support nottable, optional or a json schema

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**optional** | **bool** |  | [optional] 
**value** | **str** |  | [optional] 
**var_schema** | **object** | JSON Schema (draft-04) | [optional] 
**parameter_style** | **str** |  | [optional] 

## Example

```python
from mockserver.models.string_or_json_schema import StringOrJsonSchema

# TODO update the JSON string below
json = "{}"
# create an instance of StringOrJsonSchema from a JSON string
string_or_json_schema_instance = StringOrJsonSchema.from_json(json)
# print the JSON string representation of the object
print(StringOrJsonSchema.to_json())

# convert the object into a dict
string_or_json_schema_dict = string_or_json_schema_instance.to_dict()
# create an instance of StringOrJsonSchema from a dict
string_or_json_schema_from_dict = StringOrJsonSchema.from_dict(string_or_json_schema_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


