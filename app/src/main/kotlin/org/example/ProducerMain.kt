package org.example

import io.nats.client.Nats
import io.nats.client.Options
import java.io.File
import kotlin.math.max

fun main() {
    val natsUrl = System.getenv("NATS_URL") ?: "nats://127.0.0.1:4222"
    val targetRate = (System.getenv("TARGET_RATE") ?: "50000").toInt()

    val warmupSec = (System.getenv("WARMUP_SEC") ?: "20").toInt()
    val measureSec = (System.getenv("MEASURE_SEC") ?: "120").toInt()

    val runId = System.getenv("RUN_ID") ?: java.util.UUID.randomUUID().toString()

    val payload = "x".repeat(512) // 512 bytes (ASCII)
    val logFile = File("logs/caseA-prod-$runId.log").apply { parentFile?.mkdirs() }

    fun log(line: String) {
        println(line)
        logFile.appendText(line + "\n")
    }

    Nats.connect(Options.Builder().server(natsUrl).build()).use { nc ->
        val js = nc.jetStream()

        var seq = 0L

        val startMs = System.currentTimeMillis()
        val endMs = startMs + (warmupSec + measureSec) * 1000L

        log(
                "PROD startup run_id=$runId target_rate=${targetRate}/s warmup=${warmupSec}s measure=${measureSec}s nats=$natsUrl"
        )

        var lastPrintMs = System.currentTimeMillis()

        while (true) {
            val secStartNs = System.nanoTime()
            var secCount = 0L

            repeat(targetRate) {
                seq++
                secCount++

                val nowMs = System.currentTimeMillis()
                val key = "k" + (seq % 20_000)
                val p = (key.hashCode() and 0x7fffffff) % 16
                val subject = "events.$p"

                val e =
                        Event(
                                run_id = runId,
                                event_id = "$runId-$seq",
                                seq = seq,
                                producer_ts_ms = nowMs,
                                key = key,
                                value = seq,
                                payload_bytes = 512,
                                payload = payload
                        )

                js.publish(subject, JSON.encodeToString(Event.serializer(), e).encodeToByteArray())
            }

            val elapsedSec = max(1e-9, (System.nanoTime() - secStartNs) / 1e9)
            val actualRate = (secCount / elapsedSec).toLong()

            val nowMs = System.currentTimeMillis()

            // heartbeat once per second (or per loop if loop took >1s)
            val hb =
                    Heartbeat(
                            run_id = runId,
                            prod_max_seq = seq,
                            prod_rate = actualRate,
                            producer_ts_ms_now = nowMs
                    )
            js.publish(
                    "bench.prod",
                    JSON.encodeToString(Heartbeat.serializer(), hb).encodeToByteArray()
            )

            // one line per second
            if (nowMs - lastPrintMs >= 1000) {
                log("PROD rate=${actualRate}/s max_seq=$seq run_id=$runId")
                lastPrintMs = nowMs
            }

            // try to roughly align loop to 1s (only sleep if we were faster than 1s)
            val sleepMs = ((1_000_000_000L - (System.nanoTime() - secStartNs)) / 1_000_000L)
            if (sleepMs > 0) Thread.sleep(sleepMs)

            if (nowMs >= endMs) {
                log("PROD done run_id=$runId max_seq=$seq")
                return
            }
        }
    }
}
