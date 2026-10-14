// Package mockserver provides a Testcontainers module for starting a MockServer container.
//
// MockServer is an open-source HTTP(S) mock server and proxy for testing. This module
// starts the official mockserver/mockserver Docker image, waits for readiness, and
// exposes helpers to obtain the container's URL and mapped port.
//
// Usage:
//
//	ctx := context.Background()
//	ctr, err := mockserver.Run(ctx, "mockserver/mockserver:mockserver-7.0.0")
//	if err != nil {
//	    log.Fatal(err)
//	}
//	defer ctr.Terminate(ctx)
//
//	url, err := ctr.URL(ctx)
//	// url is "http://localhost:<mapped-port>"
package mockserver

import (
	"context"
	"fmt"
	"runtime/debug"
	"strings"

	mockserverclient "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
)

const (
	// DefaultPort is the default port MockServer listens on inside the container.
	// MockServer serves HTTP, HTTPS, SOCKS, and HTTP CONNECT on a single unified port.
	DefaultPort = "1080/tcp"

	// DefaultImageName is the Docker Hub image name (without a tag).
	DefaultImageName = "mockserver/mockserver"

	// selfModulePath is this module's own Go module path, used to derive the
	// default image tag from the build info version when consumed as a
	// versioned dependency.
	selfModulePath = "github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v9"
)

// DefaultImage is the default Docker image used by the examples and integration
// tests when Run is called without an explicit image. The tag is derived at init
// from this module's own resolved version in the build info (which tracks the
// MockServer release), falling back to :latest — mirroring the Java module's
// version-matched default instead of a hard-pinned tag that goes stale.
var DefaultImage = defaultImage()

func defaultImage() string {
	return DefaultImageName + ":" + defaultTag()
}

// defaultTag derives the image tag from this module's resolved version in the
// build info. It returns "mockserver-<version>" for a clean release version and
// falls back to "latest" otherwise (e.g. in-module tests, where the module is
// the main module and has no release version in Deps).
func defaultTag() string {
	if info, ok := debug.ReadBuildInfo(); ok {
		for _, dep := range info.Deps {
			if dep.Path == selfModulePath {
				if v := strings.TrimPrefix(dep.Version, "v"); isReleaseVersion(v) {
					return "mockserver-" + v
				}
			}
		}
	}
	return "latest"
}

// isReleaseVersion reports whether v is a clean semantic release version
// (MAJOR.MINOR.PATCH with numeric parts), excluding pseudo-versions and
// pre-releases.
func isReleaseVersion(v string) bool {
	parts := strings.Split(v, ".")
	if len(parts) != 3 {
		return false
	}
	for _, p := range parts {
		if p == "" {
			return false
		}
		for _, r := range p {
			if r < '0' || r > '9' {
				return false
			}
		}
	}
	return true
}

// MockServerContainer wraps a testcontainers.Container with MockServer-specific helpers.
type MockServerContainer struct {
	testcontainers.Container
}

// URL returns the HTTP base URL for the running MockServer container (e.g. "http://localhost:32769").
func (c *MockServerContainer) URL(ctx context.Context) (string, error) {
	host, err := c.Host(ctx)
	if err != nil {
		return "", fmt.Errorf("mockserver: failed to get host: %w", err)
	}
	port, err := c.MappedPort(ctx, "1080/tcp")
	if err != nil {
		return "", fmt.Errorf("mockserver: failed to get mapped port: %w", err)
	}
	return fmt.Sprintf("http://%s:%s", host, port.Port()), nil
}

// Client returns a MockServer control-plane client configured for the running
// container's mapped host and port. It is the Go equivalent of the Java module's
// getClient() helper.
//
//	client, err := ctr.Client(ctx)
//	if err != nil {
//	    log.Fatal(err)
//	}
//	_, err = client.When(
//	    mockserverclient.Request().Method("GET").Path("/hello"),
//	).Respond(
//	    mockserverclient.Response().StatusCode(200).Body("world"),
//	)
//
// The client is bundled via the mockserver-client-go/vN module (N = the release
// major). The "/vN" Semantic Import Versioning suffix on that module's path is
// what makes it resolvable as a normal dependency of this module for downstream
// `go get`.
func (c *MockServerContainer) Client(ctx context.Context) (*mockserverclient.Client, error) {
	host, err := c.Host(ctx)
	if err != nil {
		return nil, fmt.Errorf("mockserver: failed to get host: %w", err)
	}
	port, err := c.ServerPort(ctx)
	if err != nil {
		return nil, err
	}
	return mockserverclient.New(host, port), nil
}

// ServerPort returns the mapped host port for the MockServer container port 1080.
func (c *MockServerContainer) ServerPort(ctx context.Context) (int, error) {
	port, err := c.MappedPort(ctx, "1080/tcp")
	if err != nil {
		return 0, fmt.Errorf("mockserver: failed to get mapped port: %w", err)
	}
	return int(port.Num()), nil
}

// Run creates and starts a MockServer container. The image parameter specifies the Docker
// image to use (e.g. "mockserver/mockserver:mockserver-7.0.0"). Pass testcontainers
// CustomizeRequestOption values to configure the container request further.
//
// The container waits for an HTTP 200 response on PUT /mockserver/status before
// returning, ensuring MockServer is fully ready to receive expectations.
func Run(ctx context.Context, img string, opts ...testcontainers.ContainerCustomizer) (*MockServerContainer, error) {
	req := testcontainers.ContainerRequest{
		Image:        img,
		ExposedPorts: []string{DefaultPort},
		WaitingFor: wait.ForHTTP("/mockserver/status").
			WithMethod("PUT").
			WithPort("1080/tcp").
			WithStatusCodeMatcher(func(status int) bool {
				return status == 200
			}),
	}

	genericReq := testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	}

	for _, opt := range opts {
		if err := opt.Customize(&genericReq); err != nil {
			return nil, fmt.Errorf("mockserver: failed to apply option: %w", err)
		}
	}

	container, err := testcontainers.GenericContainer(ctx, genericReq)
	if err != nil {
		return nil, fmt.Errorf("mockserver: failed to start container: %w", err)
	}

	return &MockServerContainer{Container: container}, nil
}
