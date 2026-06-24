# Azure Managed Redis — Performance Check

A Spring Boot harness to load-test and measure an **Azure Managed Redis** instance
(geo-replicated) for a **session-JSON store/retrieve** workload. You configure and
launch tests from a web UI; results render live and are saved as **standalone HTML
reports** you can open or share.

## What it measures

| Concern | How |
|---|---|
| **GET/PUT latency & throughput** | Concurrent workers run a configurable read/write mix; latency captured with HdrHistogram → p50/p90/p95/p99/p99.9/max + ops/sec. |
| **Payload size impact** | The same test is swept across several session-JSON sizes (1 KB … 1 MB) and charted size-vs-latency. |
| **Geo-replication lag** | Writes a versioned marker to the primary, polls the **replica/secondary endpoint** until it appears, records the delay (min/mean/p95/p99/max). |
| **Availability / uptime / downtime** | A dedicated probe PINGs the primary throughout the run, tracking failures, **outage windows** and reconnection time. |
| **Server-side KPIs** | Redis `INFO` snapshot **before & after** the run: version, role, server uptime, memory, clients, hits/misses, evictions. |

## Requirements

- Java 17+ and Maven (verified with OpenJDK 17 + Maven 3.9)
- Network access from this machine to your Redis endpoint(s) on the TLS port (Azure Managed Redis default **10000**)
- An access key (password) for the instance

## Run

```bash
mvn spring-boot:run
# or
mvn -DskipTests package && java -jar target/redis-performance-check-1.0.0.jar
```

Then open **http://localhost:8080**.

## Using the UI

1. **Primary endpoint** — host, port (10000), access key, TLS on.
2. *(Optional)* **Replica/secondary endpoint** — set this to enable geo-replication
   lag measurement. For active-active geo-replication, point it at the other region's
   endpoint. Leave blank to skip.
3. **Load shape** — duration, warmup, concurrency (threads), read/write ratio,
   keyspace size, session TTL.
4. **Payload sizes** — tick the session-JSON sizes to sweep, or add custom KB values.
5. **Probes** — toggle availability and replication probes and their intervals.
6. **Test connection** to validate, then **Start performance test**.

Results stream into the page as each payload size completes. When the run finishes a
self-contained HTML report is written to `./reports/` and linked under **Saved reports**.

## Reports

Each report (`reports/report-<timestamp>-<runId>.html`) is fully standalone: it embeds
the run's JSON data and the rendering script, so it opens directly in a browser and can
be emailed/archived. Charts use Chart.js from CDN; the data tables render without it.

## How it works

```
HTML UI (index.html, app.js, render.js)
        │  REST
        ▼
TestController ──> PerfTestService ──> LoadEngine (concurrent GET/PUT, HdrHistogram)
                              ├──────> AvailabilityProbe (PING loop)
                              ├──────> ReplicationProbe  (primary write → replica read)
                              ├──────> ServerInfoReader  (INFO before/after)
                              └──────> ReportService     (standalone HTML)
```

- **Connections** are built at runtime from the UI config (`RedisConnections`, Lettuce,
  TLS), so nothing is hard-coded in `application.yml`.
- Only **one run executes at a time**; the live UI polls `/api/status`.
- The access key is used in-memory only and is **never written** into reports.

## Key config (`src/main/resources/application.yml`)

| Property | Default | Meaning |
|---|---|---|
| `server.port` | 8080 | UI/API port |
| `redisperf.reports-dir` | `./reports` | where HTML reports are written |

## REST API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/test-connection` | Validate endpoint(s), return `INFO` summary |
| POST | `/api/start` | Start a run (body = config JSON) |
| GET | `/api/status` | Current/most-recent run result |
| GET | `/api/status/{runId}` | A specific run |
| GET | `/api/reports` | List saved reports |
| GET | `/api/reports/{file}` | Open a saved report |

## Notes & caveats

- Run this **close to** the Redis region (e.g. an Azure VM in the same/peer region) for
  latency numbers that reflect the service rather than your local internet path.
- Start modest (concurrency 16, 30 s) and scale up; high concurrency + large payloads
  generate real load and bandwidth against the instance.
- "Replication lag" measured this way is **end-to-end client-observed** time (write
  ack + replication + replica read), which is what a session-failover scenario sees.
