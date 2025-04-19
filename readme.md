# ChaosBridge
A Chaos Testing Tool for Building Resilient Systems

<img src="./cover.png" alt="ChaosBridge Logo" width="300">

### Overview
ChaosBridge is a lightweight chaos testing tool built using Java Sockets and Virtual Threads (Project Loom). It helps developers simulate real-world failures (latency, packet loss, disconnections) to improve system reliability.

# Chaos Bridge API Documentation

This document describes the REST endpoints exposed by the `UiController` in the Chaos Bridge application.

## Base URL

All API endpoints are relative to the base URL of your application. By default, this is: http://localhost:9090

## Endpoints

### 1. Get All Proxies (`GET /proxy`)

**Description:** Retrieves a list of all active proxies.

**Method:** `GET`

**URL:** `/proxy`

**Request Body:** None

**Response:**

*   **Status Code:** `200 OK`
*   **Content Type:** `application/json`
*   **Body:** A JSON object with a `data` array containing proxy information.

**Example Usage:**
```bash curl http://localhost:9090/ proxy```

```json { "data": [ { "port": "8080", "serverHost": "example.com", "serverPort": "80", "key": "8080:example.com:80"  }, { "port": "8081", "serverHost": "test.com", "serverPort": "80", "key": "8081:test.com:80" } ] }```

### 2. Create a New Proxy (`POST /proxy`)

**Description:** Creates and starts a new proxy server.

**Method:** `POST`

**URL:** `/proxy`

**Request Body:**

*   **Content Type:** `application/json`
*   **Body:** A JSON object with the following fields:
    *   `port` (string, required): The port on which the proxy will listen.
    *   `serverHost` (string, required): The hostname or IP address of the target server.
    *   `serverPort` (string, required): The port of the target server.
    
```json { "port": "8082", "serverHost": "another.com", "serverPort": "80" }```

**Response:**

*   **Status Code:** `200 OK`
*   **Content Type:** `application/json`
*   **Body:** A JSON object with the following fields:
    *   `status` (string): "success"
    *   `key` (string): The unique key for the created proxy (e.g., "8082:another.com:80").
    *   `message` (string): A success message.

```json { "status": "success", "key": "8082:another.com:80" ,  "message": "Proxy started successfully {port=8082, serverHost=another.com,  serverPort=80}" }```

**Example Usage:**

```bash curl -X POST -H "Content-Type: application/json" -d '{"port": "8082", "serverHost": "another.com", "serverPort": "80"}' http://localhost:9090/ proxy```

### 3. Get Chaos Configurations (`GET /chaosConfig`)

**Description:** Retrieves a list of available chaos configurations.

**Method:** `GET`

**URL:** `/chaosConfig`

**Request Body:** None

**Response:**

*   **Status Code:** `200 OK`
*   **Content Type:** `application/json`
*   **Body:** A JSON array of chaos configuration objects.

```json [ { "type": "BANDWIDTH", "fields": [ "bytePerSecond" ] }, { "type": "LATENCY", "fields": [ "latency" ] } ]```

**Example Usage:**

```bash curl http://localhost:9090/ chaosConfig```

### 4. Delete a Proxy (`DELETE /proxy/{key}`)

**Description:** Stops and deletes a proxy server identified by its key.

**Method:** `DELETE`

**URL:** `/proxy/{key}`

**Path Parameters:**

*   `key` (string, required): The unique key of the proxy to delete (e.g., "8080:example.com:80").

**Request Body:** None

**Response:**

*   **Status Code:** `200 OK`
*   **Content Type:** `application/json`
*   **Body:** A JSON object with the following fields:
    *   `status` (string): "success"
    *   `message` (string): A success message.
    
```json { "status": "success", "message": "Stopped Server 8080:example.com:80 data " }```

**Example Usage:**

```bash curl -X DELETE http://localhost:9090/ proxy/ 8080: example. com: 80```

### 5. Apply Chaos to a Proxy (`POST /addChaos/{key}`)

**Description:** Applies a chaos configuration to a specific proxy.

**Method:** `POST`

**URL:** `/addChaos/{key}`

**Path Parameters:**

*   `key` (string, required): The unique key of the proxy to which chaos will be applied (e.g., "8080:example.com:80").

**Request Body:**

*   **Content Type:** `application/x-www-form-urlencoded`
*   **Body:** Form data with the following fields:
    *   `chaosType` (string, required): The type of chaos to apply (e.g., "BANDWIDTH", "LATENCY").
    *   `line` (string, required): The line to apply the chaos (e.g., "upstream", "downstream").
    *   Additional fields based on the selected `chaosType` (e.g., `bytePerSecond` for "BANDWIDTH", `latency` for "LATENCY").

**Response:**

*   **Status Code:** `200 OK`
*   **Content Type:** `application/json`
*   **Body:** A JSON object with the following fields:
    *   `status` (string): "success"
    *   `message` (string): A success message.

```json { "status": "success", "message": "Chaos Added for 8080:example.com:80 data {chaosType=BANDWIDTH,  line=upstream, bytePerSecond=1024}"  }```

**Example Usage:**

```bash curl -X POST -H "Content-Type: application/x-www-form- urlencoded"  -d "chaosType=BANDWIDTH& line= upstream& bytePerSecond= 1024"  http://localhost:9090/ addChaos/ 8080: example. com: 80```