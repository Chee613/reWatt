"""
reWatt — Rename Firestore rooms (spaces) for the fsktm account.
================================================================
Hall 1 -> DK1, Hall 2 -> DK2, Hall 3 -> MM8

Firestore can't rename a document ID, so each room is COPIED (every
subcollection doc, fetched in bulk) to the new name, the embedded `roomId`
field is rewritten, copy counts are verified per subcollection, and only
then are the originals deleted. Writes/deletes are batched (<=400/commit).

The room trees here are one level deep (room -> subcollection -> leaf docs);
the script discovers subcollections dynamically so it copies whatever exists.

    python rename_rooms.py            # copy + verify + delete (full rename)
    python rename_rooms.py --dry-run  # show counts, change nothing
"""
import os
import sys
import firebase_admin
from firebase_admin import credentials, firestore

_KEY = os.path.join(os.path.dirname(__file__), "..", "serviceAccountKey.json")
cred = credentials.Certificate(_KEY)
firebase_admin.initialize_app(cred)
db = firestore.client()

UID = "AC1b5u6OXfPe7bJ0QM3lIfC8jwE3"  # fsktm / tjh3281@gmail.com
RENAMES = {"Hall 1": "DK1", "Hall 2": "DK2", "Hall 3": "MM8"}
DRY_RUN = "--dry-run" in sys.argv

rooms_coll = db.collection("rewatt_users").document(UID).collection("rooms")


class Batcher:
    """Accumulates set/delete ops and auto-commits every `limit` operations."""
    def __init__(self, limit=400):
        self.limit = limit
        self.batch = db.batch()
        self.n = 0
        self.total = 0

    def set(self, ref, data):
        self.batch.set(ref, data)
        self._tick()

    def delete(self, ref):
        self.batch.delete(ref)
        self._tick()

    def _tick(self):
        self.n += 1
        self.total += 1
        if self.n >= self.limit:
            self.batch.commit()
            self.batch = db.batch()
            self.n = 0

    def flush(self):
        if self.n:
            self.batch.commit()
            self.n = 0


def copy_room(old, new):
    """Copy every subcollection doc from room `old` to room `new`. Returns per-sub counts."""
    src = rooms_coll.document(old)
    dst = rooms_coll.document(new)
    counts = {}
    b = Batcher()
    for sub in src.collections():
        n = 0
        for snap in sub.stream():            # bulk read: data included, paginated
            data = snap.to_dict()
            if data.get("roomId") == old:
                data["roomId"] = new
            if not DRY_RUN:
                b.set(dst.collection(sub.id).document(snap.id), data)
            n += 1
        counts[sub.id] = n
    b.flush()
    return counts


def count_room(doc_ref):
    """Per-subcollection doc counts for a room (one level deep)."""
    return {sub.id: sum(1 for _ in sub.stream()) for sub in doc_ref.collections()}


def delete_room(old):
    """Delete every subcollection doc under room `old`, then the (empty) room doc."""
    src = rooms_coll.document(old)
    b = Batcher()
    total = 0
    for sub in src.collections():
        for snap in sub.stream():
            b.delete(snap.reference)
            total += 1
    b.flush()
    if not DRY_RUN:
        src.delete()
    return total


def main():
    mode = "DRY RUN — nothing written or deleted" if DRY_RUN else "LIVE — copy, verify, then delete"
    print(f"Mode: {mode}\nUID: {UID}\n", flush=True)

    # Pre-flight: no destination may already hold data.
    for old, new in RENAMES.items():
        dst = rooms_coll.document(new)
        if next(iter(dst.collections()), None) is not None or dst.get().exists:
            print(f"ABORT: destination '{new}' already exists with data. Resolve manually.")
            sys.exit(1)

    for old, new in RENAMES.items():
        print(f"=== '{old}' -> '{new}' ===", flush=True)
        before = count_room(rooms_coll.document(old))
        print(f"  source: {before} (total {sum(before.values())})", flush=True)

        copied = copy_room(old, new)
        print(f"  copied: {copied}" + (" (dry run)" if DRY_RUN else ""), flush=True)

        if DRY_RUN:
            print("  (skip verify + delete)\n", flush=True)
            continue

        after = count_room(rooms_coll.document(new))
        if after != before:
            print(f"  VERIFY FAILED: dest={after} != source={before}. NOT deleting source.")
            sys.exit(1)
        print(f"  verified dest == source: {after}", flush=True)

        deleted = delete_room(old)
        print(f"  deleted old '{old}' ({deleted} doc(s))\n", flush=True)

    print("Done.", flush=True)


if __name__ == "__main__":
    main()
