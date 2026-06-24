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

## Quick start — run locally against your Azure Managed Redis

There is **no Redis config baked into the build** — you enter the instance details in
the web UI at runtime, so the only setup is starting the app.

```bash
cd redis-performance-check
mvn spring-boot:run
# or build a jar and run it:
mvn -DskipTests package && java -jar target/redis-performance-check-1.0.0.jar
```

Open **http://localhost:8080** and fill in your instance details (next section).

### Where to get your Azure Managed Redis details

In the **Azure Portal → your Azure Managed Redis resource**:

| UI field | Where to find it in Azure | Example |
|---|---|---|
| **Host** | Overview → *Endpoint / Host name* | `mycache.eastus.redis.azure.net` |
| **Port** | Overview (TLS/SSL port) — Azure Managed Redis uses **10000** | `10000` |
| **Access key / password** | *Authentication* (or *Access keys*) → **Primary** key | `AbCd…=` |
| **Use TLS/SSL** | Leave **ON** — Azure Managed Redis requires TLS | ☑ |
| **Replica host** *(optional)* | The **secondary region's** endpoint of your geo-replication group (Active geo-replication). Leave blank to skip replication-lag. | `mycache-2.westus.redis.azure.net` |

> Azure CLI alternative:
> ```bash
> az redisenterprise show -g <resource-group> -n <cache-name> --query "{host:hostName}"
> az redisenterprise database list-keys -g <resource-group> --cluster-name <cache-name> --query primaryKey -o tsv
> ```

Make sure this machine can reach the endpoint: the instance's **firewall / private
endpoint / VNet** rules must allow your client IP on the TLS port.

```bash
# connectivity sanity check from your machine
nc -vz mycache.eastus.redis.azure.net 10000
```

### Step-by-step in the UI

1. **Primary endpoint** — paste Host, Port `10000`, the **Primary access key**, keep **TLS on**.
2. *(Optional)* **Replica / secondary endpoint** — paste the secondary-region host to
   measure geo-replication lag; for active-active, point it at the other region.
   Leave blank to skip.
3. **Load shape** — duration, warmup, concurrency (threads), read/write ratio,
   keyspace size, session TTL. *Start modest:* 16 threads / 30 s.
4. **Payload sizes** — tick the session-JSON sizes to sweep, or add custom KB values.
5. **Probes** — toggle availability and replication probes and their intervals.
6. Click **Test connection** (confirms it reaches Redis and prints version/role/uptime),
   then **Start performance test**.

Results stream into the page as each payload size completes. When the run finishes a
self-contained HTML report is written to `./reports/` and linked under **Saved reports**.

> **Want representative latency?** Run this app from inside Azure (a VM in the **same or
> peer region** as the cache). From your laptop the numbers include your home/office
> internet path, not just the service.

### Try it locally first (no Azure needed)

You can validate the whole flow against a local Redis before pointing at Azure:

```bash
redis-server --port 6379 --save "" --appendonly no --daemonize yes
```

Then in the UI use Host `localhost`, Port `6379`, **TLS off**, blank password (set the
replica host to `localhost`/`6379` too if you want to exercise the replication probe).

### Run the unit tests

```bash
mvn test
```

This runs without any Redis instance (payload sizing, INFO parsing, latency percentiles).

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
