# Publishing mockserver-client to crates.io

## Prerequisites

- Rust toolchain (stable): `rustup update stable`
- `cargo` CLI (included with Rust)
- API token for crates.io with publish permission

## Status

The crate name `mockserver-client` is **claimed** on crates.io (first published as
`8.0.0-alpha.1` on 2026-06-11 to reserve the name). The release pipeline publishes <!-- version-ref:keep -->
the stable release version on top.

## Secret

The crates.io API token is stored in AWS Secrets Manager (created out of band;
referenced in Terraform via a data source):

```
Secret: mockserver-release/crates
Key:    token
```

The release script reads it with `load_secret "mockserver-release/crates" "token"`
and exports it as `CARGO_REGISTRY_TOKEN` for `cargo publish`. Retrieve it manually
via:
```sh
CARGO_REGISTRY_TOKEN=$(aws secretsmanager get-secret-value \
  --secret-id mockserver-release/crates \
  --query SecretString --output text \
  --profile mockserver-build --region eu-west-2 | jq -r '.token')
```

## Publish Command (non-interactive)

```sh
cd mockserver-client-rust
CARGO_REGISTRY_TOKEN="$CARGO_REGISTRY_TOKEN" cargo publish
```

## Version Management

The crate version in `Cargo.toml` tracks the MockServer release version (strip
`-SNAPSHOT` from `mockserver/pom.xml`). The release pipeline bumps it before
publishing.

## Verification

After publishing, verify the crate is live:

```sh
# Check crates.io API (may take a few minutes to index)
curl -s https://crates.io/api/v1/crates/mockserver-client | jq '.crate.max_version'
```

## Eventual split

If this crate is ever moved to its own repository, the package name
(`mockserver-client`) and crate ownership on crates.io remain unchanged.
