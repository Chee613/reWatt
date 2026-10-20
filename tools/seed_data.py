"""
reWatt — Preset 28-day (4-week) occupancy dataset for Hall 1-4 (FSKTM account)
==============================================================================
Run on your laptop:
    python seed_data.py            # all 4 halls
    python seed_data.py "Hall 1"   # one hall only

Prerequisites (one-time):
    pip install firebase-admin
    serviceAccountKey.json in the repo root (one level above this tools/ folder).

What it does
------------
- DELETES all existing telemetry for each hall (true "replace all").
- Writes a fresh 28-day (4 full Mon-Sun weeks) preset, 10-minute resolution.
- 20 weekdays have class intervals; 8 weekend days are all-zero.
- Each hall has its OWN independent weekly template (different start times,
  durations, interval counts, peaks, Friday slot) repeated across all 4 weeks
  with small natural drift.

Storage note: only the non-zero (active-interval) readings are written.
The app treats a missing 10-min slot as 0 (its hourly/10-min mean is computed
over present readings only), so nights / breaks / weekends render as exactly 0.
This is numerically identical to storing explicit zeros but keeps us well
under the Firestore free-tier daily write quota.
"""

import os
import sys
import firebase_admin
from firebase_admin import credentials, firestore
from datetime import datetime, timedelta, timezone
import random

MYT = timezone(timedelta(hours=8))  # Malaysia Time (UTC+8)

_KEY = os.path.join(os.path.dirname(__file__), "..", "serviceAccountKey.json")
cred = credentials.Certificate(_KEY)
firebase_admin.initialize_app(cred)
db = firestore.client()

ALL_ROOMS = ["Hall 1", "Hall 2", "Hall 3", "Hall 4"]
ROOMS = sys.argv[1:] if len(sys.argv) > 1 else ALL_ROOMS

# ── Per-hall weekly templates ────────────────────────────────────────────────
# Each interval: (start_minute_of_day, duration_minutes, is_early_ending)
#   - Monday's FIRST interval is early-ending (lecturer dismisses early).
#   - Friday's single interval is early-ending AND avoids 12:00-15:00.
#   - Normal intervals: 120/150/180 min (2/2.5/3 h). Early-ending: 60/90 min.
#   - dow: 0=Mon 1=Tue 2=Wed 3=Thu 4=Fri (weekends forced zero, not in template).
# The 4 halls are deliberately all different (start times, durations, counts,
# Friday position). >=30 min zero-gap between same-day intervals is respected.
HALL_TEMPLATES = {
    "Hall 1": {
        0: [(500, 60, True), (610, 130, False), (830, 120, False)],   # Mon 08:20e,10:10,13:50
        1: [(530, 170, False), (760, 160, False)],                    # Tue 08:50,12:40
        2: [(490, 120, False), (650, 120, False), (850, 130, False)], # Wed 08:10,10:50,14:10
        3: [(560, 150, False), (790, 160, False)],                    # Thu 09:20,13:10
        4: [(550, 90, True)],                                         # Fri 09:10e
    },
    "Hall 2": {
        0: [(550, 60, True), (670, 160, False)],                      # Mon 09:10e,11:10
        1: [(490, 130, False), (650, 120, False), (820, 150, False)], # Tue 08:10,10:50,13:40
        2: [(580, 170, False), (820, 130, False)],                    # Wed 09:40,13:40
        3: [(520, 150, False), (730, 140, False)],                    # Thu 08:40,12:10
        4: [(940, 70, True)],                                         # Fri 15:40e
    },
    "Hall 3": {
        0: [(610, 90, True), (760, 120, False), (910, 130, False)],   # Mon 10:10e,12:40,15:10
        1: [(520, 170, False), (760, 170, False)],                    # Tue 08:40,12:40
        2: [(490, 120, False), (670, 120, False), (850, 120, False)], # Wed 08:10,11:10,14:10
        3: [(610, 150, False), (820, 150, False)],                    # Thu 10:10,13:40
        4: [(490, 90, True)],                                         # Fri 08:10e
    },
    "Hall 4": {
        0: [(580, 60, True), (700, 170, False)],                      # Mon 09:40e,11:40
        1: [(490, 150, False), (680, 120, False), (850, 150, False)], # Tue 08:10,11:20,14:10
        2: [(610, 160, False), (850, 130, False)],                    # Wed 10:10,14:10
        3: [(550, 120, False), (760, 150, False)],                    # Thu 09:10,12:40
        4: [(970, 90, True)],                                         # Fri 16:10e
    },
}


def fill_interval(arr, start_min, dur_min, peak, early):
    """Fill the 144-slot day array for one class interval using the 4-phase curve."""
    start_val = random.randint(50, 80)  # ramp-up start

    if early:
        # Early dismissal: ramp-up -> short approach -> compressed ramp-down, no plateau.
        rampdown_len = random.choice([20, 30])
        rampdown_start = max(30, dur_min - rampdown_len)
        rd_enter_val = random.randint(200, int(peak))   # value entering ramp-down
        final_val = random.randint(30, 80)              # final reading at end
        approach_end = rampdown_start
        plateau = False
    else:
        approach_end = 60
        rampdown_start = dur_min - 30
        rd_enter_val = random.randint(280, 300)
        final_val = random.randint(150, 180)
        plateau = True

    for t in range(0, dur_min, 10):
        slot = (start_min + t) // 10
        if slot < 0 or slot >= 144:
            continue
        if t < 30:
            # Phase 1 — ramp-up: start_val -> ~200
            frac = t / 30.0
            v = start_val + (200 - start_val) * frac + random.uniform(-10, 10)
        elif t < approach_end:
            # Phase 2 — approach: ~200 -> peak
            span = approach_end - 30
            frac = (t - 30) / span if span > 0 else 1.0
            v = 200 + (peak - 200) * frac + random.uniform(-10, 10)
        elif plateau and t < rampdown_start:
            # Phase 3 — plateau: sampled in (peak-30, peak)
            v = random.uniform(peak - 30, peak)
        else:
            # Phase 4 — ramp-down: rd_enter_val -> final_val
            span = dur_min - rampdown_start
            frac = (t - rampdown_start) / span if span > 0 else 1.0
            v = rd_enter_val + (final_val - rd_enter_val) * frac + random.uniform(-10, 10)
        arr[slot] = max(0, int(round(v)))


def delete_hall_telemetry(uid, hall):
    coll = (db.collection("rewatt_users").document(uid)
              .collection("rooms").document(hall).collection("telemetry"))
    deleted = 0
    while True:
        docs = list(coll.limit(400).stream())
        if not docs:
            break
        batch = db.batch()
        for d in docs:
            batch.delete(d.reference)
        batch.commit()
        deleted += len(docs)
    return deleted


def get_uid():
    doc = db.collection("rewatt_usernames").document("fsktm").get()
    if doc.exists:
        return doc.to_dict().get("uid")
    return "AC1b5u6OXfPe7bJ0QM3lIfC8jwE3"  # fsktm = tjh3281@gmail.com


def seed_hall(uid, hall):
    template = HALL_TEMPLATES[hall]
    # Base peak per interval (260-300), fixed across the 4 weeks; drifts +/-10 per week.
    base_peaks = {dow: [random.randint(260, 300) for _ in intervals]
                  for dow, intervals in template.items()}

    now = datetime.now(MYT)
    batch = db.batch()
    ops = 0
    total = 0
    node_id = "seed-node"

    # 28 consecutive days ending yesterday = exactly 4 of each weekday.
    for day_back in range(28, 0, -1):
        day = now - timedelta(days=day_back)
        dow = day.weekday()           # 0=Mon .. 6=Sun
        if dow >= 5:
            continue                  # weekend: all-zero -> write nothing
        week_index = (28 - day_back) // 7
        intervals = template.get(dow, [])

        arr = [0] * 144
        for idx, (start_min, dur_min, early) in enumerate(intervals):
            peak = base_peaks[dow][idx] + random.randint(-10, 10)  # per-week drift
            fill_interval(arr, start_min, dur_min, peak, early)

        for slot in range(144):
            count = arr[slot]
            if count <= 0:
                continue
            minute_of_day = slot * 10
            dt = day.replace(hour=minute_of_day // 60, minute=minute_of_day % 60,
                             second=0, microsecond=0)
            ts_ms = int(dt.timestamp() * 1000)
            history_id = f"{ts_ms}-{node_id}"
            ref = (db.collection("rewatt_users").document(uid)
                     .collection("rooms").document(hall)
                     .collection("telemetry").document(history_id))
            batch.set(ref, {
                "roomId": hall,
                "nodeId": node_id,
                "perceptionCapturedAt": ts_ms,
                "lastUpdatedAt": ts_ms,
                "personCount": count,
                "primaryActivity": "Speech",
                "temperatureC": round(random.uniform(22.5, 26.5), 1),
                "humidityPercent": round(random.uniform(58.0, 74.0), 1),
                "lightsOn": True,
            })
            ops += 1
            total += 1
            if ops >= 400:
                batch.commit()
                batch = db.batch()
                ops = 0

    if ops > 0:
        batch.commit()
    return total


if __name__ == "__main__":
    uid = get_uid()
    if not uid:
        print("ERROR: Could not find UID for 'fsktm'.")
        sys.exit(1)

    print(f"Replacing telemetry for UID: {uid}\n")
    for hall in ROOMS:
        print(f"[{hall}]")
        try:
            removed = delete_hall_telemetry(uid, hall)
            print(f"  deleted {removed} old records")
            written = seed_hall(uid, hall)
            print(f"  OK wrote {written} new records")
        except Exception as e:
            print(f"  ERROR: {e}")
        print()

    print("Done! Open the app > Analytics > Data to see the new schedule.")
