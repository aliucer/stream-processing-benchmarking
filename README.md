# Stream Processing Benchmarking

A simple, "no-magic" benchmark harness for stream processing pipelines.

The goal is to produce comparable end-to-end performance numbers (throughput, lag, p95/p99 latency) for different processing engines, using **NATS JetStream** as the transport and **TiDB** as the sink.

## What's this?

I'm checking how different engines verify against a baseline when processing a windowed aggregation workload (10s tumbling window).

*   **Transport**: NATS JetStream (Memory storage)
*   **Sink**: TiDB
*   **Engines**:
    *   Baseline (Kotlin only)
    *   Arroyo (Planned)
    *   RisingWave (Planned)

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
