# KeyToMultiValue


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**key_match_style** | **str** |  | [optional] [default to 'SUB_SET']

## Example

```python
from mockserver.models.key_to_multi_value import KeyToMultiValue

# TODO update the JSON string below
json = "{}"
# create an instance of KeyToMultiValue from a JSON string
key_to_multi_value_instance = KeyToMultiValue.from_json(json)
# print the JSON string representation of the object
print(KeyToMultiValue.to_json())

# convert the object into a dict
key_to_multi_value_dict = key_to_multi_value_instance.to_dict()
# create an instance of KeyToMultiValue from a dict
key_to_multi_value_from_dict = KeyToMultiValue.from_dict(key_to_multi_value_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


