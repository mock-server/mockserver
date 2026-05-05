# VerificationTimes

number of request to verify

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**at_least** | **int** |  | [optional] 
**at_most** | **int** |  | [optional] 

## Example

```python
from mockserver.models.verification_times import VerificationTimes

# TODO update the JSON string below
json = "{}"
# create an instance of VerificationTimes from a JSON string
verification_times_instance = VerificationTimes.from_json(json)
# print the JSON string representation of the object
print(VerificationTimes.to_json())

# convert the object into a dict
verification_times_dict = verification_times_instance.to_dict()
# create an instance of VerificationTimes from a dict
verification_times_from_dict = VerificationTimes.from_dict(verification_times_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


