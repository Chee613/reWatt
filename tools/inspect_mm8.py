"""Read-only: summarize MM8 occupancy data for fsktm."""
import os
import firebase_admin
from firebase_admin import credentials, firestore

firebase_admin.initialize_app(credentials.Certificate(
    os.path.join(os.path.dirname(__file__), "..", "serviceAccountKey.json")))
db = firestore.client()
U = "AC1b5u6OXfPe7bJ0QM3lIfC8jwE3"
room = db.collection("rewatt_users").document(U).collection("rooms").document("MM8")

# telemetry personCount distribution
tele = list(room.collection("telemetry").stream())
counts = [d.to_dict().get("personCount", 0) for d in tele]
nz = [c for c in counts if c]
print(f"telemetry docs: {len(tele)}  nonzero: {len(nz)}")
print(f"  personCount  max={max(counts) if counts else 0}  "
      f"min_nonzero={min(nz) if nz else 0}  "
      f"top5={sorted(counts, reverse=True)[:5]}")

# learned_schedule predictedOccupancy range
print("\nlearned_schedule predictedOccupancy:")
ls_vals = []
for d in room.collection("learned_schedule").stream():
    for seg in d.to_dict().get("segments", []):
        ls_vals.append(seg.get("predictedOccupancy"))
print(f"  segments={len(ls_vals)}  values={sorted([v for v in ls_vals if v is not None], reverse=True)}")

# forecast
print("\nforecast/latest:")
fc = room.collection("forecast").document("latest").get()
if fc.exists:
    d = fc.to_dict()
    print(f"  predictedOccupancy={d.get('predictedOccupancy')}  "
          f"occupancyLevel={d.get('occupancyLevel')}  "
          f"schedule(levels 0-3) max={max(d.get('schedule', [0]))}")
