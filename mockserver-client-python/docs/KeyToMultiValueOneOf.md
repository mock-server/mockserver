# KeyToMultiValueOneOf


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**key_match_style** | **str** |  | [optional] [default to 'SUB_SET']

## Example

```python
from mockserver.models.key_to_multi_value_one_of import KeyToMultiValueOneOf

# TODO update the JSON string below
json = "{}"
# create an instance of KeyToMultiValueOneOf from a JSON string
key_to_multi_value_one_of_instance = KeyToMultiValueOneOf.from_json(json)
# print the JSON string representation of the object
print(KeyToMultiValueOneOf.to_json())

# convert the object into a dict
key_to_multi_value_one_of_dict = key_to_multi_value_one_of_instance.to_dict()
# create an instance of KeyToMultiValueOneOf from a dict
key_to_multi_value_one_of_from_dict = KeyToMultiValueOneOf.from_dict(key_to_multi_value_one_of_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


