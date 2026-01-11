# Stream Processing  
## A Product Selection Analysis

---

## Overview

**Created  →  Ali Ucer**  
**Last update  →  Date**

---

We are interested in selecting a stream processing solution for our backend, with the following criteria:

* Kotlin compatibility
* NATS compatibility (source and sink)
* Active open source development
* Closed source compatible license
* Ease of administration (unlike Kafka or Flink)
* Ability to scale horizontally (distributed)

Include a comparison table in the summary, and use ratings (binary, or 3/5 points) for factors where it makes sense.

---

## Test Flow

A: Kotlin -> NATS -> Kotlin

B: Kotlin -> NATS -> Kotlin -> TiDB

C: Kotlin -> NATS -> Arroyo/RisingWave -> NATS -> Kotlin (1 subject and partitioned)

D: Kotlin -> NATS -> Arroyo/RisingWave -> NATS -> Kotlin Writer -> TiDB

E: Kotlin -> NATS -> Arroyo -> NATS -> Benthos -> TiDB

F: Kotlin -> NATS -> RisingWave -> TiDB

---

# RisingWave

## Technical Capabilities

This section evaluates the core product functionalities and performance.

* Latency
* Throughput
* State Management & Fault Tolerance (e.g., exactly-once processing guarantees, recovery mechanisms)

Risingwave separates computing from states. The state is stored in S3. During NATS ingestion spikes, stateless compute nodes can be added almost instantly without the latency of rebalancing local storage. However, high-throughput ingestion requires parallel “Source Actors.” NATS subjects must be partitioned or use wildcards to allow multiple actors to consume. A single unpartitioned subject will be limited to a single actor.

RisingWave provides strictly exactly-once internal processing via barrier-based checkpointing, though downstream sinks typically default to at-least-once delivery and must handle idempotency.

RisingWave supports high availability, multiple Meta Node replicas. If the leader fails, a follower can assume leadership and restore the cluster.

* Windowing (time- or count-based aggregations)

This platform supports time-based window operations through window functions. Users can write sliding, thumbling, hopping windows in standard SQL syntax. Watermark is supported for out-of-order data handling.

* Complex Event Processing (CEP) / Advanced Analytics

Complex Event Processing (CEP) & Analytics RisingWave handles complex analytics using standard SQL instead of a specialized CEP API. It supports all standard time windows such as tumbling, hopping, session using normal SQL syntax. It natively manages event time and watermarks, so out-of-order data from NATS is processed correctly. You can also perform temporal joins. It allows you to join NATS streams with static or slowly changing tables such as user data from TiDB in real time. If SQL isn’t enough, you can add custom logic using UDFs written in Python, Java, JavaScript, or Rust.

* Language Bindings / API availability (Java, Scala, Python, SQL, etc.)

RisingWave implements the PostgreSQL wire protocol, and it enables any PostgreSQL driver to be used. So, for Kotlin, standard JDBC drivers can be used to query RisingWave or insert data. It also serves materialized view results directly for real-time querying without sinking everything to TiDB, and in case processing must go beyond SQL, it provides JVM-based User Defined Functions, allowing complex processing logic to be implemented directly in Kotlin.

```kotlin
val url = "jdbc:postgresql://risingwave-host:4566/dev" val conn = DriverManager.getConnection(url, props) val stmt = conn.createStatement() stmt.execute("CREATE MATERIALIZED VIEW user_stats AS...")
```

* Scalability and Performance

---

## Ecosystem & Operations

This section focuses on the ease of use, integration, and community/vendor support.

* Integration with Existing Infrastructure (NATS, TiDB, R2)

RisingWave supports NATS JetStream end-to-end. It ingests via the NATS source connector and publishes results back via the NATS/Jetstream sink. Note that sink to NATS/Jetstream is append-only, at-least-once. It can also sink TiDB through JDBC in upsert mode, and its state store runs on S3-compatible object store. Cloudflare R2’s S3-compatible endpoint will align with that.

* Deployment & Operational Overhead (ease of setup, maintenance, monitoring)

Risingwave consolidates ingestion, stream processing and serving into a single unified platform, so there is no need to manage separate compute and serving clusters. There are no Zookeeper dependencies, supports cloud-native deployment, and provides standard observability through native Prometheus and Grafana integrations.

* Community Size & Support Resources (documentation, forums, etc.)

Risingwave is an open source project (Apache 2.0), has 8600 github starts and 700+ forks. Development velocity is high, and there is ongoing maintenance (v2.7.0 release is in Dec 2025). It has a comprehensive documentation, which includes NATS/JDBC integrations.

* Managed Service Availability (e.g., AWS, GCP, Azure options)

There is a RisingWave Cloud offering by RisingWave Labs which is a fully managed service that handles cluster maintenance for you. You can use their fully hosted cloud or run the managed service inside your own existing cloud account.

* Ease of Implementation/Use

---

## Business & Cost

This section covers the commercial and strategic aspects of the vendor relationship.

* Licensing Model (open-source vs. commercial licenses)
* Total Cost of Ownership (TCO) (infrastructure, training, support, maintenance costs)
* Vendor Reputation & Roadmap (stability, longevity, future vision)
* Customer Support & Service-Level Agreements (SLAs)
* Training Time / Learning Curve

The core engine is fully Apache 2.0 licensed, it allows unrestrictedly integrating it into proprietary architectures.  
It utilizes local RAM and SSDs strictly as a tiered hot cache for active data, not as the primary source of truth. Its state resides in S3 which is low-cost object storage rather than expensive local SSD.  
Its SQL first interface is very easy to learn. Engineers with standard database skills can build streaming pipelining immediately, unlike flink, which requires Java/Scala expertise.  
It is backed by a VC-funded vendor (RisingWave Labs). It offers SLA-guaranteed managed services, alongside an active open-source community for free support.

---

# Fluvio

Fluvio can work well as a streaming platform like kafka, but it doesn’t match our requirements for a Kotlin + NATS JetStream + SQL-first stream processing stack. JetStream support is not reliably available (the community NATS connector still lists “Support JetStream” as an open issue since Mar 9, 2024), so adopting Fluvio would either require a NATS - Fluvio bridge across the system or migrating off NATS.

Stream processing is mainly defined via Fluvio’s SDF dataflow.yaml model rather than standard SQL windowing and joins, so even if WASM is acceptable, Rust and WASM development brings extra overhead and will take longer than SQL iterations.  
Also, the Java/Kotlin client is also relatively stale, the latest tagged release is about 3 years ago. For long term JVM side integrations, and this adds risk.

---

# Arroyo

## Technical Capabilities

This section evaluates the core product functionalities and performance.

* Latency
* Throughput
* State Management & Fault Tolerance (e.g., exactly-once processing guarantees, recovery mechanisms)

Arroyo keeps the hot state on worker memory, and relies on barrier based checkpoint to durable object storage such as S3 for recovery. When the join or window state is predictable, bounded and you want very fast per-event processing, this design is a good fit. The trade-off is that the state is RAM-bounded and the recovery mechanism restores from the checkpoint path, so failover time grows with checkpoint size and storage throughput.

Exactly-once delivery for inside state, but sink is at least once, so idempotent/transactional sink may be required.  
In the open-source release, the controller is a single instance, there is no controller high availability yet. If it goes down, workers eventually pause until it returns.

* Windowing (time- or count-based aggregations)

This platform supports time-based window operations through SQL windowing. Users can write sliding, tumbling, and hopping windows in standard SQL syntax. Watermarks are supported for out-of-order data handling.  
In OSS Arroyo, window state is kept on worker memory, so very large windows or high-cardinality keys can require careful sizing.

* Complex Event Processing (CEP) / Advanced Analytics

Just like RisingWave, Arroyo handles complex analytics using standard SQL instead of a specialized CEP API. It supports all standard time windows such as tumbling, hopping, session using normal SQL syntax. It natively manages event time and watermarks, so out-of-order data from NATS is processed correctly. You can also perform streaming joins (including windowed joins) for sequence and pattern style analytics. Compared to RisingWave, UDF support is narrower. If SQL isn’t enough, you can add custom logic using UDFs written in Rust or Python. Compared to RisingWave, Python UDFs are currently scalar-only. UDAFs and async Python UDFs are planned.

* Language Bindings / API availability (Java, Scala, Python, SQL, etc.)

Arroyo is used as a deployed service. You define pipelines in SQL and operate them through its REST API. It is not a serving database with a query interface, results are typically written to external sinks such as Kafka, NATS, S3, or a database, and Kotlin services read them from there. As a result, language bindings are minimal compared to RisingWave, and integration is mainly REST for job control plus the chosen sink for data access.

* Scalability and Performance

---

## Ecosystem & Operations

This section focuses on the ease of use, integration, and community/vendor support.

* Integration with Existing Infrastructure (NATS, TiDB, R2)

Arroyo supports NATS as both source and sink, and it can write file outputs directly to R2 via its filesystem connector. There is no native connector for MySQL/PostgreSQL, and the documented approach for database integration is Debezium/Kafka Connect, which is mature but adds operational overhead. If the goal is simply writing results to TiDB and serving the app, there are some lighter options such as NATS -> Kotlin writer or a small Redpanda Connect pipeline as the DB writer.

* Deployment & Operational Overhead (ease of setup, maintenance, monitoring)

Arroyo is deployed as a standalone stream processing service with a split control plane and data plane to run separate components for job management, orchestration, compilation, execution, along with a metadata database and durable checkpoint storage. This service style architecture is flexible and scales workers independently, but it introduces more moving parts to deploy and monitor than a unified streaming database platform, and as a result, served from external sinks rather than queried directly from Arroyo.

* Community Size & Support Resources (documentation, forums, etc.)

Arroyo is an open source project (dual-licensed Apache 2.0 + MIT) with about 4.7k GitHub stars and 326 forks. Development is active, with the latest release v0.15.0 on December 1, 2025. Documentation is solid at doc.arroyo.dev, including connector guides such as NATS, and support/community channels are centered around GitHub issues and Discord.

* Managed Service Availability (e.g., AWS, GCP, Azure options)

Arroyo can be self-hosted, or used via the Arroyo Cloud service managed by Arroyo Systems. After the Cloudflare acquisition, the main fully managed option described publicly is Cloudflare Pipelines (open beta), which runs serverlessly only on Cloudflare and writes to R2 without you managing clusters.

* Ease of Implementation/Use

---

## Business & Cost

This section covers the commercial and strategic aspects of the vendor relationship.

* Licensing Model (open-source vs. commercial licenses)Vendor Reputation & Roadmap (stability, longevity, future vision)
* Customer Support & Service-Level Agreements (SLAs)
* Training Time / Learning Curve
* Licensing Model (open-source vs. commercial licenses)

The open-source engine is permissively licensed (dual Apache-2.0 + MIT).

* Total Cost of Ownership (TCO) (infrastructure, training, support, maintenance costs)

In the open-source release, state lives in worker memory and is checkpointed to shared object storage, so costs scale mainly worker RAM/CPU plus relatively cheap checkpoint storage (S3/R2).  
Arroyo is also SQL-first and for non-streaming experts, so onboarding cost compared to Java/Scale first engines is lower.

* Vendor Reputation & Roadmap (stability, longevity, future vision)

Arroyo was acquired by CloudFlare, and their fully managed path is emerging via Cloudflare Pipelines. It aims to remote cluster ops and is described as usage-based. Also its community support is centered around Discord and GitHub.

---

# RedPanda

Redpanda is a high performance Kafka-API compatible message broker. It's designed as a simpler operational replacement for Kafka and works with standard Kafka client libraries in any language, including Kotlin.

Redpanda Connect is a separate, lightweight connector and data processing tool used to move data between systems and apply simple transformations. It can be used to bridge systems and write to databases via configuration.

They are not a good fit for our use case because neither Redpanda nor Redpanda Connect is a full stream processing engine like RisingWave or Arroyo. Redpanda is just the messaging layer. Redpanda Connect can do routing and lightweight transforms, but it is not designed for stateful windowing and join operations.

---

# Recommendation

This section should include a comparison table of the products previously considered.


## Status

🚧 **Work in Progress**

I'm currently building this out step-by-step. You can see the full plan and current progress in:
👉 [`docs/bench_roadmap.txt`](docs/bench_roadmap.txt)

## How to Run

1.  **Start dependencies:**
    ```bash
    docker-compose up -d
    ```

2.  **Run the app:**
    This is a standard Kotlin Gradle project.
    ```bash
    ./gradlew build
    ```
    (Check `commands_to_start.txt` or `docs/` for specific run commands as I add them).

## Project Structure

*   `app/`: Kotlin source code.
*   `nats/`: Stream configurations and data.
*   `docs/`: Detailed plans and roadmaps.

---
*Created by Ali.*
