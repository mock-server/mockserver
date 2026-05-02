# TLS, Certificates & Security

## TLS Architecture

MockServer dynamically generates TLS certificates using BouncyCastle, enabling transparent HTTPS interception without pre-configured certificates.

```mermaid
graph TB
    subgraph "Certificate Generation"
        KCF[KeyAndCertificateFactory<br/><i>Interface</i>]
        BCF[BCKeyAndCertificateFactory<br/><i>BouncyCastle implementation</i>]
        KCF -.-> BCF
    end

    subgraph "SSL Context"
        NSCF[NettySslContextFactory<br/><i>Creates & caches SslContext</i>]
        KSF[KeyStoreFactory<br/><i>JKS KeyStore + SSLContext</i>]
    end

    subgraph "Netty Pipeline"
        SNI[SniHandler<br/><i>SNI extraction + ALPN</i>]
        SSL[SslHandler<br/><i>Netty built-in</i>]
    end

    BCF -->|provides certs| NSCF
    NSCF -->|server SslContext| SNI
    SNI -->|replaces self| SSL
    BCF -->|provides certs| KSF
```

### Dynamic Certificate Generation

When a TLS connection arrives:

```mermaid
sequenceDiagram
    participant C as Client
    participant SNI as SniHandler
    participant BCF as BCKeyAndCertificateFactory
    participant NSCF as NettySslContextFactory

    C->>SNI: TLS ClientHello (SNI: api.example.com)
    SNI->>SNI: Extract hostname from SNI extension
    SNI->>BCF: Add SAN: api.example.com
    BCF->>BCF: Generate leaf certificate<br/>signed by MockServer CA
    BCF-->>NSCF: Private key + certificate chain
    NSCF->>NSCF: Build SslContext<br/>(server-side, with ALPN)
    NSCF-->>SNI: SslContext
    SNI->>SNI: Replace self with SslHandler
    SNI->>SNI: Store ALPN result on channel<br/>(HTTP_1_1 or HTTP_2)
    SNI-->>C: TLS ServerHello + Certificate
```

### Certificate Authority

MockServer maintains an in-memory CA with default DN:
- **CN**: `www.mockserver.com`
- **O**: `MockServer`
- **L**: `London`
- **ST**: `England`
- **C**: `UK`

Custom CA certificates can be loaded from PEM files via configuration.

### Key Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `KeyAndCertificateFactory` | `o.m.socket.tls` | Interface for cert generation |
| `BCKeyAndCertificateFactory` | `o.m.socket.tls.bouncycastle` | BouncyCastle implementation: generates CA + leaf X.509 certs, supports dynamic SANs, reads custom PEM certs |
| `KeyAndCertificateFactoryFactory` | `o.m.socket.tls` | Factory with pluggable supplier |
| `NettySslContextFactory` | `o.m.socket.tls` | Creates and caches Netty `SslContext` for server and client sides; supports mTLS, HTTP/2 ALPN |
| `KeyStoreFactory` | `o.m.socket.tls` | Creates JKS `KeyStore` and `SSLContext` for non-Netty use |
| `SniHandler` | `o.m.socket.tls` | Extends Netty's `AbstractSniHandler`; extracts SNI hostname, provisions certificate, negotiates ALPN |
| `PEMToFile` | `o.m.socket.tls` | PEM format utilities (read/write private keys and X.509 chains) |

### SSL Context Caching

`NettySslContextFactory` caches `SslContext` objects to avoid regenerating them for every connection. It creates separate contexts for:
- **Server-side**: For accepting client connections (with the dynamically-generated certificate)
- **Client-side**: For forwarding to upstream servers (with configurable trust)

### Forward Proxy Trust

When forwarding requests, MockServer's `NettyHttpClient` needs to trust upstream servers. Three modes are supported via `ForwardProxyTLSX509CertificatesTrustManager`:

| Mode | Behaviour |
|------|-----------|
| `ANY` | Trust all certificates (insecure, useful for testing) |
| `JVM` | Use the JVM's default truststore |
| `CUSTOM` | Use a custom CA chain from configuration |

## Mutual TLS (mTLS)

MockServer supports mTLS for both incoming connections and the control plane:

### Incoming Connection mTLS

When `tlsMutualAuthenticationRequired` is configured, `PortUnificationHandler` checks for TLS on the channel. If the connection is not TLS, it returns **426 Upgrade Required** and disconnects.

Client certificates are extracted from the SSL session via `SniHandler.retrieveClientCertificates()` and stored as a channel attribute (`UPSTREAM_CLIENT_CERTIFICATES`).

### Control Plane mTLS

Control plane endpoints (`/mockserver/expectation`, `/mockserver/verify`, etc.) can require mTLS authentication. When configured, `HttpState.controlPlaneRequestAuthenticated()` validates the client certificate chain against the configured trust store.

## Control Plane Authentication

```mermaid
flowchart TD
    REQ([Control Plane Request]) --> AUTH{Authentication<br/>configured?}
    AUTH -->|No| ALLOW([Proceed])
    AUTH -->|Yes| TYPE{Auth type?}
    TYPE -->|mTLS| MTLS[Validate client<br/>certificate chain]
    TYPE -->|JWT| JWT[Validate Bearer token<br/><i>nimbus-jose-jwt</i>]
    TYPE -->|Both| CHAIN[mTLS AND JWT<br/>both must pass]
    MTLS -->|Pass| ALLOW
    MTLS -->|Fail| DENY([401/403])
    JWT -->|Pass| ALLOW
    JWT -->|Fail| DENY
    CHAIN -->|Both pass| ALLOW
    CHAIN -->|Either fails| DENY
```

Authentication is configured in `MockServer.createServerBootstrap()` and validated in `HttpState.controlPlaneRequestAuthenticated()`:

| Configuration | Handler | Mechanism |
|---------------|---------|-----------|
| `controlPlaneTLSMutualAuthenticationCAChain` | mTLS handler | Validates client cert against CA chain |
| `controlPlaneJWTAuthenticationJWKSource` | JWT handler | Validates Bearer token using JWK source |
| Both configured | Chained handler | Both mTLS and JWT must succeed |

### Authentication Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `AuthenticationHandler` | `o.m.authentication` | Core interface: `controlPlaneRequestAuthenticated(HttpRequest): boolean` |
| `ChainedAuthenticationHandler` | `o.m.authentication` | Chains multiple `AuthenticationHandler` instances (logical AND — all must pass) |
| `AuthenticationException` | `o.m.authentication` | Thrown on authentication failure |
| `MTLSAuthenticationHandler` | `o.m.authentication.mtls` | Validates client certificate chain against configured CA certificates via `X509Certificate.verify()` |
| `JWTAuthenticationHandler` | `o.m.authentication.jwt` | Loads JWK keys from URL (`RemoteJWKSet`) or file (`ImmutableJWKSet`), extracts Bearer token from `Authorization` header, delegates to `JWTValidator` |
| `JWTValidator` | `o.m.authentication.jwt` | Validates JWT tokens using nimbus-jose-jwt; supports `withExpectedAudience()`, `withMatchingClaims()`, `withRequiredClaims()` |
| `JWTGenerator` | `o.m.authentication.jwt` | Generates JWT tokens with configurable claims (used in tests) |
| `JWKGenerator` | `o.m.authentication.jwt` | Generates JWK sets from `AsymmetricKeyPair` objects (RSA and EC key types) |

### Supported JWS Algorithms

`JWTValidator` supports 15 JWS algorithms:

| Family | Algorithms |
|--------|-----------|
| HMAC | `HS256`, `HS384`, `HS512` |
| RSA PKCS#1 | `RS256`, `RS384`, `RS512` |
| ECDSA | `ES256`, `ES256K`, `ES384`, `ES512` |
| RSA-PSS | `PS256`, `PS384`, `PS512` |
| EdDSA | `EdDSA` |

### JWT Authentication

Uses `nimbus-jose-jwt` library. The JWT handler:
1. Extracts the `Authorization: Bearer <token>` header
2. Validates the token against the configured JWK source
3. Checks required claims (issuer, audience, etc.)

## Proxy Authentication

For HTTP CONNECT proxy requests, MockServer supports Basic authentication:

1. `HttpRequestHandler` checks the `Proxy-Authorization` header
2. Validates against configured username/password
3. On failure: returns **407 Proxy Authentication Required** with `Proxy-Authenticate: Basic` header

SOCKS5 proxy also supports username/password authentication (configured separately).

## TLS Configuration Properties

| Property | Default | Purpose |
|----------|---------|---------|
| `tlsMutualAuthenticationRequired` | false | Require client certificates |
| `tlsMutualAuthenticationCertificateChain` | (none) | PEM file with trusted CA chain for client certs |
| `dynamicallyCreateCertificateAuthorityCertificate` | true | Auto-generate CA cert |
| `certificateAuthorityPrivateKey` | (auto) | PEM file for custom CA private key |
| `certificateAuthorityCertificate` | (auto) | PEM file for custom CA certificate |
| `forwardProxyTLSX509CertificatesTrustManagerType` | ANY | Trust mode for upstream connections |
| `forwardProxyTLSCustomTrustX509Certificates` | (none) | PEM file for custom upstream trust |
| `controlPlaneTLSMutualAuthenticationRequired` | false | Require mTLS for control plane |
| `controlPlaneTLSMutualAuthenticationCAChain` | (none) | CA chain for control plane mTLS |
| `controlPlaneJWTAuthenticationJWKSource` | (none) | JWK source URL for JWT validation |
| `controlPlaneJWTAuthenticationRequired` | false | Require JWT for control plane |
