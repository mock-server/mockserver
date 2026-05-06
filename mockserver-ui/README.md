# mockserver-ui 

> A dashboard to view the expectations, requests, and logs in [MockServer](https://mock-server.com/)

[![Build status](https://badge.buildkite.com/a1d7b386b768855f167d5104bc4e71cd6176e84af4faf09024.svg?style=square&theme=slack)](https://buildkite.com/mockserver/mockserver-ui)

# Community

* Backlog:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://trello.com/b/dsfTCP46/mockserver" target="_blank"><img height="20px" src="https://mock-server.com/images/trello_badge-md.png" alt="Trello Backlog"></a>
* Feature Requests:&nbsp;&nbsp;&nbsp;<a href="https://github.com/mock-server/mockserver-monorepo/issues"><img height="20px" src="https://mock-server.com/images/GitHub_Logo-md.png" alt="Github Issues"></a>
* Issues / Bugs:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://github.com/mock-server/mockserver-monorepo/issues"><img height="20px" src="https://mock-server.com/images/GitHub_Logo-md.png" alt="Github Issues"></a>
* Discussions:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://github.com/mock-server/mockserver-monorepo/discussions"><img height="20px" src="https://mock-server.com/images/GitHub_Logo-md.png" alt="GitHub Discussions"></a>

## Getting Started
This node module is built using [Vite](https://vitejs.dev/) and TypeScript. It is not intended to be used standalone (except for development) and is bundled into [MockServer](https://mock-server.com/) on path `/mockserver/dashboard`, for example:
```
https://localhost:1080/mockserver/dashboard
```

For development this node module can be run using `npm start` and can be pointed at a running version of [MockServer](https://mock-server.com/) using `host`, `port` and `context` query parameters as required, for example: 
```
http://localhost:3000/?host=localhost&port=1080&context=
```

To run locally:
```bash
# 1. run node 
npm start
# 2. navigate to UI
open http://localhost:3000/?port=1080
```

## Contributing
In lieu of a formal styleguide, take care to maintain the existing coding style. Add unit tests for any new or changed functionality.
