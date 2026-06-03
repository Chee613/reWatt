"""Delete the 'Hall 4' room (all subcollection docs + the room doc) for fsktm."""
import os
import firebase_admin
from firebase_admin import credentials, firestore

_KEY = os.path.join(os.path.dirname(__file__), "..", "serviceAccountKey.json")
firebase_admin.initialize_app(credentials.Certificate(_KEY))
db = firestore.client()

UID = "AC1b5u6OXfPe7bJ0QM3lIfC8jwE3"  # fsktm / tjh3281@gmail.com
ROOM = "Hall 4"

src = db.collection("rewatt_users").document(UID).collection("rooms").document(ROOM)

before = {sub.id: sum(1 for _ in sub.stream()) for sub in src.collections()}
print(f"'{ROOM}' contents: {before} (total {sum(before.values())})", flush=True)

batch = db.batch()
n = total = 0
for sub in src.collections():
    for snap in sub.stream():
        batch.delete(snap.reference)
        n += 1
        total += 1
        if n >= 400:
            batch.commit()
            batch = db.batch()
            n = 0
if n:
    batch.commit()
src.delete()
print(f"Deleted {total} doc(s) + the room doc. '{ROOM}' removed.", flush=True)

after = {sub.id: sum(1 for _ in sub.stream()) for sub in src.collections()}
print(f"Remaining under '{ROOM}': {after if after else 'nothing'}", flush=True)
