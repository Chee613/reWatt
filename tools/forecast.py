"""
reWatt Prophet Occupancy Forecaster
====================================
Runs Prophet for EVERY account that has sensor history, not just fsktm.
Each account gets its own forecast stored under its own UID.

Run on your laptop (no phone connection needed):
    python forecast.py

Prerequisites (one-time setup):
    pip install prophet pandas firebase-admin

You also need serviceAccountKey.json in the repo root (one level above this tools/ folder).
Download from: Firebase console → Project settings → Service accounts →
               Generate new private key
"""

import os
import firebase_admin
from firebase_admin import credentials, firestore
from prophet import Prophet
import pandas as pd
from datetime import datetime, timedelta, timezone

MYT = timezone(timedelta(hours=8))  # Malaysia Time (UTC+8)

_KEY = os.path.join(os.path.dirname(__file__), "..", "serviceAccountKey.json")
cred = credentials.Certificate(_KEY)
firebase_admin.initialize_app(cred)
db = firestore.client()

ROOMS = ["Hall 1", "Hall 2", "Hall 3", "Hall 4"]
MIN_RECORDS = 10  # Skip forecast if not enough history yet


def get_all_uids() -> list[dict]:
    """Return list of {uid, username/email} for every registered account."""
    users = []
    for doc in db.collection("rewatt_users").stream():
        data = doc.to_dict() or {}
        label = data.get("username") or data.get("email") or doc.id[:12]
        users.append({"uid": doc.id, "label": label})
    return users


def forecast_room(uid: str, room_id: str) -> None:
    cutoff_ms = int((datetime.now(timezone.utc).timestamp() - 30 * 86400) * 1000)

    docs = (
        db.collection("rewatt_users").document(uid)
          .collection("rooms").document(room_id)
          .collection("telemetry")
          .where("perceptionCapturedAt", ">=", cutoff_ms)
          .order_by("perceptionCapturedAt")
          .stream()
    )

    rows = []
    for d in docs:
        data = d.to_dict()
        ts    = data.get("perceptionCapturedAt")
        count = data.get("personCount")
        if ts is not None and count is not None:
            rows.append({
                "ds": datetime.fromtimestamp(ts / 1000, tz=MYT).replace(tzinfo=None),
                "y":  float(count),
            })

    print(f"    {len(rows)} telemetry records found")
    if len(rows) < MIN_RECORDS:
        print(f"    Skipping — need at least {MIN_RECORDS} records")
        return

    df = pd.DataFrame(rows)
    model = Prophet(daily_seasonality=True, weekly_seasonality=True)
    model.fit(df)

    future   = model.make_future_dataframe(periods=48, freq="h")
    forecast = model.predict(future)

    now      = datetime.now(MYT).replace(tzinfo=None)
    upcoming = forecast[forecast["ds"] > now].head(1)
    if upcoming.empty:
        print("    No upcoming forecast window — skipping")
        return

    row       = upcoming.iloc[0]
    predicted = max(0, int(round(row["yhat"])))
    level     = "likely_occupied" if predicted >= 5 else "likely_empty"
    next_label = row["ds"].strftime("%H:%M")

    db.collection("rewatt_users").document(uid) \
      .collection("rooms").document(room_id) \
      .collection("forecast").document("latest") \
      .set({
          "predictedOccupancy":    predicted,
          "occupancyLevel":        level,
          "nextSessionStartLabel": next_label,
          "forecastedAt":          int(datetime.utcnow().timestamp() * 1000),
          "source":                "prophet",
      })

    print(f"    Forecast written: {predicted} people | {level} | next={next_label}")


if __name__ == "__main__":
    users = get_all_uids()
    if not users:
        print("No accounts found in Firestore. Make sure at least one phone has signed in.")
        exit(1)

    print(f"Found {len(users)} account(s): {[u['label'] for u in users]}\n")

    for user in users:
        uid   = user["uid"]
        label = user["label"]
        print(f"=== {label} (uid={uid[:12]}...) ===")
        for room in ROOMS:
            print(f"  [{room}]")
            try:
                forecast_room(uid, room)
            except Exception as e:
                print(f"    ERROR: {e}")
        print()

    print("Done. Open any Admin phone to see updated predictions.")
