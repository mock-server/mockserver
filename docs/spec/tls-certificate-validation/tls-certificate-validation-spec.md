# TLS Certificate Validation — Specification

| Field | Value |
|-------|-------|
| Status | Draft |
| Created | 2026-05-03 |
| Author | — |
| Modules | `mockserver-core`, `mockserver-netty` |

## 1. Executive Summary

MockServer users who provide custom TLS certificates receive no upfront validation — misconfigurations are only discovered when a client connects, producing cryptic Netty-level error messages that obscure the root cause. This specification defines a comprehensive overhaul: startup-time certificate validation that fails hard with actionable messages, improved runtime TLS error diagnostics, consistency fixes for file-existence checks, null-safety for `createServerSslContext()`, intermediate CA chain support, and cleanup of confusing naming and dead code. The goal is that **no user should ever need to search Stack Overflow to diagnose a MockServer TLS misconfiguration**.

## 2. Problem Statement

MockServer users who configure custom TLS certificates via `privateKeyPath`, `x509CertificatePath`, `certificateAuthorityCertificate`, and `certificateAuthorityPrivateKey` currently receive no upfront validation. Misconfigurations are only discovered when a client connects and the TLS handshake fails, producing error messages such as:

- `"Insufficient buffer remaining for AEAD cipher fragment"`
- `"TLS handshake failure while a client attempted to connect to {channel}"`
- `NullPointerException` deep in Netty's SSL pipeline (when `createServerSslContext()` returns `null`)

These messages give no indication of the root cause (e.g., key/cert mismatch, expired certificate, missing file).

## 3. Background & Context

### Configuration Entry Points

Certificate paths enter the system through two parallel configuration mechanisms:

- **Static properties:** `ConfigurationProperties.java` — static setters called by property/env-var loading. Some setters call `fileExists()` (e.g., `certificateAuthorityCertificate` at line 1519), but others do not (e.g., `certificateAuthorityPrivateKey` at line 1505, `tlsMutualAuthenticationCertificateChain` at line 1588).
- **Instance configuration:** `Configuration.java` — fluent setters (lines 1671–1735) that store values directly with **no** `fileExists()` validation on any path.

### Certificate Loading & SSL Context

`NettySslContextFactory.createServerSslContext()` (line 180) orchestrates certificate loading. On failure, it catches `Throwable`, logs an error, and **returns `null`** (line 228), causing a `NullPointerException` when Netty later tries to use the context.

The method also contains a double-build bug: `sslContextBuilder.build()` is called at line 214, then immediately overwritten by a second `sslContextBuilder.build()` via the customizer at lines 215–217.

### Certificate Generation vs. Loading

`BCKeyAndCertificateFactory.customPrivateKeyAndCertificateProvided()` (line 355) returns `true` when custom certs are **NOT** provided (i.e., when the fields are blank). This inverted naming causes confusion — it actually means "should generate certificates dynamically". It controls `buildAndSavePrivateKeyAndX509Certificate()` (line 206), `privateKey()` (line 259), `x509Certificate()` (line 278), and `certificateNotYetCreated()` (line 352).

### Certificate Chain Handling

`BCKeyAndCertificateFactory.certificateChain()` (line 384) returns `[leaf, CA]` — only two certificates. When the leaf cert is signed by an intermediate CA, the intermediate is lost because `x509Certificate()` at line 281 calls `x509FromPEMFile()` which reads only the **first** PEM block from the file.

### Runtime Error Handling

`PortUnificationHandler.exceptionCaught()` (line 427) handles TLS failures. It checks for `certificate_unknown`/`unknown_ca` specifically (line 436) but provides a generic message for all other handshake failures (line 450). The `certificate_unknown` message at line 441 contains the typo `"MocksServer"`.

## 4. Actors

```mermaid
flowchart LR
    User["User / Operator"]
    Config["Configuration\n(properties / env vars)"]
    Factory["NettySslContextFactory"]
    BC["BCKeyAndCertificateFactory"]
    PUH["PortUnificationHandler"]
    Client["TLS Client"]

    User -->|sets cert paths| Config
    Config -->|read at startup| Factory
    Factory -->|delegates to| BC
    BC -->|loads/generates certs| Factory
    Factory -->|creates SslContext| PUH
    Client -->|TLS handshake| PUH
    PUH -->|error on failure| User
```

## 5. Current Behaviour

| Scenario | What Happens Today | User Experience |
|----------|-------------------|-----------------|
| Only `privateKeyPath` set, `x509CertificatePath` missing | MockServer starts, falls through to dynamic cert generation | Silent misconfiguration — user's custom cert is ignored |
| Private key doesn't match certificate | MockServer starts, TLS handshake fails on first connection | `"Insufficient buffer remaining for AEAD cipher fragment"` |
| Certificate expired | MockServer starts, some clients reject, others accept | Intermittent failures depending on client strictness |
| Certificate not signed by configured CA | MockServer starts, chain verification fails at client | `"TLS handshake failure while a client attempted to connect to {channel}"` |
| CA certificate file doesn't exist | `fileExists()` throws on `ConfigurationProperties` setter, but **not** on `Configuration` setter | Inconsistent — works or fails depending on config method |
| CA private key file doesn't exist | No `fileExists()` call — silently proceeds | Cryptic error later during cert generation |
| `createServerSslContext()` throws | Logs error, returns `null` | `NullPointerException` on first TLS connection |
| `x509CertificatePath` contains intermediate chain | Only first cert loaded | `"unknown_ca"` or chain verification failure at client |
| Client doesn't trust CA | `"...trust MocksServer Certificate Authority"` | Typo in message; no mention of which cert files are configured |

## 6. Desired Behaviour

| Scenario | What Should Happen |
|----------|-------------------|
| Only `privateKeyPath` set | **Fail to start:** `"Both 'privateKeyPath' and 'x509CertificatePath' must be configured together. You set 'privateKeyPath' to '/path/to/key.pem' but 'x509CertificatePath' is not set."` |
| Key/cert mismatch | **Fail to start:** `"The private key at '/path/to/key.pem' does not match the certificate at '/path/to/cert.pem'. The public key fingerprints differ. Regenerate the key pair or check that the files correspond to each other."` |
| Cert not signed by CA | **Fail to start:** `"The certificate at '/path/to/cert.pem' was not signed by the CA certificate at '/path/to/ca.pem'. Verify the certificate chain or update 'certificateAuthorityCertificate'."` |
| Certificate expired | **Fail to start:** `"The certificate at '/path/to/cert.pem' expired on 2025-01-01T00:00:00Z. Replace it with a valid certificate."` |
| Missing EKU | **Warn at startup** (not hard failure): `"The certificate at '/path/to/cert.pem' does not include the 'serverAuth' Extended Key Usage extension. Some strict TLS clients may reject it."` |
| CA file doesn't exist | **Fail to start:** `"The CA certificate file '/path/to/ca.pem' does not exist."` |
| `createServerSslContext()` fails | **Throw RuntimeException** — MockServer cannot serve TLS without a context |
| Intermediate chain in PEM | Full chain loaded and sent during handshake |
| Client doesn't trust CA | `"TLS handshake failure: Client does not trust MockServer Certificate Authority. Configured CA cert: '/path/to/ca.pem'. See https://mock-server.com/mock_server/HTTPS_TLS.html"` |

## 7. Scope

### In Scope

| Area | Description |
|------|-------------|
| Startup validation | Validate certificate configuration before accepting connections |
| Hard failure | MockServer MUST NOT start with invalid TLS configuration |
| Error messages | Actionable messages with file paths and remediation steps |
| Runtime diagnostics | Improved TLS handshake failure messages |
| File existence consistency | All certificate path setters call `fileExists()` |
| Null safety | `createServerSslContext()` throws instead of returning null |
| Intermediate CA chains | Full chain loading from PEM files |
| Naming cleanup | Rename `customPrivateKeyAndCertificateProvided()` |
| Dead code removal | Remove double `sslContextBuilder.build()` |
| Typo fix | `"MocksServer"` → `"MockServer"` |

### Out of Scope

| Area | Rationale |
|------|-----------|
| Client-side (forward proxy) TLS validation | Different code path, different failure modes — separate spec |
| OCSP / CRL checking | Adds complexity and network dependency; not needed for test tool |
| Certificate auto-renewal | MockServer is a test tool, not a production server |
| HSM / PKCS#11 key storage | Niche use case, no demand |
| UI/dashboard certificate status | Separate concern |
| mTLS client certificate validation at startup | Only server-side certs validated; client certs validated at handshake time |

## 8. Functional Requirements

### 8.1 Startup Certificate Validation (Hard Failure)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-01 | When only one of `privateKeyPath` / `x509CertificatePath` is set (non-blank), MockServer MUST fail to start with a message naming both properties and their current values. | MUST |
| FR-02 | When both `privateKeyPath` and `x509CertificatePath` are set, MockServer MUST verify the private key matches the certificate by comparing public key encodings. On mismatch, MockServer MUST fail to start with a message including both file paths. | MUST |
| FR-03 | When `certificateAuthorityCertificate` is configured and custom leaf certs are provided, MockServer MUST verify the leaf certificate's signature against the CA certificate. On failure, MockServer MUST fail to start with a message including both file paths. | MUST |
| FR-04 | MockServer MUST check `X509Certificate.checkValidity(new Date())` for the leaf certificate. If expired or not yet valid, MockServer MUST fail to start with a message including the expiry/notBefore date and the file path. | MUST |
| FR-05 | MockServer SHOULD check for `serverAuth` Extended Key Usage on the leaf certificate. If absent, MockServer SHOULD log a WARN-level message but MUST NOT fail to start. | SHOULD |
| FR-06 | When `certificateAuthorityCertificate` is set to a non-default value, MockServer MUST verify the file exists and contains a valid PEM-encoded X.509 certificate. On failure, MockServer MUST fail to start. | MUST |
| FR-07 | When `certificateAuthorityPrivateKey` is set to a non-default value, MockServer MUST verify the file exists and contains a valid PEM-encoded private key. On failure, MockServer MUST fail to start. | MUST |

### 8.2 Improved Runtime TLS Error Messages

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-08 | When a TLS handshake fails in `PortUnificationHandler`, the error message MUST include a diagnostic hint based on the exception message content. Mappings: `certificate_unknown` / `unknown_ca` → "client does not trust the server's CA certificate"; `bad_certificate` → "the certificate was rejected by the client — check expiry and chain"; `handshake_failure` → "TLS protocol or cipher mismatch"; `no_certificate` → "client did not send a certificate (mTLS may be required)". | MUST |
| FR-09 | The typo `"MocksServer"` in `PortUnificationHandler.java` line 441 MUST be corrected to `"MockServer"`. | MUST |
| FR-10 | All TLS handshake failure messages in `PortUnificationHandler` MUST include the configured certificate file paths (`x509CertificatePath`, `certificateAuthorityCertificate`) to aid debugging. | MUST |

### 8.3 Consistent fileExists() Validation

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-11 | `ConfigurationProperties.certificateAuthorityPrivateKey(String)` (line 1505) MUST call `fileExists()` before setting the property, consistent with `certificateAuthorityCertificate(String)` (line 1519). | MUST |
| FR-12 | `ConfigurationProperties.tlsMutualAuthenticationCertificateChain(String)` (line 1588) MUST call `fileExists()` before setting the property. | MUST |
| FR-13 | `Configuration.java` fluent setters for `certificateAuthorityPrivateKey` (line 1671), `certificateAuthorityCertificate` (line 1688), `privateKeyPath` (line 1712), and `x509CertificatePath` (line 1734) MUST call `fileExists()` before storing the value. The `fileExists()` utility should be made accessible to `Configuration.java` (currently package-private in `ConfigurationProperties`). | MUST |

### 8.4 Fix createServerSslContext() Null Return

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-14 | `NettySslContextFactory.createServerSslContext()` MUST throw a `RuntimeException` (wrapping the original cause) on failure instead of catching `Throwable`, logging, and returning `null`. The exception message MUST include the configured certificate paths and a summary of what failed. | MUST |

### 8.5 Intermediate CA Chain Support

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-15 | When `x509CertificatePath` contains multiple PEM-encoded certificates (a chain file), `BCKeyAndCertificateFactory.x509Certificate()` MUST load all certificates using `x509ChainFromPEMFile()` instead of `x509FromPEMFile()` (which reads only the first PEM block). The first certificate in the file is the leaf; subsequent certificates are intermediates. | MUST |
| FR-16 | `BCKeyAndCertificateFactory.certificateChain()` (line 384) MUST return the full chain: `[leaf, intermediate₁, ..., intermediateₙ, CA]`. Currently it returns only `[leaf, CA]`. | MUST |

### 8.6 Fix Confusing Method Naming

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-17 | `BCKeyAndCertificateFactory.customPrivateKeyAndCertificateProvided()` (line 355) MUST be renamed to `shouldGenerateCertificates()` (or equivalent) to accurately reflect that it returns `true` when custom certificates are **not** provided and dynamic generation is needed. All call sites (lines 206, 259, 278, 352) MUST be updated. | MUST |

### 8.7 Fix Double-Build in createServerSslContext()

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-18 | The first `sslContextBuilder.build()` call at `NettySslContextFactory.java` line 214 MUST be removed. Only the customizer-applied build at lines 215–217 should remain. The current code builds the context, discards the result, then immediately rebuilds via the customizer. | MUST |

## 9. Non-Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| NFR-01 | All validation logic MUST compile and run on Java 11. No Java 17+ APIs or language features. | MUST |
| NFR-02 | Validation MUST NOT introduce new runtime dependencies. BouncyCastle (already present) and standard `java.security` APIs are sufficient. | MUST |
| NFR-03 | All error messages MUST be actionable: state what is wrong, which files are involved, and what the user should do to fix it. | MUST |
| NFR-04 | Deployments with correct TLS configuration MUST experience no behavioural change (backward compatibility). Deployments using default/generated certificates MUST be unaffected. | MUST |
| NFR-05 | Validation MUST run eagerly during `NettySslContextFactory` initialization (called from `MockServer` startup), not lazily on first TLS connection. | MUST |
| NFR-06 | Validation SHOULD complete in under 100ms for typical certificate files (< 10KB). | SHOULD |

## 10. Edge Cases & Error Handling

| Edge Case | Expected Behaviour |
|-----------|--------------------|
| Both `privateKeyPath` and `x509CertificatePath` are blank (default) | No custom cert validation; MockServer generates certs dynamically. No change from current behaviour. |
| Certificate file exists but is not valid PEM | Fail to start: `"The file '/path/to/cert.pem' is not a valid PEM-encoded certificate. Ensure the file contains a '-----BEGIN CERTIFICATE-----' block."` |
| Private key file exists but is not valid PEM | Fail to start: `"The file '/path/to/key.pem' is not a valid PEM-encoded private key. Ensure the file contains a '-----BEGIN PRIVATE KEY-----' or '-----BEGIN RSA PRIVATE KEY-----' block."` |
| Certificate file contains zero PEM blocks | Treated as invalid PEM (same as above). |
| Certificate file has correct leaf but wrong intermediate order | Load certificates in file order. RFC 5246 requires leaf first, then intermediates. If the file has intermediates before the leaf, the leaf (first cert) won't match the private key — caught by FR-02. |
| Private key is PKCS#1 format (`BEGIN RSA PRIVATE KEY`) | Must continue to work — BouncyCastle `PEMToFile.privateKeyFromPEMFile()` already handles both PKCS#1 and PKCS#8. |
| Certificate is valid but CA cert/key are defaults while leaf is custom | FR-03 catches this: the default CA didn't sign the custom leaf cert. |
| `certificateAuthorityCertificate` points to a private key file (user mixup) | FR-06 catches this: the file won't parse as an X.509 certificate. Message should suggest checking that the paths are not swapped. |
| File path is a classpath resource (e.g., `org/mockserver/...`) | `fileExists()` already supports classpath resolution. Validation must also resolve classpath resources for PEM parsing. |
| Symlinked certificate files | `fileExists()` follows symlinks. No special handling needed. |
| Certificate valid today but expires within 24 hours | Not checked (out of scope — expiry-soon warnings are a separate feature). |
| `sslServerContextBuilderCustomizer` throws | Currently silently returns null. After FR-14, the exception propagates with context. |

## 11. Success Criteria

| Criterion | Measurement |
|-----------|-------------|
| All 18 functional requirements have unit tests | Test count in `mockserver-core/src/test/java/org/mockserver/socket/tls/` |
| Misconfigured cert paths cause startup failure | Integration test: start MockServer with mismatched key/cert, assert `RuntimeException` with expected message |
| Correct configurations still work | Existing TLS integration tests pass without modification |
| Error messages include file paths | Assert message content in unit tests |
| Intermediate chain sent during TLS handshake | Integration test: configure leaf + intermediate, connect with client that only trusts root CA, assert handshake succeeds |
| No new dependencies | `pom.xml` diff shows no new `<dependency>` entries |
| Java 11 compatible | CI builds with Java 11 source/target pass |

## 12. Open Questions

| # | Question | Impact | Proposed Answer |
|---|----------|--------|-----------------|
| OQ-1 | Should we validate certificate expiry with a configurable grace period (e.g., warn if expiring within N days)? | Low — test tool typically uses short-lived certs | No — keep it simple. Expired = fail, valid = pass. |
| OQ-2 | Should FR-14 (throw on SSL context failure) be configurable to allow degraded-mode startup without TLS? | Medium — some users may want HTTP-only fallback | No — if TLS is configured, it must work. HTTP-only mode doesn't configure certificate paths. |
| OQ-3 | Should `Configuration.java` setters (FR-13) share the same `fileExists()` method, or should validation be centralized in `NettySslContextFactory`? | Low — implementation detail | Centralize a `FileValidator.fileExists()` utility in a shared package. Setters call it for fail-fast on obvious mistakes; `NettySslContextFactory` does the full semantic validation. |
| OQ-4 | Should the renamed method (FR-17) be `shouldGenerateCertificates()` or `usingGeneratedCertificates()` or `isCustomCertificateAbsent()`? | Low — naming preference | `shouldGenerateCertificates()` — it's a verb that matches how it's used in conditionals. |
| OQ-5 | For FR-15, should we support DER-encoded certificates in addition to PEM? | Low — PEM is the documented format | No — document PEM as the only supported format. |

## 13. Key Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| KD-1 | Hard failure at startup (not warn-and-continue) for FR-01 through FR-04, FR-06, FR-07 | A MockServer that can't do TLS correctly is worse than a MockServer that doesn't start. Fail-fast prevents hours of debugging. |
| KD-2 | Missing EKU (FR-05) is WARN, not failure | Some legitimate certificates (especially internal/test CAs) omit EKU. Hard failure would break valid setups. |
| KD-3 | Validation runs in `NettySslContextFactory`, not in configuration setters | Setters do basic file-existence checks (fail-fast). Semantic validation (key matching, chain verification, expiry) requires all config values to be set, so it runs during SSL context initialization. |
| KD-4 | `createServerSslContext()` throws instead of returning null | Null return causes deferred `NullPointerException` with no context. Throwing immediately with a descriptive message is strictly better. |
| KD-5 | No new dependencies | The JDK `java.security` package and BouncyCastle (already in the dependency tree) provide everything needed for certificate validation. |
| KD-6 | Intermediate chain support uses existing `x509ChainFromPEMFile()` | The method already exists in `PEMToFile` and is used for trust chains. Reusing it for the server cert chain is consistent. |

## Appendix: Key Files

### Production Code

| File | Relevant Lines | Role |
|------|---------------|------|
| `mockserver-core/.../configuration/ConfigurationProperties.java` | 1505, 1519, 1540, 1560, 1588 | Certificate path setters with inconsistent `fileExists()` |
| `mockserver-core/.../configuration/Configuration.java` | 1671–1735 | Fluent setters with no `fileExists()` |
| `mockserver-core/.../socket/tls/NettySslContextFactory.java` | 180–228 | `createServerSslContext()` — null return, double build |
| `mockserver-core/.../socket/tls/bouncycastle/BCKeyAndCertificateFactory.java` | 206, 259, 278, 352, 355, 384 | Cert generation, confusing naming, chain construction |
| `mockserver-core/.../socket/tls/PEMToFile.java` | — | `x509FromPEMFile()` vs `x509ChainFromPEMFile()` |
| `mockserver-core/.../socket/tls/KeyStoreFactory.java` | — | Keystore construction from certs |
| `mockserver-netty/.../netty/unification/PortUnificationHandler.java` | 427–456 | TLS error handling, typo |
| `mockserver-core/.../socket/tls/SniHandler.java` | — | SNI-based cert selection |

### Test Code

| File | Role |
|------|------|
| `mockserver-core/src/test/java/org/mockserver/socket/tls/` | Existing TLS unit tests — extend with validation tests |

### Documentation

| File | Role |
|------|------|
| `docs/code/tls-and-security.md` | Internal TLS architecture docs — update after implementation |
| `jekyll-www.mock-server.com/mock_server/HTTPS_TLS.html` | Consumer-facing TLS docs — update error message references |
