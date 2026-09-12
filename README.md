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

## Traffic simulator

A concurrent HTTP load generator (`simulation` package) drives the running
Gatekeeper server with `GET /api/resource` requests from deterministic
simulated clients (`sim-client-1`, `sim-client-2`, ...). It is a local
testing tool, not a general-purpose load tester: by default — and
currently without any override — it refuses any `--base-url` that isn't
`localhost`, `127.0.0.1`, or `::1`.

Start a server, then in another terminal:

```bash
mvn exec:java -Dexec.mainClass=com.aryandhere.ratelimiter.simulation.TrafficSimulatorApplication \
  -Dexec.args="--base-url http://localhost:8080 --client-count 20 --requests-per-client 50 --concurrency 8"
```

Supported flags: `--base-url`, `--client-count`, `--requests-per-client`,
`--concurrency`, `--request-timeout-seconds`, `--client-id-prefix`,
`--warmup-requests`. Warmup requests run first and are excluded from every
reported statistic. Work runs on a bounded, fixed-size thread pool
processed in batches (never an unbounded number of in-flight requests), and
every generous safe maximum (max concurrency, max client count, etc.) is
documented in `--help`-style usage output and enforced before anything
runs.

The simulator reports allowed/rate-limited/client-error/server-error/transport-error
counts (these always sum to the total measured requests), throughput,
latency percentiles, and *client-distribution consistency* — whether
otherwise-identical simulated clients happened to receive similar
treatment in this run. This is a distributional sanity check, not a claim
about demographic fairness.

## Direct algorithm benchmark

Separately, a local benchmark (`benchmark` package) calls the three
`RateLimiter` implementations directly — no HTTP involved — so algorithm
cost is never confused with HTTP overhead:

```bash
mvn exec:java -Dexec.mainClass=com.aryandhere.ratelimiter.benchmark.AlgorithmBenchmarkApplication \
  -Dexec.args="--clients 20 --operations-per-client 2000 --concurrency 4 --warmup-operations 5000 --trials 3"
```

Each trial gets a fresh limiter instance (per algorithm, per trial), plus a
separate warmup phase with its own throwaway limiter. Algorithm execution
order rotates by one position each trial so no algorithm consistently runs
first or last. Every trial uses the same synthetic per-client limit
(`operationsPerClient / 2`, with a window/refill long enough not to reset
mid-trial), so every algorithm's accept *and* reject paths are exercised
identically — this is a fixed benchmark harness parameter, not a
recommended production setting.

Results are written to `reports/benchmarks/` as timestamped CSV (machine-readable)
and Markdown (human-readable) files — a new file each run, never overwriting
a previous one. **Throughput** is operations per second during the measured
phase; **p50/p95/p99** are the 50th/95th/99th percentile latencies of
individual `decide()` calls (nearest-rank method, documented in
`LatencyStats`), converted from nanoseconds to milliseconds for display.

### Measured results (this machine, this run)

From [`reports/benchmarks/benchmark-20260912-085056.md`](reports/benchmarks/benchmark-20260912-085056.md)
(Apple Silicon Mac, Java 21.0.12.1, 10 processors; 20 clients × 2,000
operations/client, concurrency 4, 5,000 warmup operations, 3 trials;
median across trials):

| Algorithm | Median throughput (ops/s) | Median p50 (ms) | Median p95 (ms) | Median p99 (ms) |
|---|---|---|---|---|
| Fixed Window | 1,950,435 | 0.000 | 0.000 | 0.000 |
| Sliding Window Log | 1,651,840 | 0.000 | 0.000 | 0.000 |
| Token Bucket | 1,861,555 | 0.000 | 0.000 | 0.000 |

At this scale, a single in-memory `decide()` call is sub-microsecond, so
p50/p95/p99 round to `0.000` ms at the 3-decimal-place precision the
benchmark displays — that is the honest result, not a rounding bug; see the
full report for millisecond-scale `max` values and per-trial detail. These
numbers describe **this machine, this JVM, this one run** — they are not a
claim that any algorithm is universally faster, and they were not compared
across hardware or repeated over time. See the linked report's own
Interpretation and Limitations sections before drawing conclusions.

### HTTP results vs. direct algorithm results

The traffic simulator measures a full HTTP round trip (JSON encoding,
socket I/O, the JDK `HttpServer`'s dispatch) on top of the algorithm; the
direct benchmark measures only the algorithm call. Expect the simulator's
latencies to be dominated by HTTP/OS overhead, not by which algorithm is
selected — the direct benchmark above is what isolates algorithm cost.

### Practical tradeoffs

- **Fixed Window** is the simplest and cheapest, but permits a client to
  briefly send up to twice its intended rate across a window boundary.
- **Sliding Window Log** is precise — no boundary bursts — at the cost of
  storing a timestamp per accepted request per client.
- **Token Bucket** supports controlled bursts up to its capacity while
  still capping sustained throughput at the refill rate.

## Milestone status

**Milestone 3 (current): traffic simulator and algorithm benchmarks.**
This milestone adds the `simulation`, `benchmark`, and `stats` packages, and
the `reports/benchmarks/` directory of generated reports. It does **not**
include a frontend, Docker, distributed rate limiting, or a database —
those remain out of scope.

Nothing here has been evaluated for production security hardening,
distributed deployment, or scalability beyond a single process. Benchmark
numbers reflect one machine and one run each; see each report's own
Limitations section.

## Project layout

```
src/main/java/com/aryandhere/ratelimiter/
  GatekeeperApplication.java  Command-line entry point (HTTP server)
  core/        RateLimiter interface, RateLimitDecision, and the three algorithms
  time/        TimeSource abstraction and its system-clock implementation
  config/      CLI argument parsing, ServerConfig, and the RateLimiter factory
  http/        HTTP handlers, JSON response writing, and the server wrapper
  stats/       LatencyStats: shared percentile/latency computation
  simulation/  Concurrent HTTP traffic simulator and its CLI
  benchmark/   Direct algorithm benchmark, report writers, and its CLI
src/test/java/com/aryandhere/ratelimiter/
  core/        Unit and concurrency tests per algorithm, plus RateLimitDecision
  time/        TimeSource tests, including the ManualTimeSource test double
  config/      Argument parsing and factory tests
  http/        JSON encoding tests and end-to-end HTTP integration tests
  stats/       LatencyStats percentile tests
  simulation/  Argument/URL validation, and ephemeral-server integration tests
  benchmark/   Argument parsing, median calculation, runner, and report-writer tests
reports/benchmarks/  Generated CSV and Markdown benchmark reports (timestamped)
```

## Build and test

Requires Java 21 and Maven.

```bash
mvn test    # run the full test suite
mvn package # build the jar
```

## License

See [LICENSE](LICENSE).
