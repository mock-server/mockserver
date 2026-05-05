# Python Client Code Generation

## Prerequisites

```bash
brew install openapi-generator
```

## Generate

The OpenAPI spec contains `$ref: http://json-schema.org/draft-04/schema` references that
OpenAPI Generator cannot resolve. Before generating, create a patched copy that replaces
those refs with `type: object`:

```bash
cd /path/to/mockserver-monorepo

# Create patched spec (replace JSON Schema $ref with inline object)
sed 's|\$ref: http://json-schema.org/draft-04/schema|type: object\n              description: JSON Schema (draft-04)|g' \
  mockserver/mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml \
  > .tmp/mock-server-openapi-patched.yaml

# Generate
openapi-generator generate \
  -i .tmp/mock-server-openapi-patched.yaml \
  --config mockserver-client-python/code-generation-config.json \
  --skip-validate-spec \
  -g python \
  -o mockserver-client-python
```

## Post-generation

1. Update `pyproject.toml` metadata (package name, author, URLs) — the generator overwrites these
2. Remove generated CI files not needed in the monorepo (`.travis.yml`, `.github/`, `.gitlab-ci.yml`)
3. Run tests: `cd mockserver-client-python && pip install -e '.[dev]' && pytest test/`

## References

- [OpenAPI Generator Python docs](https://openapi-generator.tech/docs/generators/python)
- [Source OpenAPI spec](../mockserver/mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml)
