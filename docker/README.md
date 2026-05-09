# MockServer Docker Container

> Docker container for running [MockServer](https://www.mock-server.com) — an HTTP(S) mock server and proxy for testing

[![Docker Hub](https://img.shields.io/docker/pulls/mockserver/mockserver.svg)](https://hub.docker.com/r/mockserver/mockserver/)

## Quick Start

```bash
docker pull mockserver/mockserver
docker run -d --rm --name mockserver -p 1080:1080 mockserver/mockserver
```

## Usage

Run in daemon mode (no log output):

```bash
docker run -d --rm --name mockserver -p <serverPort>:1080 mockserver/mockserver
```

Or run in foreground (log output to console):

```bash
docker run --rm --name mockserver -p <serverPort>:1080 mockserver/mockserver
```

Stop the container:

```bash
docker stop mockserver && docker rm mockserver
```

## Configuration

The default command executed when the container runs is:

```bash
-logLevel INFO -serverPort 1080
```

Override command line options:

```bash
docker run --rm --name mockserver -p 1090:1090 mockserver/mockserver -logLevel INFO -serverPort 1090 -proxyRemotePort 443 -proxyRemoteHost mock-server.com
```

All [configuration properties](https://mock-server.com/mock_server/configuration_properties.html) can also be set via environment variables in a `docker-compose.yml`:

```yaml
version: "2.4"
services:
  mockServer:
    image: mockserver/mockserver:latest
    ports:
    - 1080:1080
    environment:
    - MOCKSERVER_MAX_EXPECTATIONS=100
    - MOCKSERVER_MAX_HEADER_SIZE=8192
```

For more configuration options see the [Docker documentation](https://www.mock-server.com/where/docker.html).

## Community, Issues & Contributing

See the [main MockServer README](../README.md) for community links, how to report issues, and contribution guidelines.
