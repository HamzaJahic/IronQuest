# IronQuest

A gamified 4-day Upper/Lower workout tracker. Kotlin · Jetpack Compose · Room · Hilt · MVVM · Coroutines/StateFlow.

## Run it

Open the folder in Android Studio (Ladybug or newer), let Gradle sync, run `app`. Unit tests: `./gradlew testDebugUnitTest`.

## Architecture

```
domain/        Pure Kotlin — no Android imports
  model/       ExerciseDefinition, WorkoutTemplate, LoggedSet, session/result models
  program/     ProgramCatalog: exercises + the 4-day split + rotation
  usecase/     ProgressiveOverloadUseCase, BuildQuestUseCase
  progression/ LevelCurve, XpRules, detectLevelUp
  repository/  WorkoutRepository interface
data/
  local/       Room: entities, DAOs, IronQuestDatabase, SeedCallback
  repository/  OfflineWorkoutRepository (transactions, XP awarding)
di/            Hilt modules (DB, Clock, repository binding)
ui/
  theme/       Dark-only game palette + HUD typography
  components/  NeonPanel, neonGlow, XpBar, LevelBadge, StatChip, GlowButton
  dashboard/   Character sheet, attributes, main quest, quest board
  workout/     Active workout: Ghost Mode, overload suggestions, set logging, XP toasts, victory
  calendar/    Month grid with glowing completed days + battle-log bottom sheet
  navigation/  NavHost + bottom bar
```

### Room schema (v1)

| Table | Purpose |
|---|---|
| `exercises` | Catalog, upserted from `ProgramCatalog` on every open (no migration needed to add exercises) |
| `workouts` | One row per session. `completedAtMillis == null` → in progress, resumable |
| `sets` | reps / weightKg / durationMinutes, FK to workout (CASCADE) and exercise (RESTRICT), rep-range snapshot, XP awarded |
| `player_profile` | Single row: total XP + per-stat XP + quests completed |

Schemas are exported to `app/schemas/` on build — commit them and add `Migration`s + a `MigrationTestHelper` test before bumping the version.

## Game rules (all in `XpRules` / `ProgressiveOverloadUseCase`)

**Progressive overload**, evaluated against the last *completed* session of the same workout:

| Rule | Condition | Suggestion |
|---|---|---|
| Level Up | all prescribed sets logged, all at the working weight, all ≥ top of range | +2.5 kg (upper/small lifts) or +5 kg (squat, deadlift, RDL, leg press) |
| Deload | ≥ 2 sets below bottom of range | −10 %, rounded **down** to 2.5 kg |
| Hold | anything else (incl. one missed set) | same weight |

**XP:** 10 per set (cardio: 1/min, min 10) · +5 "Ghost Slayer" when a set beats last session's same set (heavier with ≥ reps, or ≥ weight with more reps — lighter-for-more-reps doesn't count) · +100 Quest Complete. Set XP also feeds the exercise's stat: legs → CON, upper → STR, cycling → STA.

**Levels:** character L→L+1 costs `100 + 50·(L−1)` XP; stats `40 + 20·(L−1)`.

## Design decisions worth knowing

- The workout row is created on the **first logged set**, not on screen open, so opening and backing out leaves no empty sessions. A `Mutex` serialises writes so fast double taps can't create two rows.
- Ghost data is a **snapshot** taken when the screen loads, so it never shifts mid-session.
- Editing a logged set updates it but never re-awards XP; completing twice is idempotent.
- Weight input accepts `,` as decimal separator.
- Calendar days are keyed on the session's *start* date (a workout past midnight stays on the day it started).
- The neon halo uses `Paint.setShadowLayer`, which is hardware-accelerated only on API 28+. On 26–27 the halo is absent; borders and gradients still carry the look.
