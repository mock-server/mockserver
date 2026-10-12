# Provider Contract Fixtures

Real provider responses, each copied verbatim from the provider's API reference or an
SDK's recorded test data and cited in its `<case>.contract.json`. `LlmProviderContractTest`
sends each contract's request through MockServer and checks that the response has the
real one's structure. Nothing here is generated from MockServer, so do not regenerate it.

See [docs/code/llm-codec-fixtures.md](../../../../../../../docs/code/llm-codec-fixtures.md#provider-contract-tests-real-responses)
for the contract format, the normalisation rules and how to add a case.
