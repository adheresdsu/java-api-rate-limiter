# Gatekeeper — Concurrent API Rate Limiter

Gatekeeper is a small, dependency-light Java library and HTTP server that
implements the core algorithms behind API rate limiting. It exists as a
portfolio project to demonstrate Java fundamentals — object-oriented
design, concurrency, data structures, HTTP, and testing — in a
self-contained, well-documented package rather than as a thin wrapper
around a framework.

## The problem

Any service exposed over the network needs a way to stop a single client
(a misbehaving script, a retry storm, an abusive user) from consuming a
disproportionate share of its capacity. A rate limiter answers one
question for every incoming request: *has this client already used up its
allowance, or should this request go through?*

That question sounds simple, but answering it correctly under concurrent
load — many threads asking it at once, for many different clients, without
serializing all of them behind a single lock — is the actual engineering
problem this project explores.

## Algorithms

Three independent, interchangeable implementations of the same
`RateLimiter` interface:

- **Fixed Window** — each client gets `maxRequests` per fixed-length
  window; the count resets the moment a request lands in a new window.
  Simple and cheap, but a client can send a full window's worth of
  requests at the tail end of one window and another full window's worth
  at the start of the next, briefly exceeding the intended rate at the
  boundary.

- **Sliding Window Log** — each client's recent request timestamps are
  kept in a log; a request is allowed only if fewer than `maxRequests`
  timestamps fall inside the trailing window. This is exact — no boundary
  effects — at the cost of memory proportional to the number of accepted
  requests per client within the window.

- **Token Bucket** — each client has a bucket of tokens, refilled over
  time at a fixed rate up to a capacity ceiling; every accepted request
  spends one token. This allows short bursts up to the bucket's capacity
  while still capping sustained long-term throughput at the refill rate.

All three are thread-safe using per-client locking (a `ConcurrentHashMap`
plus a lock scoped to each client's own state), not one global lock, so
requests for different clients don't serialize against each other. Time is
supplied through an injected `TimeSource` rather than read from the system
clock directly, so tests can advance time deterministically without
`Thread.sleep`.

Each decision from `RateLimiter.decide(clientId)` returns a `RateLimitDecision`
record: whether the request was allowed, the configured `limit`, `remaining`
(never negative), and — only when rejected — a `retryAfter` duration. The
simpler `tryAcquire(clientId)` boolean is a default method built on top of
`decide`, so nothing calls into the limiter twice per request.

**Retry-After rounding:** each algorithm computes how long until the client's
next request would be allowed, in milliseconds, then rounds that value **up**
to the nearest whole second (never down — rounding down could tell a client
to retry before it's actually eligible). Fixed window rounds up the time
until the window resets; sliding window log rounds up the time until the
oldest in-window timestamp expires; token bucket rounds up the time needed to
accumulate one more token at the configured refill rate.

## HTTP server

A minimal HTTP server, built on the JDK's own `com.sun.net.httpserver.HttpServer`
(no Spring Boot, no external web framework), exposes the rate limiter over
two endpoints. The HTTP layer (`http` package) only translates requests to
and from `RateLimiter` calls — it does not duplicate any algorithm logic.

### `GET /health`

Never rate limited. Always returns:

```
200 OK
Content-Type: application/json; charset=utf-8

{"status":"ok"}
```

### `GET /api/resource`

The protected demonstration endpoint. Requires an `X-Client-Id` header
(not a query parameter):

| Condition | Status | Notes |
|---|---|---|
| Missing `X-Client-Id` | `400` | `{"error":"..."}` |
| Blank `X-Client-Id` | `400` | `{"error":"..."}` |
| Allowed | `200` | `X-RateLimit-Limit`, `X-RateLimit-Remaining` headers set |
| Rejected | `429` | same headers, plus `Retry-After` (whole seconds) |
| Method other than `GET` | `405` | `Allow: GET` header set |
| Unexpected server error | `500` | generic body, no stack trace or internal detail |

All responses use `Content-Type: application/json; charset=utf-8`.

### curl examples

```bash
curl -i http://localhost:8080/health

curl -i -H "X-Client-Id: alice" http://localhost:8080/api/resource

# Missing header
curl -i http://localhost:8080/api/resource

# Trigger a 429 by exceeding the configured limit
for i in $(seq 1 30); do curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-Client-Id: alice" http://localhost:8080/api/resource; done
```

## Configuration

The server is configured entirely from the command line; every flag has a
safe default and unknown/invalid flags are rejected with a usage message
instead of being silently ignored.

| Flag | Default | Applies to |
|---|---|---|
| `--port` | `8080` | all |
| `--algorithm` | `token-bucket` | all (`fixed-window`, `sliding-window`, `token-bucket`) |
| `--limit` | `100` | fixed-window, sliding-window |
| `--window-seconds` | `60` | fixed-window, sliding-window |
| `--capacity` | `20` | token-bucket |
| `--refill-tokens` | `10` | token-bucket |
| `--refill-period-seconds` | `1` | token-bucket |
| `--workers` | `16` | all (HTTP server thread pool size) |

## Starting and stopping the server

```bash
mvn exec:java -Dexec.args="--algorithm token-bucket --port 8080"
```

or, after `mvn package`, as a runnable jar:

```bash
java -jar target/gatekeeper-0.1.0-SNAPSHOT.jar --algorithm token-bucket --port 8080
```

The server prints its listening address and active configuration on
startup. Stop it with `Ctrl+C`; a shutdown hook stops accepting new
connections, gives in-flight requests a brief grace period, then shuts down
the worker thread pool.

## Milestone status

**Milestone 2 (current): local HTTP server around the existing algorithms.**
This milestone adds the `http` and `config` packages, the `RateLimitDecision`
extension to `RateLimiter`, and the `GatekeeperApplication` entry point. It
does **not** include a traffic simulator, Docker configuration, a frontend,
or performance benchmarking — those are future milestones.

No throughput or latency numbers have been measured yet. Any performance
claims will be added only once real benchmarks exist. Nothing here has been
evaluated for production security hardening, distributed deployment, or
scalability beyond a single process.

## Project layout

```
src/main/java/com/aryandhere/ratelimiter/
  GatekeeperApplication.java  Command-line entry point
  core/    RateLimiter interface, RateLimitDecision, and the three algorithms
  time/    TimeSource abstraction and its system-clock implementation
  config/  CLI argument parsing, ServerConfig, and the RateLimiter factory
  http/    HTTP handlers, JSON response writing, and the server wrapper
src/test/java/com/aryandhere/ratelimiter/
  core/    Unit and concurrency tests per algorithm, plus RateLimitDecision
  time/    TimeSource tests, including the ManualTimeSource test double
  config/  Argument parsing and factory tests
  http/    JSON encoding tests and end-to-end HTTP integration tests
```

## Build and test

Requires Java 21 and Maven.

```bash
mvn test    # run the full test suite
mvn package # build the jar
```

## License

See [LICENSE](LICENSE).
