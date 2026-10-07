# ChaosBridge

A Chaos Testing Tool for Building Resilient Systems

<img src="./cover.png" alt="ChaosBridge Logo" width="300">

[![Build](https://img.shields.io/badge/JDK-21%2B-blue)](https://adoptium.net/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.2.1-14a3a3)](https://micronaut.io/)
[![Documentation](https://img.shields.io/badge/docs-GitHub%20Pages-3ecf8e)](https://sahariardev.github.io/ChaosBridge/)

### Overview

ChaosBridge is a lightweight chaos testing tool built using Java Sockets and Virtual Threads
(Project Loom). It sits between your client and a target server and simulates real-world failures —
latency, packet loss and bandwidth throttling — so you can prove your systems stay reliable when the
network does not.

> 📚 **Full documentation:** https://sahariardev.github.io/ChaosBridge/
> (source lives in [`docs/`](./docs) and is deployed to GitHub Pages)

### Features

- **Wire-level chaos** — failure injection happens on the raw byte stream, independent of protocol.
- **Virtual threads** — one virtual thread per connection via Project Loom, so concurrency is cheap.
- **REST controlled** — start proxies and attach chaos profiles at runtime for use in CI pipelines.
- **Directional** — apply chaos to `upstream` (client → server), `downstream`, or both.
- **Small footprint** — an embedded Micronaut server and a concurrent in-memory store, no database.
- **Built-in console** — manage proxies and chaos profiles from the browser.

### Requirements

- JDK 21 or newer
- No external services

## Quick start

```bash
# Start the control plane (API + web console)
./gradlew run
```

The API and console are available at `http://localhost:9091`.

```bash
# 1. Start a proxy: localhost:8080 -> example.com:80
curl -X POST http://localhost:9091/proxy \
  -H "Content-Type: application/json" \
  -d '{"port":"8080","serverHost":"example.com","serverPort":"80"}'

# 2. Add 2 seconds of downstream latency
curl -X POST http://localhost:9091/addChaos/8080:example.com:80 \
  -H "Content-Type: application/json" \
  -d '{"chaosType":"LATENCY","line":"downstream","latency":"2"}'

# 3. Point your client at localhost:8080 and observe the behaviour

# 4. Stop the proxy when you are done
curl -X DELETE http://localhost:9091/proxy/8080:example.com:80
```

## Docker

A published image is available on Docker Hub:

```bash
docker run --rm -p 9091:9091 sahariardev/chaosbridge:latest
```

The console is then available at `http://localhost:9091`. To build the image yourself:

```bash
docker build -t sahariardev/chaosbridge:latest .
docker run --rm -p 9091:9091 sahariardev/chaosbridge:latest
```

## API reference

Base URL: `http://localhost:{port}` (default `http://localhost:9091`). All responses are JSON.

| Method   | Path                              | Description                                  |
|----------|-----------------------------------|----------------------------------------------|
| `GET`    | `/`                               | Web console (Velocity + Bootstrap).          |
| `GET`    | `/proxy`                          | List all active proxies.                     |
| `POST`   | `/proxy`                          | Create and start a proxy.                    |
| `DELETE` | `/proxy/{key}`                    | Stop and remove a proxy.                     |
| `GET`    | `/chaosConfig`                    | List available chaos types and their fields. |
| `POST`   | `/addChaos/{key}`                 | Attach a chaos profile to a proxy.           |
| `GET`    | `/allChaos/{key}`                 | List a proxy's chaos profiles.               |
| `DELETE` | `/removeChaos/{key}/{chaosId}`    | Remove a chaos profile from a proxy.         |
| `GET`    | `/metrics`                        | Aggregate, per-host and per-proxy metrics.   |
| `GET`    | `/metrics/{key}`                  | Metrics and active chaos for one proxy.      |
| `GET`    | `/prometheus`                     | Prometheus text exposition format.           |

A proxy is identified by the key `{port}:{serverHost}:{serverPort}`, which is returned when it is
created.

### 1. Get all proxies — `GET /proxy`

```bash
curl http://localhost:9091/proxy
```

```json
{
  "data": [
    { "port": "8080", "serverHost": "example.com", "serverPort": "80", "key": "8080:example.com:80" }
  ]
}
```

### 2. Create a proxy — `POST /proxy`

Request body (`application/json`):

| Field        | Type   | Required | Description                  |
|--------------|--------|----------|------------------------------|
| `port`       | string | yes      | Port the proxy listens on.   |
| `serverHost` | string | yes      | Target hostname or IP.       |
| `serverPort` | string | yes      | Target port.                 |

```bash
curl -X POST http://localhost:9091/proxy \
  -H "Content-Type: application/json" \
  -d '{"port":"8082","serverHost":"another.com","serverPort":"80"}'
```

```json
{ "status": "success", "key": "8082:another.com:80", "message": "Proxy started successfully {port=8082, serverHost=another.com, serverPort=80}" }
```

### 3. Delete a proxy — `DELETE /proxy/{key}`

```bash
curl -X DELETE http://localhost:9091/proxy/8080:example.com:80
```

```json
{ "status": "success", "message": "Stopped Server 8080:example.com:80 data " }
```

### 4. Get chaos configurations — `GET /chaosConfig`

```bash
curl http://localhost:9091/chaosConfig
```

```json
[
  { "type": "BANDWIDTH",   "fields": ["bytePerSecond"] },
  { "type": "LATENCY",     "fields": ["latency"] },
  { "type": "PACKET_LOSS", "fields": ["packetLossRate"] }
]
```

### 5. Apply chaos to a proxy — `POST /addChaos/{key}`

Accepts `application/json` or `application/x-www-form-urlencoded`.

| Field            | Type   | Required        | Description                              |
|------------------|--------|-----------------|------------------------------------------|
| `chaosType`      | string | yes             | `BANDWIDTH`, `LATENCY` or `PACKET_LOSS`. |
| `line`           | string | yes             | `upstream` or `downstream`.              |
| `bytePerSecond`  | number | for `BANDWIDTH` | Max bytes forwarded per second.          |
| `latency`        | number | for `LATENCY`   | Delay in seconds.                        |
| `packetLossRate` | number | for `PACKET_LOSS` | Drop probability between 0.0 and 1.0.  |

```bash
curl -X POST http://localhost:9091/addChaos/8080:example.com:80 \
  -H "Content-Type: application/json" \
  -d '{"chaosType":"PACKET_LOSS","line":"upstream","packetLossRate":"0.3"}'
```

```json
{ "status": "success", "message": "Chaos Added for 8080:example.com:80 data {chaosType=PACKET_LOSS, line=upstream, packetLossRate=0.3}" }
```

### 6. List chaos profiles — `GET /allChaos/{key}`

```bash
curl http://localhost:9091/allChaos/8080:example.com:80
```

```json
{
  "status": "success",
  "message": [
    { "id": "5f0c9c1e-...", "type": "PACKET_LOSS", "line": "upstream", "packetLossRate": 0.3 }
  ]
}
```

### 7. Remove a chaos profile — `DELETE /removeChaos/{key}/{chaosId}`

```bash
curl -X DELETE http://localhost:9091/removeChaos/8080:example.com:80/5f0c9c1e-...
```

```json
{ "status": "success", "message": "Removed Chaos for 8080:example.com:80" }
```

### Error responses

Invalid input is rejected with `400 Bad Request`; chaos attached to an unknown proxy returns
`404 Not Found`. Both use the same shape:

```json
{ "status": "error", "message": "..." }
```

## Chaos types

| Type          | Field            | Effect                                                       |
|---------------|------------------|--------------------------------------------------------------|
| `BANDWIDTH`   | `bytePerSecond`  | Forwards at most one buffer of that size each second.        |
| `LATENCY`     | `latency`        | Delays each chunk by the given number of seconds.            |
| `PACKET_LOSS` | `packetLossRate` | Drops chunks with the given probability (`1.0` = drop all).  |

`line` selects the affected direction: `upstream` (client → server) or `downstream` (server → client).

## Metrics & dashboard

The web console at `http://localhost:9091/` is a live dashboard showing active proxies, running
chaos and traffic metrics. The same data is available via the API:

- `GET /metrics` — totals, a per-host roll-up and a row per proxy.
- `GET /metrics/{key}` — detailed counters and the active chaos list for one proxy.
- `GET /prometheus` — Prometheus text exposition for scraping.

Counters include total/active/failed connections, upstream and downstream bytes, dropped chunks
(packet loss), delayed chunks (latency), throttled chunks (bandwidth), attached chaos profiles and
uptime.

```bash
curl http://localhost:9091/metrics
curl http://localhost:9091/prometheus
```

## Configuration

`src/main/resources/application.yml`:

```yaml
micronaut:
  server:
    port: 9091
  router:
    static-resources:
      default:
        enabled: true
        mapping: /**
        paths: classpath:static

jackson:
  serialization-inclusion: NON_ABSENT
```

The log file location defaults to `logs/chaosBridge.log` and can be overridden with the
`chaosBridge.log.path` system property.

## Testing

The test suite boots a real Micronaut server and real TCP sockets, so chaos is verified on the wire.

```bash
./gradlew test
```

- `ApiControllerIntegrationTest` — drives every REST endpoint over HTTP.
- `ProxyChaosIntegrationTest` — end-to-end proxy with latency, packet loss and bandwidth chaos.
- `ServerTest` — raw proxy lifecycle (forwarding, port release, duplicate bind).
- `ChaosFactoryTest` / `StoreTest` — numeric type handling and the concurrent store.

## Project structure

```
src/main/java/com/github/sahariardev/
├── Application.java              # Micronaut entry point
├── StreamUtil.java               # buffered stream copy helper
├── chaos/                        # chaos profiles + factory + config metadata
├── common/                       # in-memory Store and constants
├── pipeline/                     # per-direction chaos pipeline
├── proxy/                        # TCP proxy server
└── web/                          # REST controller + executor factory
src/main/resources/
├── views/                        # Velocity templates for the web console
└── static/                       # console JavaScript
docs/                             # static GitHub Pages documentation site
```

## Documentation site

The `docs/` folder contains a self-contained, Supabase-themed static site. Preview it locally with:

```bash
python -m http.server 8000 --directory docs
# then open http://localhost:8000
```

It is deployed automatically to GitHub Pages by `.github/workflows/pages.yml` on every push to
`main` that touches `docs/`. Alternatively, enable **Settings → Pages → Build from `main` / `/docs`**.
