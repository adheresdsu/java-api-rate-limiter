# Gatekeeper — Concurrent API Rate Limiter

Gatekeeper is a small, dependency-light Java library that implements the
core algorithms behind API rate limiting. It exists as a portfolio project
to demonstrate Java fundamentals — object-oriented design, concurrency,
data structures, and testing — in a self-contained, well-documented package
rather than as a thin wrapper around a framework.

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

## Milestone status

**Milestone 1 (current): repository scaffolding and core algorithms.**
This milestone covers the `RateLimiter` interface, the three algorithm
implementations, the `TimeSource` abstraction, and their test suites. It
does **not** include an HTTP server, a traffic simulator, a frontend,
Docker configuration, or performance benchmarking — those are future
milestones.

No throughput or latency numbers have been measured yet. Any performance
claims will be added only once real benchmarks exist.

## Project layout

```
src/main/java/com/aryandhere/ratelimiter/
  core/    RateLimiter interface and the three algorithm implementations
  time/    TimeSource abstraction and its system-clock implementation
src/test/java/com/aryandhere/ratelimiter/
  core/    Unit and concurrency tests per algorithm
  time/    TimeSource tests, including the ManualTimeSource test double
```

## Build and test

Requires Java 21 and Maven.

```bash
mvn test    # run the full test suite
mvn package # build the jar
```

## License

See [LICENSE](LICENSE).
