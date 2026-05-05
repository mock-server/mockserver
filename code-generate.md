brew install openapi-generator

openapi-generator generate -i https://raw.githubusercontent.com/jamesdbloom/mockserver/master/mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml --config code-generation-config.json -g ruby -o .