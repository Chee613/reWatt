"""
reWatt — Seed 30 days of fake telemetry for Hall 1–4 (FSKTM account)
=====================================================================
Run on your laptop:
    python seed_data.py

Prerequisites (one-time):
    pip install firebase-admin

You also need serviceAccountKey.json in the repo root (one level above this tools/ folder).
Download from: Firebase console → Project settings → Service accounts →
               Generate new private key
"""

import os
import firebase_admin
from firebase_admin import credentials, firestore
from datetime import datetime, timedelta, timezone
import random

MYT = timezone(timedelta(hours=8))  # Malaysia Time (UTC+8)

_KEY = os.path.join(os.path.dirname(__file__), "..", "serviceAccountKey.json")
cred = credentials.Certificate(_KEY)
firebase_admin.initialize_app(cred)
db = firestore.client()

ROOMS = ["Hall 1", "Hall 2", "Hall 3", "Hall 4"]

# Break periods (room is EMPTY during these).
# Format: (start_hour, start_min, end_hour, end_min)
# 0=Mon, 1=Tue, 2=Wed, 3=Thu, 4=Fri
HALL_BREAKS = {
    "Hall 1": {
        0: [(10, 15, 11, 45), (15, 15, 16, 45)],
        1: [(11, 15, 12, 45)],
        2: [(11, 15, 12, 45), (15, 15, 16, 45)],
        3: [(8,  0,  8, 45), (12, 15, 15, 45)],
        4: [(12, 15, 15, 45)],
    },
    "Hall 2": {
        0: [(9,  0, 10,  0), (14,  0, 15, 30)],
        1: [(10, 0, 11,  0), (15, 30, 17,  0)],
        2: [(9, 30, 11,  0), (13,  0, 14,  0)],
        3: [(11, 0, 12, 30), (15,  0, 16,  0)],
        4: [(10, 30, 12, 0), (14, 30, 16,  0)],
    },
    "Hall 3": {
        0: [(9,  0, 10, 30), (13,  0, 14, 30)],
        1: [(11, 0, 12,  0), (15,  0, 16, 30)],
        2: [(10, 0, 11, 30), (14, 30, 16,  0)],
        3: [(8, 30, 10,  0), (12,  0, 13,  0)],
        4: [(11, 0, 12, 30), (15,  0, 16, 30)],
    },
    "Hall 4": {
        0: [(9,  0, 10, 30), (13,  0, 14,  0), (16,  0, 17, 30)],
        1: [(10, 30, 12, 0), (14,  0, 15,  0)],
        2: [(9,  0, 10,  0), (13, 30, 15,  0)],
        3: [(11, 30, 13, 0), (15, 30, 17,  0)],
        4: [(10, 0, 11, 30), (13,  0, 14, 30), (16,  0, 17, 30)],
    },
}


def in_break(h, m, breaks):
    cur = h * 60 + m
    for (sh, sm, eh, em) in breaks:
        if sh * 60 + sm <= cur < eh * 60 + em:
            return True
    return False


def is_class_time(dt, hall):
    """True if the datetime falls during an occupied (class) slot."""
    dow = dt.weekday()
    if dow >= 5:
        return False
    h, m = dt.hour, dt.minute
    # Overnight 18:30–07:30
    overnight_start = 18 * 60 + 30
    overnight_end   =  7 * 60 + 30
    cur = h * 60 + m
    if cur >= overnight_start or cur < overnight_end:
        return False
    breaks = HALL_BREAKS.get(hall, {}).get(dow, [])
    if in_break(h, m, breaks):
        return False
    return True


def person_count(dt, hall):
    if not is_class_time(dt, hall):
        return 0
    h = dt.hour
    if h in (9, 10, 14, 15):
        base = random.randint(75, 115)   # peak
    elif h in (8, 11, 13, 16):
        base = random.randint(45, 80)    # shoulder
    else:
        base = random.randint(50, 90)    # mid
    return max(0, base + random.randint(-8, 8))


def get_uid():
    # Primary: look up via username doc
    doc = db.collection("rewatt_usernames").document("fsktm").get()
    if doc.exists:
        return doc.to_dict().get("uid")
    # Fallback: fsktm = tjh3281@gmail.com (Firebase Auth uid)
    return "AC1b5u6OXfPe7bJ0QM3lIfC8jwE3"


def seed_hall(uid, hall):
    now = datetime.now(MYT)
    batch = db.batch()
    ops = 0
    total = 0

    for day_back in range(30, 0, -1):
        day = now - timedelta(days=day_back)
        for hour in range(7, 20):           # 07:00 – 19:30
            for minute in (0, 30):
                dt = day.replace(hour=hour, minute=minute, second=0, microsecond=0)
                ts_ms = int(dt.timestamp() * 1000)
                node_id = "seed-node"
                history_id = f"{ts_ms}-{node_id}"
                count = person_count(dt, hall)

                ref = (
                    db.collection("rewatt_users").document(uid)
                      .collection("rooms").document(hall)
                      .collection("telemetry").document(history_id)
                )
                batch.set(ref, {
                    "roomId": hall,
                    "nodeId": node_id,
                    "perceptionCapturedAt": ts_ms,
                    "lastUpdatedAt": ts_ms,
                    "personCount": count,
                    "primaryActivity": "Speech" if count > 0 else "Silence",
                    "temperatureC": round(random.uniform(22.5, 26.5), 1),
                    "humidityPercent": round(random.uniform(58.0, 74.0), 1),
                    "lightsOn": count > 0,
                })
                ops += 1
                total += 1

                if ops >= 450:   # Firestore batch limit is 500
                    batch.commit()
                    batch = db.batch()
                    ops = 0

    if ops > 0:
        batch.commit()

    print(f"  ✓ {hall}: {total} records written")


if __name__ == "__main__":
    uid = get_uid()
    if not uid:
        print("ERROR: Could not find UID for 'fsktm' in Firestore.")
        print("Make sure the fsktm account has signed in at least once on a phone.")
        exit(1)

    print(f"Seeding 30-day fake telemetry for UID: {uid}\n")
    for hall in ROOMS:
        print(f"[{hall}]")
        try:
            seed_hall(uid, hall)
        except Exception as e:
            print(f"  ERROR: {e}")
        print()

    print("Done! Open the Admin phone → Analytics tab → Data to see the heatmap.")
