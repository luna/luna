---
layout: developer-doc
title: Language Server HTTP Endpoints
category: language-server
tags: [language-server, protocol, specification]
order: 6
---

# HTTP Endpoints

Language server exposes a number of HTTP endpoints on the same socket as the
JSONRPC protocol.

<!-- MarkdownTOC levels="2" autolink="true" indent="    " -->

- [`/_health`](#---health-)
- [`/_health/readiness`](#---health-readiness-)
- [`/_health/liveness`](#---health-liveness-)
- [`/_idle`](#---idle-)

<!-- /MarkdownTOC -->

## `/_health`

HTTP endpoint that provides basic health checking capabilities.

### `GET | HEAD`

Returns `200 OK` when the server is started and `500 Internal Server Error`
otherwise.

#### Request

```text
> GET /_health HTTP/1.1
> Host: localhost:63597
> User-Agent: curl/7.77.0
> Accept: */*
```

#### Response

```text
< HTTP/1.1 200 OK
< Server: akka-http/10.2.0-RC1
< Date: Fri, 09 Jul 2021 15:16:16 GMT
< Content-Type: text/plain; charset=UTF-8
< Content-Length: 2
<
OK
```

## `/_health/readiness`

The server readiness probe.

### `GET | HEAD`

Returns `200 OK` when the server is initialized and `500 Internal Server Error`
otherwise.

#### Request

```text
> GET /_health/readiness HTTP/1.1
> Host: localhost:63597
> User-Agent: curl/7.77.0
> Accept: */*
```

#### Response

```text
< HTTP/1.1 200 OK
< Server: akka-http/10.2.0-RC1
< Date: Fri, 09 Jul 2021 15:30:53 GMT
< Content-Type: text/plain; charset=UTF-8
< Content-Length: 2
<
OK
```

## `/_health/liveness`

The server liveness probe.

### `GET | HEAD`

Checks if all the server subsystems are functioning and returns `200 OK` or
`500 Internal Server Error` otherwise.

#### Request

```text
> GET /_health/liveness HTTP/1.1
> Host: localhost:60339
> User-Agent: curl/7.77.0
> Accept: */*
```

#### Response

```text
< HTTP/1.1 200 OK
< Server: akka-http/10.2.0-RC1
< Date: Fri, 09 Jul 2021 15:35:43 GMT
< Content-Type: text/plain; charset=UTF-8
< Content-Length: 2
<
OK
```

## `/_idle`

The server idleness probe.

### `GET`

Return the amount of time the language server is idle.

#### Request

```text
> GET /_idle HTTP/1.1
> Host: localhost:60339
> User-Agent: curl/7.77.0
> Accept: */*
```

#### Response

```text
< HTTP/1.1 200 OK
< Server: akka-http/10.2.0-RC1
< Date: Fri, 09 Jul 2021 15:44:51 GMT
< Content-Type: application/json
< Content-Length: 21
<
{"idle_time_sec":58}
```
