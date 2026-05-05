# ExpectationId

pointer to existing expectation

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **str** |  | 

## Example

```python
from mockserver.models.expectation_id import ExpectationId

# TODO update the JSON string below
json = "{}"
# create an instance of ExpectationId from a JSON string
expectation_id_instance = ExpectationId.from_json(json)
# print the JSON string representation of the object
print(ExpectationId.to_json())

# convert the object into a dict
expectation_id_dict = expectation_id_instance.to_dict()
# create an instance of ExpectationId from a dict
expectation_id_from_dict = ExpectationId.from_dict(expectation_id_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


