"""
Rewatt x Prophet Demo
=====================
Uses four weeks of sensor-like 15-minute occupancy data for "Lecture Hall B" to:
  - Forecast the next 7 days  -> forecast_week.png
  - Forecast a single day     -> forecast_day.png

This version includes fixed zero-occupancy timetable constraints:
  - Every weekday: 18:30 - 07:30 next day = 0 occupancy
  - MON: 10:15 - 11:45, 15:15 - 16:45
  - TUE: 11:15 - 12:45
  - WED: 11:15 - 12:45, 15:15 - 16:45
  - THU: 08:00 - 08:45, 12:15 - 15:45
  - FRI: 12:15 - 15:45
  - SAT/SUN: all day

Prophet understands day-of-week and Malaysia public holidays natively.

HOW TO RUN (VS Code)
--------------------
1. Open a terminal in VS Code (Ctrl+`) and activate the virtual environment:
     .venv\Scripts\activate          # Windows
     source .venv/bin/activate       # macOS / Linux
2. Install dependencies:
     pip install -r requirements.txt
3. Run: python prophet_demo.py
"""

# %% [markdown]
# ## 1. Imports

# %%
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from datetime import datetime, timedelta, time
from prophet import Prophet

np.random.seed(42)
plt.rcParams["figure.dpi"] = 110


# %% [markdown]
# ## 2. Generate four weeks of sensor-like occupancy history data
#
# This version uses 15-minute intervals because the zero-occupancy
# timetable contains times such as 10:15, 11:45, 15:15, etc.
#
# Base sensor-like pattern:
#   Weekday daytime allowed slots : 30-120 people
#   Forced-empty timetable slots  : 0 people
#   Weekend                       : 0 people
#
# Important:
#   The forced-empty timetable is applied consistently across all 4 weeks.

# %%
START = datetime(2026, 4, 22, 0, 0)
DAYS = 28
FREQ_MINUTES = 15
PERIODS = DAYS * 24 * (60 // FREQ_MINUTES)
CAPACITY = 120


def minutes_since_midnight(t: time) -> int:
    """Convert a time object into minutes after midnight."""
    return t.hour * 60 + t.minute


def in_time_range(current: time, start: time, end: time) -> bool:
    """
    Return True if current time is inside [start, end).

    Supports normal ranges, e.g. 10:15-11:45,
    and overnight ranges, e.g. 18:30-07:30 next day.
    """
    current_min = minutes_since_midnight(current)
    start_min = minutes_since_midnight(start)
    end_min = minutes_since_midnight(end)

    if start_min < end_min:
        return start_min <= current_min < end_min

    # Overnight range: example 18:30-07:30 next day
    return current_min >= start_min or current_min < end_min


# Python weekday format:
# Monday = 0, Tuesday = 1, ..., Sunday = 6
ZERO_OCCUPANCY_INTERVALS = {
    0: [
        (time(10, 15), time(11, 45)),
        (time(15, 15), time(16, 45)),
    ],  # MON

    1: [
        (time(11, 15), time(12, 45)),
    ],  # TUE

    2: [
        (time(11, 15), time(12, 45)),
        (time(15, 15), time(16, 45)),
    ],  # WED

    3: [
        (time(8, 0), time(8, 45)),
        (time(12, 15), time(15, 45)),
    ],  # THU

    4: [
        (time(12, 15), time(15, 45)),
    ],  # FRI
}


def is_forced_zero_occupancy(ts: datetime) -> bool:
    """
    Return True if the lecture hall must have 0 occupancy at timestamp ts.
    """
    wd = ts.weekday()
    current_time = ts.time()

    # Saturday and Sunday: all day zero
    if wd >= 5:
        return True

    # Every weekday: 18:30 - 07:30 next day zero
    if in_time_range(current_time, time(18, 30), time(7, 30)):
        return True

    # Day-specific forced-zero intervals
    for start_time, end_time in ZERO_OCCUPANCY_INTERVALS.get(wd, []):
        if in_time_range(current_time, start_time, end_time):
            return True

    return False


def generate_sensor_like_occupancy(ts: datetime) -> int:
    """
    Generate sensor-like occupancy only for allowed lecture-hall operating periods.

    In the real system, this part should be replaced by actual sensor data.
    Forced-zero periods are handled before this function is called.
    """
    hh = ts.hour

    # Morning / afternoon teaching period
    if 7 <= hh < 18:
        return np.random.randint(30, CAPACITY + 1)

    # Safety fallback, normally avoided by forced-zero schedule
    return np.random.randint(0, 15)


records = []

for i in range(PERIODS):
    ts = START + timedelta(minutes=i * FREQ_MINUTES)

    if is_forced_zero_occupancy(ts):
        occ = 0
    else:
        occ = generate_sensor_like_occupancy(ts)

    records.append({
        "timestamp": ts,
        "occupancy": float(occ),
    })

df = pd.DataFrame(records)

print(df.head(24).to_string(index=False))
print(f"\nTotal rows of history: {len(df)}")
print(f"Data frequency: every {FREQ_MINUTES} minutes")

# Sanity check: confirm all forced-zero rows are really zero
forced_zero_mask = df["timestamp"].apply(is_forced_zero_occupancy)
non_zero_violations = df.loc[forced_zero_mask & (df["occupancy"] != 0)]

if len(non_zero_violations) == 0:
    print("Sanity check passed: all required zero-occupancy intervals are 0.")
else:
    print("WARNING: Some forced-zero intervals are not zero:")
    print(non_zero_violations.to_string(index=False))


# %% [markdown]
# ## 3. Visualise the history

# %%
fig, ax = plt.subplots(figsize=(14, 3.5))

ax.plot(
    df["timestamp"],
    df["occupancy"],
    lw=0.8,
    color="#2c3e50",
)

ax.set_title(
    "Lecture Hall B - four weeks of sensor-based 15-minute occupancy history",
    weight="bold",
)
ax.set_ylabel("People present")
ax.grid(alpha=0.3)

plt.tight_layout()
plt.savefig("history.png")
plt.show()


# %% [markdown]
# ## 4. Fit Prophet model

# %%
prophet_df = df.rename(columns={
    "timestamp": "ds",
    "occupancy": "y",
})

model = Prophet(
    weekly_seasonality=True,
    daily_seasonality=True,
    seasonality_mode="additive",
    interval_width=0.80,
)

model.add_country_holidays(country_name="MY")  # Malaysia public holidays
model.fit(prophet_df)

print("Prophet model fitted.")


# %% [markdown]
# ## 5. Forecast - single day, next 24 hours

# %%
last_ts = df["timestamp"].iloc[-1]
steps_per_day = 24 * (60 // FREQ_MINUTES)

future_day = [
    last_ts + timedelta(minutes=FREQ_MINUTES * (i + 1))
    for i in range(steps_per_day)
]

forecast_day = model.predict(pd.DataFrame({"ds": future_day}))

pred_day = np.maximum(forecast_day["yhat"].values, 0)
lower_day = np.maximum(forecast_day["yhat_lower"].values, 0)
upper_day = np.maximum(forecast_day["yhat_upper"].values, 0)

# Enforce the same known zero-occupancy timetable on the forecast.
# This is useful for an electricity-saving system because the timetable rule is known.
forced_zero_future_day = np.array([
    is_forced_zero_occupancy(ts)
    for ts in future_day
])

pred_day[forced_zero_future_day] = 0
lower_day[forced_zero_future_day] = 0
upper_day[forced_zero_future_day] = 0

print(f"\n15-minute forecast for {future_day[0]:%A, %Y-%m-%d}:")

for ts, p in zip(future_day, pred_day):
    if is_forced_zero_occupancy(ts):
        tag = "  <- forced empty"
    elif p < 5:
        tag = "  <- likely empty"
    else:
        tag = ""

    print(f"  {ts:%H:%M} : {p:5.1f} people{tag}")


# Show every 4th label so the x-axis is not too crowded
x_pos = np.arange(len(future_day))
label_step = 4

hour_labels = [
    ts.strftime("%H:%M") if i % label_step == 0 else ""
    for i, ts in enumerate(future_day)
]

fig, ax = plt.subplots(figsize=(14, 5))

ax.bar(
    x_pos,
    pred_day,
    color="#e74c3c",
    alpha=0.7,
    label="Forecast",
)

ax.fill_between(
    x_pos,
    lower_day,
    upper_day,
    alpha=0.2,
    color="#e74c3c",
    label="80% confidence band",
)

ax.set_xticks(x_pos)
ax.set_xticklabels(
    hour_labels,
    rotation=45,
    ha="right",
    fontsize=8,
)

ax.set_title(
    f"Lecture Hall B - Single Day Forecast: {future_day[0]:%A, %d %B %Y}",
    fontsize=13,
    weight="bold",
)

ax.set_ylabel("People present")
ax.legend(loc="upper left")
ax.grid(alpha=0.3, axis="y")

plt.tight_layout()
plt.savefig("forecast_day.png")
plt.show()

print("Saved: forecast_day.png")


# %% [markdown]
# ## 6. Forecast - whole week, next 7 days

# %%
future_week = [
    last_ts + timedelta(minutes=FREQ_MINUTES * (i + 1))
    for i in range(7 * steps_per_day)
]

forecast_week = model.predict(pd.DataFrame({"ds": future_week}))

pred_week = np.maximum(forecast_week["yhat"].values, 0)
lower_week = np.maximum(forecast_week["yhat_lower"].values, 0)
upper_week = np.maximum(forecast_week["yhat_upper"].values, 0)

# Enforce the same known zero-occupancy timetable on the forecast.
forced_zero_future_week = np.array([
    is_forced_zero_occupancy(ts)
    for ts in future_week
])

pred_week[forced_zero_future_week] = 0
lower_week[forced_zero_future_week] = 0
upper_week[forced_zero_future_week] = 0

print("\n15-minute forecast for the next 7 days:")

for day in range(7):
    start = day * steps_per_day
    end = (day + 1) * steps_per_day

    day_ts = future_week[start]
    day_preds = pred_week[start:end]
    day_times = future_week[start:end]

    print(f"\n  --- {day_ts:%A, %Y-%m-%d} ---")

    for ts, p in zip(day_times, day_preds):
        if is_forced_zero_occupancy(ts):
            tag = "  <- forced empty"
        elif p < 5:
            tag = "  <- likely empty"
        else:
            tag = ""

        print(f"    {ts:%H:%M} : {p:5.1f} people{tag}")


fig, ax = plt.subplots(figsize=(16, 5))

ax.plot(
    future_week,
    pred_week,
    color="#2980b9",
    lw=2,
    label="Prophet forecast, timetable-adjusted",
)

ax.fill_between(
    future_week,
    lower_week,
    upper_week,
    alpha=0.15,
    color="#2980b9",
    label="80% confidence band",
)

# Day boundary lines
for day in range(1, 7):
    boundary = future_week[day * steps_per_day]

    ax.axvline(
        boundary,
        color="gray",
        ls="--",
        lw=0.8,
        alpha=0.5,
    )

    ax.text(
        boundary,
        ax.get_ylim()[1] * 0.95,
        boundary.strftime("%a"),
        ha="center",
        fontsize=8,
        color="gray",
    )

ax.set_title(
    "Lecture Hall B - 7-Day Occupancy Forecast",
    fontsize=13,
    weight="bold",
)

ax.set_ylabel("People present")
ax.legend(loc="upper left")
ax.grid(alpha=0.3)

plt.tight_layout()
plt.savefig("forecast_week.png")
plt.show()

print("Saved: forecast_week.png")