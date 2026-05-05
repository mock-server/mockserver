# VerificationSequence

verification sequence

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expectation_ids** | [**List[ExpectationId]**](ExpectationId.md) |  | [optional] 
**http_requests** | [**List[RequestDefinition]**](RequestDefinition.md) |  | [optional] 
**maximum_number_of_request_to_return_in_verification_failure** | **int** |  | [optional] 

## Example

```python
from mockserver.models.verification_sequence import VerificationSequence

# TODO update the JSON string below
json = "{}"
# create an instance of VerificationSequence from a JSON string
verification_sequence_instance = VerificationSequence.from_json(json)
# print the JSON string representation of the object
print(VerificationSequence.to_json())

# convert the object into a dict
verification_sequence_dict = verification_sequence_instance.to_dict()
# create an instance of VerificationSequence from a dict
verification_sequence_from_dict = VerificationSequence.from_dict(verification_sequence_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


