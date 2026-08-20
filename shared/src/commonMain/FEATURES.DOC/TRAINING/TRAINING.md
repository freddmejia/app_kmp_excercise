# FEATURE — Log Workout (Strength flow)

> Form for `STRENGTH`-type activities. Captures **which muscles were trained that day and how many sets**, nothing else. No weight, no reps, no exercise catalog. Consumes the **Activities** list (upstream) and the **Muscles** list (defined here).
>
> Consistency note: this document describes the current `STRENGTH` flow. The "exercise + weight × reps" version from the Activity doc remains a **future enhancement**, not a contradiction.

---

## 1. Purpose and boundary

Materializes a strength session as a set of **muscle → sets** entries. It computes no metrics: it produces the raw data (presence + effective/warmup sets) that Progress and Goals live on.

Boundary rule: the atomic unit is **the set**, split into *effective* vs. *warmup*. Load and reps do not enter.

---

## 2. Data model

Four tables. Two are an editable catalog (muscle hierarchy), two are the session.

### 2.1 `muscle_group` (catalog, editable)
| Field | Type | Req. | Description |
|---|---|---|---|
| `id` | INTEGER PK | auto | |
| `name` | TEXT | yes | "Chest", "Back", "Shoulders", "Legs", "Glutes"… |
| `is_archived` | INTEGER (0/1) | def. 0 | Archive, never delete with history |
| `created_at` | INTEGER | auto | |

### 2.2 `muscle` (catalog, editable, child of a group)
| Field | Type | Req. | Description |
|---|---|---|---|
| `id` | INTEGER PK | auto | |
| `group_id` | INTEGER FK | yes | Group it belongs to |
| `name` | TEXT | yes | "Upper chest", "Lower chest", "Lats"… |
| `is_archived` | INTEGER (0/1) | def. 0 | |
| `created_at` | INTEGER | auto | |

### 2.3 `workout_session` (header)
| Field | Type | Req. | Feeds |
|---|---|---|---|
| `id` | INTEGER PK | auto | |
| `activity_id` | INTEGER FK | yes | Segments goals by activity; must be a `STRENGTH` activity |
| `date_epoch_day` | INTEGER | yes | Goal window, frequency, streak, gaps |
| `notes` | TEXT | no | Qualitative context |
| `created_at` | INTEGER | auto | Audit / same-day ordering |

### 2.4 `session_muscle` (the entry: a muscle worked in the session)
| Field | Type | Req. | Feeds |
|---|---|---|---|
| `id` | INTEGER PK | auto | |
| `session_id` | INTEGER FK | yes | |
| `muscle_id` | INTEGER FK | yes | Presence → "which muscle I did / didn't"; links to its group |
| `effective_sets` | INTEGER | **yes (≥1)** | **Volume** per muscle and per group |
| `warmup_sets` | INTEGER | no (def. 0) | Warmup ratio; does not add to volume |

**`UNIQUE(session_id, muscle_id)`** → a muscle appears **only once per session**. If worked at two moments in the day, the sets are **summed** into that same row (no two entries of the same muscle on the same day).

---

## 3. Logging granularity (key decision)

In your examples you log at two levels: "upper chest" (child muscle) and "back 2 sets" (parent group). To support **both without aggregation ambiguity**, the rule is:

- **You always log against a `muscle` (leaf).** The session never points to a group directly.
- **Each group ships with a generic leaf** in the seed, e.g. `Back › General`, `Shoulders › General`. When you don't want sub-granularity, you log against that "General" leaf → still a leaf, still aggregates cleanly to its group.
- **Group volume = sum of the effective sets of all its leaves** (the specific ones + "General").

This way "upper/lower chest" coexist with "back (general)" and **everything aggregates uniformly to the group** with no special branches in the metric.

---

## 4. Logging flow (the "check")

1. Pick **activity** (`STRENGTH`) and **date**.
2. Muscle list **grouped by muscle group** (collapsible sections: Chest, Back, Shoulders…).
3. **Check per muscle** = "I did this muscle that day" → checking it adds it to the session and reveals its two counters:
   - **Effective sets** (required, ≥1)
   - **Warmup sets** (optional, def. 0)
4. Unchecking removes the entry from the session.

The check is not a separate field: **it is the existence of the `session_muscle` row**. Check = create entry; effective sets = the counted data point.

---

## 5. Displayed fields

**Header**: activity, date.
**Muscle list** (log picker): sections by group; per muscle → name, checkbox, and if checked, effective/warmup inputs.
**Session summary** (read): per muscle, effective + warmup; total effective sets for the session.

---

## 6. Filtering and ordering of muscles

| Filter | Values | Default |
|---|---|---|
| Text | substring over `name` | empty |
| Group | one / all | all (sections) |
| Status | active / archived / all | **active** |

Ordering within each group: by usage (session count desc.) to surface what you train most; alphabetical as an alternative. The log picker forces `status = active`.

---

## 7. Reactive CRUD

Two CRUDs coexist, both via SQLDelight `Flow` (a write re-emits, UI with no manual refresh):

**A. Session entries** (`session_muscle`): check/uncheck and set edits update the summary and totals instantly.

**B. Muscle catalog** (`muscle_group` + `muscle`): same pattern as Activity — reference by `id`, **archive instead of delete**, safe rename (history points to `id`). Creating a new muscle appears immediately within its group in the picker.

A single observable catalog query feeds the log picker and the muscle-management screen; a new entry is reflected in both in the same frame.

---

## 8. Integrity rules

1. `session_muscle.effective_sets ≥ 1` — no effective set, no entry (equivalent to not having checked it).
2. `UNIQUE(session_id, muscle_id)` — sets are summed, rows not duplicated.
3. **No deleting** a group/muscle with history → archive. Hard delete only with no sessions referencing it.
4. Reference by `id`, never by name → renaming does not alter history.
5. `workout_session.activity_id` must be of type `STRENGTH`.

---

## 9. Metric impact (traceability)

| Captured data | Metric it enables |
|---|---|
| Existence of `session_muscle` (presence) | **Which muscle/group I trained and which I didn't** (req 2), per window |
| `effective_sets` per muscle | **Volume per muscle** (sets/week) |
| `effective_sets` aggregated by group | **Volume per group** |
| Distinct days with a muscle/group | **Frequency** per muscle/group |
| `warmup_sets` vs `effective_sets` | **Warmup ratio** (session quality) |
| Trend of effective sets week over week | **Progression** (req 4) — by volume, not by load |
| `date_epoch_day` + `activity_id` | Goal completion per window, streak, gaps |

**Explicit consequence**: by not capturing weight/reps, **there is no load-based progression** (e1RM, "how much I lifted", "which exercise to improve" from the original plan). Strength progression here is **by set volume**. A valid decision, consistent with "which muscle I did, nothing else"; load can be reintroduced later without breaking this model.

---

## 10. Validation and edge cases

- Checking a muscle with no effective sets → blocks saving the entry (or auto-unchecks).
- Non-`STRENGTH` activity → this flow does not apply (use its type's form).
- Archiving a muscle with history → leaves the picker, its historical volume is preserved.
- Group with no active leaves → cannot log against it until it has at least one leaf (the "General" one prevents this via seed).

---

## 11. Open decisions (to ratify)

1. **"General" leaf per group** for group-level logging (my solution to the two-level nature of your examples). Does it fit, or would you rather log the group directly with no leaf?
2. **Muscle catalog seed**: start with typical groups + leaves (Chest: upper/lower/General; Back: lats/General…) editable, or empty?
3. **Picker order**: by usage (recommended) vs. alphabetical as default.
4. **Confirm** that this flow applies only to `STRENGTH` (your point 4 was left unanswered).