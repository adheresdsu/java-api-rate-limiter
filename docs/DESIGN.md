# Gatekeeper — Technical Design

This document explains how Gatekeeper is put together and why, at the
level of "what would a new contributor need to know before changing this
code." It describes the implementation as it actually exists; see the
[README](../README.md) for how to run and use the project.

## Package responsibilities

| Package | Responsibility |
|---|---|
| `core` | The `RateLimiter` interface, the `RateLimitDecision` record, and the three algorithm implementations. No I/O, no HTTP, no CLI parsing — pure decision logic. |
| `time` | `TimeSource`, an abstraction over "the current time," and its production implementation (`SystemTimeSource`). |
| `config` | CLI argument parsing (`ArgsParser`), the resulting `ServerConfig`, and `RateLimiterFactory`, which turns a `ServerConfig` into a concrete `RateLimiter`. |
| `http` | The HTTP server wrapper (`GatekeeperHttpServer`), request handlers, and JSON response writing. Translates between HTTP and `RateLimiter` calls; contains no algorithm logic of its own. |
| `stats` | `LatencyStats`, shared percentile/latency math used by the benchmark. |
| `simulation` | The concurrent HTTP traffic generator and its CLI, including the localhost-only safety restriction. |
| `benchmark` | The direct (non-HTTP) algorithm benchmark, its CLI, and CSV/Markdown report writers. |

`GatekeeperApplication` (top-level) is the only class that wires these
packages together for the server: it parses config, builds a rate limiter,
starts the HTTP server, and registers a shutdown hook.

## Request lifecycle

For `GET /api/resource`:

1. `GatekeeperHttpServer` (built on the JDK's `com.sun.net.httpserver.HttpServer`)
   dispatches the request to `ResourceHandler` on a thread from a fixed-size
   worker pool (`Executors.newFixedThreadPool(workers)`), created once at
   startup and shared by every request.
2. `ResourceHandler` checks the HTTP method (rejecting anything but `GET`
   with `405`), then reads the `X-Client-Id` header, rejecting a missing or
   blank value with `400` before ever touching the rate limiter.
3. It calls `RateLimiter.decide(clientId)` **exactly once** per request.
   The returned `RateLimitDecision` (allowed, limit, remaining, and — only
   when rejected — `retryAfter`) drives both the response headers and the
   status code, so the limiter is never consulted twice for one request and
   the headers can never disagree with the status.
4. `HandlerSupport.safely(...)` wraps the handler body so an unexpected
   exception becomes a generic `500` response instead of leaking a stack
   trace or crashing the dispatch thread.
5. `JsonSupport.write` sets `Content-Type: application/json; charset=utf-8`,
   writes the body as UTF-8 bytes, and closes the response stream.

`GET /health` is a separate, unconditional handler (`HealthHandler`) that
never calls into the rate limiter, so `/health` cannot itself be rate
limited or throttled by traffic against `/api/resource`.

## Thread-safety strategy

Each algorithm keeps its per-client state in a `ConcurrentHashMap<String, ...>`
keyed by client ID, with `computeIfAbsent` used to create a client's first
entry atomically. All mutation of one client's state after that happens
inside a lock scoped to that client alone:

- **Fixed Window** and **Token Bucket** use a private inner class
  (`Window`, `Bucket`) whose `decide(...)` method is a `synchronized`
  instance method, so the lock is the object itself — one lock per client.
- **Sliding Window Log** stores a raw `Deque<Long>` and synchronizes
  explicitly on that deque inside `decide(...)`.

This is a deliberate choice over one global lock: two requests for two
different clients never block each other, because they synchronize on two
different objects. Concurrency tests in `src/test/java/.../core/` assert
this directly — many threads hammering the same client never observe more
than the configured limit of accepted requests.

## Why state is isolated per client

Rate limiting is inherently a per-client question ("has *this* client used
up its allowance?"). Sharing one counter, window, or bucket across clients
would either let one client's traffic count against another's limit, or
force all clients through a single lock — serializing unrelated requests
for no reason. Keying by client ID and locking only within a client's own
entry gives correct isolation without over-synchronizing.

## Why a bounded executor is used

`GatekeeperHttpServer` creates one `Executors.newFixedThreadPool(workers)`
at startup and passes it to `HttpServer.setExecutor(...)`. A fixed size
(rather than an unbounded cached pool) means a burst of concurrent
connections queues up on the OS socket backlog and the fixed pool instead
of spawning unbounded threads, which would let a request flood exhaust
memory or thread-scheduling capacity — the rate limiter itself would be
correct, but the server underneath it would fall over first. The same
reasoning applies to `BenchmarkRunner` and the traffic simulator, both of
which also use fixed-size executors and process work in bounded batches
(`executeBatched`) rather than submitting every task at once.

## Fixed Window: behavior and boundary bursts

Each client's window is anchored to that client's first request, not to
wall-clock epoch boundaries. `decide()` resets the count to zero the moment
a request lands `windowMillis` or more after `windowStart`. Because the
reset is abrupt, a client can send `maxRequests` at the very end of one
window and another `maxRequests` at the very start of the next — up to
`2 * maxRequests` within a span far shorter than one window. This is the
algorithm's known weakness; `SlidingWindowLogRateLimiter` exists
specifically to avoid it.

## Sliding Window Log: accuracy and memory cost

Every accepted request's timestamp is appended to a per-client deque.
Before deciding, `decide()` evicts timestamps older than `now - windowMillis`
from the front of the deque, then allows the request only if fewer than
`maxRequests` timestamps remain. Because eviction and counting both use
exact timestamps rather than bucketed windows, no rolling window of the
configured size can ever contain more than `maxRequests` accepted
requests — there is no boundary case to exploit. The cost is memory: the
deque holds one `Long` per accepted request until it ages out, so a
high-limit, long-window, high-traffic client keeps a correspondingly large
log resident in memory. Fixed Window and Token Bucket use O(1) space per
client regardless of traffic.

## Token Bucket: refill behavior

Each client's bucket starts full (`capacity` tokens) and is refilled
*lazily* — there is no background thread ticking the clock. `refill()` is
called at the start of every `decide()` and adds
`(elapsedMillis / 1000.0) * refillRatePerSecond` tokens, capped at
`capacity`, based on the time elapsed since the bucket was last touched.
An accepted request consumes exactly one token. This allows a client that
has been idle to burst up to `capacity` requests instantly, while capping
sustained throughput at `refillRatePerSecond` requests per second once the
bucket is empty.

## Time-source injection

Every algorithm receives a `TimeSource` in its constructor rather than
calling `System.currentTimeMillis()` directly. `SystemTimeSource` is the
production implementation; tests use a manually-advanced test double
(`ManualTimeSource`) so window resets, token refills, and log eviction can
be exercised deterministically — advancing simulated time by an exact
number of milliseconds — instead of relying on `Thread.sleep` and real
wall-clock timing, which would make tests slow and flaky.

## Decision metadata and Retry-After calculation

`RateLimitDecision` is a record: `allowed`, `limit`, `remaining` (never
negative), and `retryAfter` (a `Duration`, present only when rejected).
Each algorithm computes how many milliseconds must elapse before the next
request would be allowed — time until the window resets (Fixed Window),
time until the oldest in-window timestamp expires (Sliding Window Log), or
time to accumulate one more token at the refill rate (Token Bucket) — and
passes that to `RetryAfter.ceilSeconds(millis)`, which rounds **up** to the
nearest whole second:

```java
long seconds = (clampedMillis + 999) / 1000;
```

Rounding up (never down) is deliberate: a client that waits the returned
number of seconds is guaranteed to have waited at least as long as
required. Rounding down could tell a client to retry before it is actually
eligible, causing an immediate second rejection.

## Simulator safety restrictions

`LocalTargetValidator.requireLocalHttpBaseUri(...)` rejects any `--base-url`
that is not a plain `http` URL, with no user-info, query string, fragment,
or path, whose host is exactly `localhost`, `127.0.0.1`, `::1` (or its
bracketed/expanded forms). There is no flag or configuration override to
target a remote host — this is enforced in code, not just documented,
because the simulator is a load-generation tool and should never be
repurposable as a way to direct traffic at an arbitrary third-party host.
`SimulatorArgsParser` additionally enforces generous but finite maximums on
concurrency, client count, and requests per client, documented in its
`--help` usage text, so a typo in a flag value cannot accidentally launch
an unbounded number of threads or requests.

## Benchmark methodology

`BenchmarkRunner` calls each `RateLimiter` implementation's `decide()`
directly — no HTTP — so algorithm cost is never confounded with HTTP
dispatch, JSON encoding, or socket I/O. For every algorithm and trial:

- A **fresh limiter instance** is constructed (no state carried over
  between trials or between algorithms).
- A **synthetic per-client limit** of `max(1, operationsPerClient / 2)` is
  used, with a window (`BENCHMARK_WINDOW`, one hour) or refill rate
  (`NEGLIGIBLE_REFILL_RATE_PER_SECOND`) chosen to be effectively static for
  the duration of one trial. This makes roughly half of each client's
  operations accepted and half rejected, so every algorithm's accept *and*
  reject code paths are exercised identically — a benchmark-harness
  parameter, not a production recommendation.
- **Algorithm order rotates by one position each trial** (`rotatedOrder`)
  so no single algorithm consistently runs first (and benefits from
  cooler caches/less GC pressure) or last.
- Each (trial, algorithm) pair runs a **separate warmup phase** first,
  against its own throwaway limiter instance, whose results are discarded
  before the timed run begins.
- Work is submitted to a fixed-size executor in bounded batches
  (`concurrency * 4` tasks per batch via `executeBatched`), not all at
  once, so the number of in-flight tasks stays bounded regardless of the
  total operation count.
- Per-operation latency is measured with `System.nanoTime()` immediately
  around the `decide()` call; `LatencyStats` computes p50/p95/p99 using the
  nearest-rank method over the collected latencies for the trial.
- Across trials, `Median summary` in each report takes the median of each
  metric (not the mean), which is less sensitive to one outlier trial
  affected by JIT warm-up or GC pauses.

## Known limitations

- **Single-process, in-memory only.** All rate-limiter state lives in one
  JVM's heap; there is no shared or distributed state.
- **No persistence.** Restarting the process resets every client's
  allowance.
- **No per-route configuration.** One algorithm and one configuration
  apply to the whole server for its lifetime.
- **Benchmark and simulator results are machine- and run-specific.** They
  are useful for understanding relative algorithm behavior on one machine,
  not as an absolute or cross-hardware performance claim.
- **This design document and the accompanying security/privacy checks are
  not a substitute for a full security audit** before any production use.
