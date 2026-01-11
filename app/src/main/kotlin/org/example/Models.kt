package org.example

import kotlinx.serialization.Serializable

@Serializable
data class Event(
        val run_id: String,
        val event_id: String,
        val seq: Long,
        val producer_ts_ms: Long,
        val key: String,
        val value: Long,
        val payload_bytes: Int,
        val payload: String
)

@Serializable
data class Heartbeat(
        val run_id: String,
        val prod_max_seq: Long,
        val prod_rate: Long,
        val producer_ts_ms_now: Long
)
