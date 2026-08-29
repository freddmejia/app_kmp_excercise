# FEATURE — Body Weight

> **Isolated** CRUD: no dependencies upstream or downstream. One measurement per day, filterable by date/month, with a line chart to show evolution.
>
> Consistency note: the "relative strength" metric (e1RM ÷ body weight) that would once have connected this screen to Strength **no longer applies**, because the Strength flow dropped load/reps. Body weight is therefore **fully standalone** today.

---

## 1. Purpose and boundary

Records body weight over time and charts it. It feeds no other screen. Its value is in the **trend reading**, not in the individual data point.

Boundary rule: one numeric measurement per day + its evolution. No body composition, measurements, or body-fat % (future extension if desired).

---

## 2. Data model

Table `body_weight`.

| Field | Type | Req. | Description |
|---|---|---|---|
| `id` | INTEGER PK | auto | |
| `date_epoch_day` | INTEGER **UNIQUE** | yes | One measurement per day |
| `weight_kg` | REAL | yes | Canonical unit **kg** (conversion to lb in the UI only if ever added) |
| `note` | TEXT | no | Context ("fasted", "post-trip"); does not feed any metric |

`date_epoch_day` is unique → logging twice on the same day **overwrites** (upsert), it does not duplicate.

---

## 3. Displayed fields

**History row**: date, weight, delta vs. the previous measurement (▲/▼ kg), note if present.
**Range header / summary**: current weight, change over the range, trend, rate (see §6).
**Chart**: see §5.

---

## 4. Filtering and ordering

| Filter | Values | Default |
|---|---|---|
| Date range | from–to | last 30 days |
| Month | month selector → that month's daily points | — |
| Presets | 30 d / 3 m / 6 m / 1 year / all | 30 d |

History order: date desc. The filter is a `StateFlow` that is `combine`d with the series → chart and list always consistent with the chosen range.

---

## 5. Line chart (the value decision)

Body weight fluctuates daily (water, food, sleep); **a raw-point line is misleading**. The chart therefore has two layers:

1. **Raw points** — each daily measurement, faint.
2. **Moving average (7 days)** — the main line, the one that reveals the real trend.

Mode by range:
- **Short range** (≤ ~90 days): X axis by day; raw + moving average.
- **Long range** (> ~90 days or yearly view): X axis by month; **monthly average** as the main series (more legible than thousands of points).

Gaps (days with no measurement): the line **connects across the gap** (visual interpolation), standard for weight trend. With a single measurement in the range → just the point, no line. The moving average requires a minimum number of points to be drawn.

---

## 6. Derived metrics (all computed, not stored)

| Metric | Calculation | Purpose |
|---|---|---|
| **Current weight** | last measurement | reference |
| **Change over range** | last − first (Δ kg and %) | period outcome |
| **Trend** | slope of the moving average → rising / falling / stable | real direction without noise |
| **Rate** | Δ kg ÷ weeks in range (kg/week) | deficit/surplus control |
| **Min / max of range** | extremes | dispersion |

---

## 7. Reactive CRUD

Series observable via SQLDelight `Flow`; a write re-emits and refreshes list + chart + summary with no manual refresh.

- **Create / upsert**: one per day; if the day already exists, it overwrites.
- **Read**: series filtered by range, reactive.
- **Update**: edit a day's weight or note.
- **Delete**: **hard delete allowed with no restriction.**

This is where it differs from Activities and Muscles: since this entity **has no dependents** (nothing references a weight measurement), deleting is safe and breaks no historical metric. That is why **"archive instead of delete" does not apply** — that rule existed to protect referenced history, and here there is none. The criterion is the same as always (protect integrity); here there is simply nothing to protect.

---

## 8. Validation and edge cases

- `weight_kg` required and > 0 → blocks save.
- Logging a day that already exists → confirm overwrite (upsert).
- Range with no measurements → empty chart with a "no data in this range" state.
- A single measurement → point with no line; trend/rate metrics show "not enough data".

---

## 9. Open decisions (to ratify)

1. **Moving-average window**: 7 days (recommended) vs. configurable.
2. **Short/long range threshold** for switching to the monthly view: ~90 days vs. another.
3. **Overwrite confirmation** when logging a day that already exists: prompt for it or silent upsert?
4. **Per-measurement note**: keep it, or drop it to make it even more minimal?
