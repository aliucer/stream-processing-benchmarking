package org.example

import io.nats.client.Nats
import io.nats.client.Options
import io.nats.client.PullSubscribeOptions
import java.io.File
import java.time.Duration
import kotlin.math.max

fun main() {
    val natsUrl = System.getenv("NATS_URL") ?: "nats://127.0.0.1:4222"
    val durable = System.getenv("DURABLE") ?: "dur_A_events"

    // Optional: if set, only count events matching this run_id
    val runIdFilter = System.getenv("RUN_ID")

    var logFile = File("logs/caseA-cons-pending.log").apply { parentFile?.mkdirs() }

    fun log(line: String) {
        println(line)
        logFile.appendText(line + "\n")
    }

    Nats.connect(Options.Builder().server(natsUrl).build()).use { nc ->
        val js = nc.jetStream()

        // Heartbeat stream
        val hbSub = nc.subscribe("bench.prod")

        // EVENTS pull durable
        val pso = PullSubscribeOptions.builder().stream("EVENTS").durable(durable).build()
        val eventsSub = js.subscribe("events.*", pso)

        log("OBS startup nats=$natsUrl durable=$durable run_id_filter=${runIdFilter ?: "none"}")

        var prodMaxSeq = 0L
        var runIdFromHb: String? = null

        var obsMaxSeq = 0L
        var secCount = 0L
        val lats = ArrayList<Long>(100_000)
        var lastPrintMs = System.currentTimeMillis()

        while (true) {
            // Drain heartbeats (non-blocking)
            var hbMsg = hbSub.nextMessage(1)
            while (hbMsg != null) {
                val hb = JSON.decodeFromString(Heartbeat.serializer(), String(hbMsg.data))

                if (runIdFromHb == null) {
                    runIdFromHb = hb.run_id
                    if (runIdFilter == null || runIdFilter == runIdFromHb) {
                        logFile =
                                File("logs/caseA-cons-$runIdFromHb.log").apply {
                                    parentFile?.mkdirs()
                                }
                        log("OBS log_switched file=${logFile.path} run_id=$runIdFromHb")
                    }
                }

                // If RUN_ID is set, ignore other runs' heartbeats
                if (runIdFilter == null || hb.run_id == runIdFilter) {
                    prodMaxSeq = hb.prod_max_seq
                }

                hbMsg = hbSub.nextMessage(1)
            }

            // Pull a batch
            val batchNowMs = System.currentTimeMillis()
            val msgs = eventsSub.fetch(5_000, Duration.ofMillis(200))

            for (m in msgs) {
                val e = JSON.decodeFromString(Event.serializer(), String(m.data))

                if (runIdFilter == null || e.run_id == runIdFilter) {
                    obsMaxSeq = max(obsMaxSeq, e.seq)
                    secCount++
                    // keep negatives as warning (batch timestamp)
                    lats.add(batchNowMs - e.producer_ts_ms)
                }

                m.ack()
            }

            val t = System.currentTimeMillis()
            if (t - lastPrintMs >= 1000) {
                val rid = runIdFilter ?: (runIdFromHb ?: "unknown")
                val lag = max(0L, prodMaxSeq - obsMaxSeq)
                val (p95, p99) = p95p99(lats)

                log(
                        "OBS rate=${secCount}/s max_seq=$obsMaxSeq lag=$lag p95=${p95}ms p99=${p99}ms run_id=$rid"
                )

                secCount = 0
                lats.clear()
                lastPrintMs = t
            }
        }
    }
}

private fun p95p99(xs: MutableList<Long>): Pair<Long, Long> {
    if (xs.isEmpty()) return 0L to 0L
    xs.sort()
    fun pick(p: Double): Long {
        val idx = ((xs.size - 1) * p).toInt()
        return xs[idx]
    }
    return pick(0.95) to pick(0.99)
}
