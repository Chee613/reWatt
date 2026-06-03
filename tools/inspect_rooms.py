"""Read-only: list rooms (incl. implicit/missing parents) + subcollections for fsktm."""
import os
import firebase_admin
from firebase_admin import credentials, firestore

_KEY = os.path.join(os.path.dirname(__file__), "..", "serviceAccountKey.json")
cred = credentials.Certificate(_KEY)
firebase_admin.initialize_app(cred)
db = firestore.client()

UID = "AC1b5u6OXfPe7bJ0QM3lIfC8jwE3"  # fsktm / tjh3281@gmail.com

rooms_coll = db.collection("rewatt_users").document(UID).collection("rooms")
print("rooms via list_documents() (includes implicit parents):")
for ref in rooms_coll.list_documents():
    snap = ref.get()
    print(f"\n  === '{ref.id}'  exists={snap.exists}  fields={snap.to_dict()}")
    for sub in ref.collections():
        docs = list(sub.stream())
        print(f"      sub '{sub.id}': {len(docs)} docs")
        if docs:
            print(f"          sample fields: {docs[0].to_dict()}")
