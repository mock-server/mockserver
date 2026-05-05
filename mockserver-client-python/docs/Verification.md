# Verification

verification

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expectation_id** | [**ExpectationId**](ExpectationId.md) |  | [optional] 
**http_request** | [**RequestDefinition**](RequestDefinition.md) |  | [optional] 
**times** | [**VerificationTimes**](VerificationTimes.md) |  | [optional] 
**maximum_number_of_request_to_return_in_verification_failure** | **int** |  | [optional] 

## Example

```python
from mockserver.models.verification import Verification

# TODO update the JSON string below
json = "{}"
# create an instance of Verification from a JSON string
verification_instance = Verification.from_json(json)
# print the JSON string representation of the object
print(Verification.to_json())

# convert the object into a dict
verification_dict = verification_instance.to_dict()
# create an instance of Verification from a dict
verification_from_dict = Verification.from_dict(verification_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


