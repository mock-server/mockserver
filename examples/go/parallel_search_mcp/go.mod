module github.com/mock-server/mockserver-monorepo/examples/go/parallel_search_mcp

go 1.21

require github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9 v9.0.0

require github.com/gorilla/websocket v1.5.3 // indirect

replace github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9 => ../../../mockserver-client-go
