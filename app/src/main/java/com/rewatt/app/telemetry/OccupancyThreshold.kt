package com.rewatt.app.telemetry

// ═══════════════════════════════════════════════════════════════════════════════
// OCCUPANCY THRESHOLD DEFINITION
// ═══════════════════════════════════════════════════════════════════════════════
//
// Output:  OccupancyDecision.occupied
//   TRUE  = someone is in the room
//   FALSE = room is empty
//
// ── SOLE signal: LD2412 mmWave radar ────────────────────────────────────────
//
//   The radar outputs a state integer (0–3) with per-target distance and energy.
//   Energy and distance gates are applied to reject environmental noise:
//
//   MOVING_ENERGY_MIN    = 10
//     LD2412 reports 0-100 energy for the moving target.  Values below 10 are
//     typically caused by HVAC airflow, swinging curtains, or micro-vibration.
//     A human walking or gesturing typically produces energy > 20.
//
//   STATIONARY_ENERGY_MIN = 15
//     Stationary energy below 15 is usually wall/furniture reflection.
//     A seated person typically sustains stationary energy > 20.
//
//   DISTANCE_MAX_CM = 600
//     Targets detected beyond 6 m are outside the LD2412 classroom mount range.
//     Detections beyond this distance are treated as cross-room/corridor bleed.
//
// ── FINAL RULE ───────────────────────────────────────────────────────────────
//
//   occupied = radarOccupied
//
//   Temperature (SHT30) is intentionally excluded from the occupancy decision.
//   It is displayed as a separate environmental reading only.
//   Neither humidity nor perception person-count affects this verdict.
//
// ═══════════════════════════════════════════════════════════════════════════════
object OccupancyThreshold {
    const val MOVING_ENERGY_MIN:     Int = 10
    const val STATIONARY_ENERGY_MIN: Int = 15
    const val DISTANCE_MAX_CM:       Int = 600
}

data class OccupancyDecision(
    val occupied:  Boolean,  // TRUE = someone in room / FALSE = empty
    val reason:    String,   // human-readable explanation shown in UI
)

/** A moving target that passes the energy and distance noise gates (shared with CCTV motion events). */
fun RadarTelemetry.isMovingTarget(): Boolean =
    (radarState == 1 || radarState == 3) &&
        movingEnergy >= OccupancyThreshold.MOVING_ENERGY_MIN &&
        movingDistanceCm in 1..OccupancyThreshold.DISTANCE_MAX_CM

fun RadarTelemetry.toOccupancyDecision(): OccupancyDecision {
    val t = OccupancyThreshold

    val movingQualifies = isMovingTarget()

    val stationaryQualifies =
        (radarState == 2 || radarState == 3) &&
        stationaryEnergy >= t.STATIONARY_ENERGY_MIN &&
        stationaryDistanceCm in 1..t.DISTANCE_MAX_CM

    val occupied = movingQualifies || stationaryQualifies

    val reason = when {
        radarState == 0 ->
            "No radar target (state 0)"
        movingQualifies ->
            "Moving target at ${movingDistanceCm} cm, energy ${movingEnergy}"
        stationaryQualifies ->
            "Stationary target at ${stationaryDistanceCm} cm, energy ${stationaryEnergy}"
        radarState == 1 ->
            "Moving signal too weak (energy ${movingEnergy} < ${t.MOVING_ENERGY_MIN}) — noise gated"
        radarState == 2 ->
            "Stationary signal too weak (energy ${stationaryEnergy} < ${t.STATIONARY_ENERGY_MIN}) — noise gated"
        radarState == 3 ->
            "Both signals below energy gate — noise gated"
        else ->
            "No target"
    }

    return OccupancyDecision(occupied = occupied, reason = reason)
}
