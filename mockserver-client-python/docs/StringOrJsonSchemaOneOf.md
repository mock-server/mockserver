# StringOrJsonSchemaOneOf


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
from mockserver.models.string_or_json_schema_one_of import StringOrJsonSchemaOneOf

# TODO update the JSON string below
json = "{}"
# create an instance of StringOrJsonSchemaOneOf from a JSON string
string_or_json_schema_one_of_instance = StringOrJsonSchemaOneOf.from_json(json)
# print the JSON string representation of the object
print(StringOrJsonSchemaOneOf.to_json())

# convert the object into a dict
string_or_json_schema_one_of_dict = string_or_json_schema_one_of_instance.to_dict()
# create an instance of StringOrJsonSchemaOneOf from a dict
string_or_json_schema_one_of_from_dict = StringOrJsonSchemaOneOf.from_dict(string_or_json_schema_one_of_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


