// Package mockserver provides a Testcontainers module for MockServer.
//
// MockServer (https://www.mock-server.com) is an open-source HTTP(S) mock server and
// proxy for testing. This module starts the official mockserver/mockserver Docker image,
// waits for the /mockserver/status endpoint to respond, and provides helpers to access
// the container's URL and mapped port.
//
// # Quick Start
//
//	import (
//	    "context"
//	    "log"
//
//	    mockserver "github.com/mock-server/mockserver-monorepo/mockserver-testcontainers/go/v9"
//	)
//
//	func main() {
//	    ctx := context.Background()
//	    ctr, err := mockserver.Run(ctx, "mockserver/mockserver:mockserver-7.0.0")
//	    if err != nil {
//	        log.Fatal(err)
//	    }
//	    defer ctr.Terminate(ctx)
//
//	    url, err := ctr.URL(ctx)
//	    if err != nil {
//	        log.Fatal(err)
//	    }
//	    log.Printf("MockServer running at %s", url)
//	}
package mockserver
