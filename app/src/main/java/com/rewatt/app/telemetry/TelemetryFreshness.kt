package com.rewatt.app.telemetry

class TelemetryFreshness(
    private val staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
) {
    fun evaluate(lastValidPacketAtMillis: Long?, nowMillis: Long): PacketFreshness {
        if (lastValidPacketAtMillis == null) return PacketFreshness.WaitingForFirstPacket
        return if (nowMillis - lastValidPacketAtMillis > staleAfterMillis) {
            PacketFreshness.Stale
        } else {
            PacketFreshness.Fresh
        }
    }

    companion object {
        const val DEFAULT_STALE_AFTER_MILLIS = 30_000L
    }
}

enum class PacketFreshness {
    WaitingForFirstPacket,
    Fresh,
    Stale,
}

