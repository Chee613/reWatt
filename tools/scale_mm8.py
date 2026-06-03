"""
Scale MM8 occupancy so the MAXIMUM personCount becomes 100, preserving shape & time.
====================================================================================
factor = 100 / current_max_personCount, applied proportionally to:
  - telemetry.personCount        (the occupancy series shown in charts)
  - learned_schedule.segments[].predictedOccupancy  (same people-count scale)
Timestamps, segment start/end minutes, and every other field are left untouched.
Only NON-zero values are changed (0 stays 0). Forecast levels (0-3) are not touched.

    python scale_mm8.py --dry-run   # preview, change nothing
    python scale_mm8.py             # apply
"""
import os
import sys
import firebase_admin
from firebase_admin import credentials, firestore

firebase_admin.initialize_app(credentials.Certificate(
    os.path.join(os.path.dirname(__file__), "..", "serviceAccountKey.json")))
db = firestore.client()

U = "AC1b5u6OXfPe7bJ0QM3lIfC8jwE3"  # fsktm / tjh3281@gmail.com
ROOM = "MM8"
TARGET_MAX = 100
DRY_RUN = "--dry-run" in sys.argv

room = db.collection("rewatt_users").document(U).collection("rooms").document(ROOM)


def commit_safe(batch, n):
    if n and not DRY_RUN:
        batch.commit()


tele = list(room.collection("telemetry").stream())
cur_max = max((d.to_dict().get("personCount", 0) for d in tele), default=0)
if cur_max <= 0:
    print("No occupancy data found; aborting.")
    sys.exit(1)
factor = TARGET_MAX / cur_max
print(f"Mode: {'DRY RUN' if DRY_RUN else 'APPLY'}")
print(f"current max personCount = {cur_max}  ->  scale factor = {factor:.5f}\n")

# ── telemetry.personCount ────────────────────────────────────────────────────
batch = db.batch()
n = changed = new_max = 0
samples = []
for d in tele:
    old = d.to_dict().get("personCount", 0)
    if not old:
        continue
    new = round(old * factor)
    new_max = max(new_max, new)
    if len(samples) < 6:
        samples.append((old, new))
    if not DRY_RUN:
        batch.update(d.reference, {"personCount": new})
    n += 1
    changed += 1
    if n >= 400:
        commit_safe(batch, n)
        batch = db.batch()
        n = 0
commit_safe(batch, n)
print(f"telemetry: {changed} docs scaled.  sample old->new: {samples}")
print(f"           new max personCount = {new_max}\n")

# ── learned_schedule.segments[].predictedOccupancy ───────────────────────────
ls_docs = list(room.collection("learned_schedule").stream())
ls_changed = 0
ls_new_max = 0
for d in ls_docs:
    data = d.to_dict()
    segs = data.get("segments", [])
    touched = False
    for seg in segs:
        po = seg.get("predictedOccupancy")
        if isinstance(po, (int, float)) and po:
            seg["predictedOccupancy"] = round(po * factor)
            ls_new_max = max(ls_new_max, seg["predictedOccupancy"])
            touched = True
    if touched:
        ls_changed += 1
        if not DRY_RUN:
            d.reference.update({"segments": segs})
print(f"learned_schedule: {ls_changed} day-docs updated.  new max predictedOccupancy = {ls_new_max}")

print("\n" + ("Dry run complete — nothing written." if DRY_RUN else "Done — MM8 scaled to max 100."))
