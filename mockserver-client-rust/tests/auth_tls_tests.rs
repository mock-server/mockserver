//! Control-plane auth + TLS/mTLS parity tests.
//!
//! Two kinds of tests, no running MockServer required:
//!  * a `tiny_http` stub that captures request **headers**, proving a configured
//!    control-plane bearer token rides on the `Authorization` header of a
//!    control-plane request (and that, by default, no such header is sent), and
//!  * builder-level wiring tests proving that real PEM material (a CA cert and a
//!    client cert + key generated at test time with `openssl`) loads into the
//!    reqwest HTTP layer without error, and that malformed PEM is rejected at
//!    `build()` time. A full TLS handshake is impractical in a unit test and is
//!    covered centrally by the Docker harness.

use std::process::Command;
use std::sync::mpsc;
use std::thread;

use mockserver_client::*;

// ---------------------------------------------------------------------------
// Stub HTTP server that captures request headers
// ---------------------------------------------------------------------------

/// What the stub captured about the single request it served.
struct Captured {
    headers: Vec<(String, String)>,
}

impl Captured {
    /// Case-insensitively find the first value of a header.
    fn header(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(k, _)| k.eq_ignore_ascii_case(name))
            .map(|(_, v)| v.as_str())
    }
}

/// Start a one-shot stub server that replies `200`/`resp_body` to the first
/// request, capturing its headers. Returns the bound port and a receiver that
/// yields the captured request.
fn header_stub(resp_body: &'static str) -> (u16, mpsc::Receiver<Captured>) {
    let server = tiny_http::Server::http("127.0.0.1:0").expect("bind stub server");
    let port = match server.server_addr() {
        tiny_http::ListenAddr::IP(addr) => addr.port(),
        #[allow(unreachable_patterns)]
        _ => panic!("expected IP listen address"),
    };
    let (tx, rx) = mpsc::channel();

    thread::spawn(move || {
        let mut request = server.recv().expect("stub recv");
        let headers = request
            .headers()
            .iter()
            .map(|h| (h.field.as_str().as_str().to_string(), h.value.as_str().to_string()))
            .collect();
        // Drain the body so the connection completes cleanly.
        let mut body = String::new();
        request.as_reader().read_to_string(&mut body).ok();
        tx.send(Captured { headers }).ok();

        let response = tiny_http::Response::from_string(resp_body)
            .with_status_code(tiny_http::StatusCode(200));
        request.respond(response).ok();
    });

    (port, rx)
}

// ---------------------------------------------------------------------------
// Bearer token — control-plane header attach
// ---------------------------------------------------------------------------

#[test]
fn test_bearer_token_attached_to_control_plane_request() {
    let (port, rx) = header_stub("{\"ports\":[1080]}");

    let client = ClientBuilder::new("127.0.0.1", port)
        .control_plane_bearer_token("test-jwt-token")
        .build()
        .expect("build client");

    // `status()` issues a control-plane PUT /mockserver/status.
    client.status().expect("status call");

    let captured = rx.recv().expect("captured request");
    assert_eq!(
        captured.header("authorization"),
        Some("Bearer test-jwt-token"),
        "control-plane request must carry the bearer token"
    );
}

#[test]
fn test_basic_credentials_attached_to_control_plane_request() {
    let (port, rx) = header_stub("{\"ports\":[1080]}");

    let client = ClientBuilder::new("127.0.0.1", port)
        .control_plane_basic_authentication("admin", "s3cret")
        .build()
        .expect("build client");

    client.status().expect("status call");

    let captured = rx.recv().expect("captured request");
    assert_eq!(
        captured.header("authorization"),
        Some("Basic YWRtaW46czNjcmV0"),
        "control-plane request must carry the basic credentials"
    );
}

#[test]
fn test_basic_credentials_stay_out_of_the_error_of_a_failed_request() {
    let server = tiny_http::Server::http("127.0.0.1:0").expect("bind stub server");
    let port = match server.server_addr() {
        tiny_http::ListenAddr::IP(addr) => addr.port(),
        #[allow(unreachable_patterns)]
        _ => panic!("expected IP listen address"),
    };
    thread::spawn(move || {
        if let Ok(request) = server.recv() {
            request
                .respond(tiny_http::Response::from_string("failed").with_status_code(tiny_http::StatusCode(500)))
                .ok();
        }
    });

    let client = ClientBuilder::new("127.0.0.1", port)
        .control_plane_basic_authentication("admin", "pa55-near-miss")
        .build()
        .expect("build client");
    let error = client.reset().expect_err("a 500 fails");
    let failure = format!("{error:?} {error}");

    assert!(!failure.contains("pa55-near-miss"), "{failure}");
    assert!(!failure.contains("YWRtaW46cGE1NS1uZWFyLW1pc3M="), "{failure}");
}

#[test]
fn test_no_bearer_token_means_no_authorization_header() {
    let (port, rx) = header_stub("{\"ports\":[1080]}");

    let client = ClientBuilder::new("127.0.0.1", port)
        .build()
        .expect("build client");

    client.status().expect("status call");

    let captured = rx.recv().expect("captured request");
    assert_eq!(
        captured.header("authorization"),
        None,
        "default client must not send an Authorization header"
    );
}

#[test]
fn test_bearer_token_accepts_into_string() {
    // Compile-time + runtime check that `impl Into<String>` accepts an owned
    // String as well as a &str.
    let (port, rx) = header_stub("{\"ports\":[1080]}");
    let token = String::from("owned-token");

    let client = ClientBuilder::new("127.0.0.1", port)
        .control_plane_bearer_token(token)
        .build()
        .expect("build client");
    client.status().expect("status call");

    let captured = rx.recv().expect("captured request");
    assert_eq!(captured.header("authorization"), Some("Bearer owned-token"));
}

// ---------------------------------------------------------------------------
// TLS CA trust + mTLS client identity — builder wiring
// ---------------------------------------------------------------------------

/// Generate a self-signed cert + PKCS#8 key PEM pair into `dir`, returning the
/// cert and key file paths. Skips (returns None) if `openssl` is unavailable.
fn gen_cert(dir: &std::path::Path, stem: &str) -> Option<(std::path::PathBuf, std::path::PathBuf)> {
    let key = dir.join(format!("{stem}-key.pem"));
    let cert = dir.join(format!("{stem}-cert.pem"));

    // EC key in PKCS#8 PEM (native-tls Identity::from_pkcs8_pem accepts EC/RSA).
    let key_ok = Command::new("openssl")
        .args([
            "req",
            "-x509",
            "-newkey",
            "rsa:2048",
            "-nodes",
            "-keyout",
            key.to_str().unwrap(),
            "-out",
            cert.to_str().unwrap(),
            "-days",
            "1",
            "-subj",
            "/CN=mockserver-client-test",
        ])
        .output();

    match key_ok {
        Ok(out) if out.status.success() => Some((cert, key)),
        _ => None,
    }
}

/// A throwaway temp dir under the OS temp root, cleaned on drop.
struct TempDir(std::path::PathBuf);
impl TempDir {
    fn new() -> Self {
        let mut p = std::env::temp_dir();
        let unique = format!(
            "mockserver-rust-tls-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        );
        p.push(unique);
        std::fs::create_dir_all(&p).expect("create temp dir");
        TempDir(p)
    }
    fn path(&self) -> &std::path::Path {
        &self.0
    }
}
impl Drop for TempDir {
    fn drop(&mut self) {
        std::fs::remove_dir_all(&self.0).ok();
    }
}

#[test]
fn test_ca_cert_pem_path_loads_into_client() {
    let dir = TempDir::new();
    let (cert, _key) = match gen_cert(dir.path(), "ca") {
        Some(p) => p,
        None => {
            eprintln!("skipping: openssl not available");
            return;
        }
    };

    // A valid CA PEM must build a usable HTTPS client without error.
    let client = ClientBuilder::new("127.0.0.1", 1080)
        .secure(true)
        .ca_cert_pem_path(&cert)
        .build();
    assert!(
        client.is_ok(),
        "valid CA cert PEM should wire into the client: {:?}",
        client.err()
    );
}

#[test]
fn test_ca_cert_pem_bytes_loads_into_client() {
    let dir = TempDir::new();
    let (cert, _key) = match gen_cert(dir.path(), "ca-bytes") {
        Some(p) => p,
        None => {
            eprintln!("skipping: openssl not available");
            return;
        }
    };
    let pem = std::fs::read(&cert).expect("read cert");

    let client = ClientBuilder::new("127.0.0.1", 1080)
        .secure(true)
        .ca_cert_pem(pem)
        .build();
    assert!(client.is_ok(), "valid CA cert bytes should wire in: {:?}", client.err());
}

#[test]
fn test_invalid_ca_cert_pem_fails_build() {
    let client = ClientBuilder::new("127.0.0.1", 1080)
        .secure(true)
        .ca_cert_pem(b"not a pem".to_vec())
        .build();
    assert!(client.is_err(), "malformed CA PEM must fail build()");
}

#[test]
fn test_missing_ca_cert_path_fails_build() {
    // A non-existent path records a sentinel that build() must reject loudly.
    let client = ClientBuilder::new("127.0.0.1", 1080)
        .secure(true)
        .ca_cert_pem_path("/no/such/ca-cert-file.pem")
        .build();
    assert!(client.is_err(), "missing CA cert file must fail build()");
}

#[test]
fn test_client_cert_pem_loads_into_client() {
    let dir = TempDir::new();
    let (cert, key) = match gen_cert(dir.path(), "client") {
        Some(p) => p,
        None => {
            eprintln!("skipping: openssl not available");
            return;
        }
    };

    let client = ClientBuilder::new("127.0.0.1", 1080)
        .secure(true)
        .client_cert_pem(&cert, &key)
        .build();
    assert!(
        client.is_ok(),
        "valid client cert + key PEM should wire into the client (mTLS): {:?}",
        client.err()
    );
}

#[test]
fn test_missing_client_cert_fails_build() {
    let client = ClientBuilder::new("127.0.0.1", 1080)
        .secure(true)
        .client_cert_pem("/no/such/cert.pem", "/no/such/key.pem")
        .build();
    assert!(client.is_err(), "missing client cert files must fail build()");
}
