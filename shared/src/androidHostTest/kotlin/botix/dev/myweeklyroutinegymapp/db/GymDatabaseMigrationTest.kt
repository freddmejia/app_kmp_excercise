package botix.dev.myweeklyroutinegymapp.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The migrations that carry an existing database forward: 1 -> 2 adds activity
 * categories, 2 -> 3 adds the activity -> muscle link.
 *
 * This is the path that never runs on a developer machine: a fresh install calls
 * `Schema.create()` and gets the current schema directly, so the migration only
 * executes on a phone that already has data. Getting it wrong is invisible until an
 * existing user updates and the app dies on "no such table: activity_category".
 */
class GymDatabaseMigrationTest {

    /**
     * Schema version 1, exactly as it shipped.
     *
     * Written out rather than derived from the current `.sq` files, because that is
     * the point: this is a frozen copy of what is on users' phones, and it must not
     * follow later edits. When it stops matching what version 1 really was, these
     * tests stop testing the upgrade anyone actually performs.
     */
    private fun v1Database(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        listOf(
            """
            CREATE TABLE activity (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                name             TEXT NOT NULL,
                measurement_type TEXT NOT NULL,
                icon             TEXT,
                color            TEXT,
                is_archived      INTEGER NOT NULL DEFAULT 0,
                is_seed          INTEGER NOT NULL DEFAULT 0,
                created_at       INTEGER NOT NULL
            )
            """,
            "CREATE UNIQUE INDEX activity_active_name_unique ON activity(name) WHERE is_archived = 0",
            "CREATE INDEX activity_archived_idx ON activity(is_archived)",
            """
            CREATE TABLE muscle_group (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                name        TEXT NOT NULL,
                is_archived INTEGER NOT NULL DEFAULT 0,
                created_at  INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE muscle (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id    INTEGER NOT NULL REFERENCES muscle_group(id),
                name        TEXT NOT NULL,
                is_archived INTEGER NOT NULL DEFAULT 0,
                created_at  INTEGER NOT NULL
            )
            """,
            "CREATE INDEX muscle_group_id_idx ON muscle(group_id)",
            """
            CREATE TABLE workout_session (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                activity_id    INTEGER NOT NULL REFERENCES activity(id),
                date_epoch_day INTEGER NOT NULL,
                notes          TEXT,
                created_at     INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE session_muscle (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id     INTEGER NOT NULL REFERENCES workout_session(id) ON DELETE CASCADE,
                muscle_id      INTEGER NOT NULL REFERENCES muscle(id),
                effective_sets INTEGER NOT NULL CHECK (effective_sets >= 1),
                warmup_sets    INTEGER NOT NULL DEFAULT 0 CHECK (warmup_sets >= 0),
                UNIQUE (session_id, muscle_id)
            )
            """,
            "CREATE INDEX workout_session_date_idx ON workout_session(date_epoch_day)",
            "CREATE INDEX workout_session_activity_idx ON workout_session(activity_id)",
            "CREATE INDEX session_muscle_session_idx ON session_muscle(session_id)",
            "CREATE INDEX session_muscle_muscle_idx ON session_muscle(muscle_id)",
            """
            CREATE TABLE body_weight (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                date_epoch_day INTEGER NOT NULL UNIQUE,
                weight_kg      REAL NOT NULL,
                note           TEXT
            )
            """,
            "CREATE INDEX body_weight_date_idx ON body_weight(date_epoch_day)",
        ).forEach { driver.execute(null, it.trimIndent(), 0) }
        return driver
    }

    @Test
    fun schemaVersionMatchesTheMigrationsOnDisk() {
        // Guards the migration files' existence. Each .sqm adds one; delete one and
        // this drops back, every installed app keeps its old database, never creates
        // the new table, and crashes on the first query against it.
        assertEquals(7L, GymDatabase.Schema.version)
    }

    @Test
    fun migratingFromVersionThreeAddsOnlyTheDistanceAndTimeColumns() {
        // The upgrade path for anyone already on the categories + muscle-link build.
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(fresh)

        val migrated = v1Database()
        GymDatabase.Schema.migrate(migrated, 1, 3)
        assertTrue(!migrated.columnsOf("workout_session").contains("distance_meters"))

        GymDatabase.Schema.migrate(migrated, 3, 4)

        // The two columns this step is about, present and named.
        val afterStepFour = migrated.columnsOf("workout_session")
        assertTrue(afterStepFour.contains("distance_meters"))
        assertTrue(afterStepFour.contains("duration_seconds"))
        // Still keyed by activity here; step 7 rebuilds the table around a category.
        assertTrue(afterStepFour.contains("activity_id"))

        // And the two paths converge once the remaining steps have run.
        GymDatabase.Schema.migrate(migrated, 4, GymDatabase.Schema.version)
        assertEquals(fresh.tableNames(), migrated.tableNames())
        assertEquals(fresh.columnsOf("workout_session"), migrated.columnsOf("workout_session"))
    }

    @Test
    fun existingSessionsSurviveWithNoDistanceOrTime() {
        val driver = v1Database()
        driver.execute(
            null,
            "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
                "VALUES ('Gym', 'STRENGTH', NULL, NULL, 0, 0, 0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO workout_session (activity_id, date_epoch_day, notes, created_at) " +
                "VALUES (1, 20000, NULL, 7)",
            0,
        )

        GymDatabase.Schema.migrate(driver, 1, GymDatabase.Schema.version)

        val stored = createDatabase(driver).workoutSessionQueries.selectSessionById(1).executeAsOne()
        assertEquals(20_000L, stored.date_epoch_day)
        // Null, not zero: a strength session never measured a distance, and a zero
        // would claim it covered none.
        assertNull(stored.distance_meters)
        assertNull(stored.duration_seconds)
    }

    /**
     * Step 1 -> 2, pinned at 2 rather than at the current version.
     *
     * `activity` no longer exists at head — 6.sqm drops it — so this reads the table
     * through raw SQL at the version that still has it. The step is still worth a
     * test: it is the one that put a REFERENCES clause on an ADD COLUMN, which SQLite
     * only permits because the column defaults to NULL.
     */
    @Test
    fun existingActivitiesSurviveStepTwoAsUncategorised() {
        val driver = v1Database()
        driver.execute(
            null,
            "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
                "VALUES ('Gym', 'STRENGTH', NULL, NULL, 0, 0, 5)",
            0,
        )

        GymDatabase.Schema.migrate(driver, 1, 2)

        assertEquals("Gym", driver.scalar("SELECT name FROM activity"))
        assertEquals("STRENGTH", driver.scalar("SELECT measurement_type FROM activity"))
        assertEquals("5", driver.scalar("SELECT created_at FROM activity"))
        assertNull(driver.scalar("SELECT category_id FROM activity"))
    }

    @Test
    fun theMigratedDatabaseSupportsTheWholeCategoryFeature() {
        val driver = v1Database()
        driver.execute(
            null,
            "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
                "VALUES ('Running', 'DISTANCE_TIME', NULL, NULL, 0, 0, 0)",
            0,
        )

        GymDatabase.Schema.migrate(driver, 1, GymDatabase.Schema.version)
        val db = createDatabase(driver)

        // Exercises the table, its columns and the join to what now references it — if
        // the migration DDL had drifted from ActivityCategory.sq, one of these fails.
        db.activityCategoryQueries.insert("Cardio", "🏃", null, 0L, MeasurementType.STRENGTH)
        val categoryId = db.activityCategoryQueries.lastInsertRowId().executeAsOne()
        db.workoutSessionQueries.insertSession(categoryId, 20_000L, null, 0L)

        val usage = db.activityCategoryQueries.selectActiveWithUsage().executeAsOne()
        assertEquals("Cardio", usage.name)
        // Logs, not activities: the count followed the thing that references a
        // category when activities stopped existing.
        assertEquals(1L, usage.session_count)

        // The unique-name index arrived with the migration too, not just the table.
        assertTrue(db.activityCategoryQueries.countActiveWithName("Cardio", 0L).executeAsOne() > 0)
    }

    @Test
    fun migratingProducesTheSameActivityColumnsAsAFreshInstall() {
        val migrated = v1Database()
        GymDatabase.Schema.migrate(migrated, 1, GymDatabase.Schema.version)

        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(fresh)

        // The two paths must converge. Comparing the columns catches the classic
        // migration bug — a column added to the .sq file but forgotten in the .sqm,
        // which only ever breaks for users who upgrade.
        assertEquals(columnsOfActivity(fresh), columnsOfActivity(migrated))
    }

    @Test
    fun migratingFromVersionTwoAddsOnlyTheLinkTable() {
        // The upgrade path for anyone who already installed the categories build.
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(fresh)
        // Not on a fresh install any more: step 7 drops it again. This test is about
        // the window between 3 and 7 in which it exists.
        assertTrue(!fresh.tableNames().contains("activity_muscle"))

        val migrated = v1Database()
        GymDatabase.Schema.migrate(migrated, 1, 2)
        assertTrue(!migrated.tableNames().contains("activity_muscle"))

        GymDatabase.Schema.migrate(migrated, 2, 3)
        // The link table arrives at step 3 and is dropped again at step 7, so this is
        // the only window in which it exists at all.
        assertTrue(migrated.tableNames().contains("activity_muscle"))

        GymDatabase.Schema.migrate(migrated, 3, GymDatabase.Schema.version)
        assertTrue(!migrated.tableNames().contains("activity_muscle"))
        assertEquals(fresh.tableNames(), migrated.tableNames())
    }

    @Test
    fun migratingFromVersionFourAddsTheSeedColumnsAndLedger() {
        // The upgrade path for anyone on the build that shipped no muscle catalogue —
        // which is everyone, since this is the first version that ships one.
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(fresh)

        val migrated = v1Database()
        GymDatabase.Schema.migrate(migrated, 1, 4)
        assertTrue(!migrated.tableNames().contains("muscle_seed"))
        assertTrue(!migrated.columnsOf("muscle").contains("seed_key"))

        GymDatabase.Schema.migrate(migrated, 4, 5)

        assertEquals(fresh.columnsOf("muscle"), migrated.columnsOf("muscle"))
        assertEquals(fresh.columnsOf("muscle_group"), migrated.columnsOf("muscle_group"))
        assertTrue(migrated.tableNames().contains("muscle_seed"))

        // Table parity only once the later steps have run — step 6 adds one of its
        // own. Keyed off Schema.version so a new migration extends this test.
        GymDatabase.Schema.migrate(migrated, 5, GymDatabase.Schema.version)
        assertEquals(fresh.tableNames(), migrated.tableNames())
    }

    @Test
    fun migratingFromVersionFiveMovesMeasurementOntoTheCategory() {
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(fresh)

        val migrated = v1Database()
        GymDatabase.Schema.migrate(migrated, 1, 5)
        assertTrue(!migrated.columnsOf("activity_category").contains("measurement_type"))

        GymDatabase.Schema.migrate(migrated, 5, 6)
        assertTrue(migrated.columnsOf("activity_category").contains("measurement_type"))

        // Table parity only at the end: step 7 drops two of its own.
        GymDatabase.Schema.migrate(migrated, 6, GymDatabase.Schema.version)
        assertEquals(fresh.tableNames(), migrated.tableNames())
        assertEquals(
            fresh.columnsOf("activity_category"),
            migrated.columnsOf("activity_category"),
        )
    }

    @Test
    fun aCategoryInheritsTheMeasurementOfItsActivities() {
        val driver = v1Database()
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('Running', 'DISTANCE_TIME', NULL, NULL, 0, 0, 0)", 0)
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('Cycling', 'DISTANCE_TIME', NULL, NULL, 0, 0, 0)", 0)

        // The category table only exists from version 2, so it is filled between the
        // steps rather than in v1Database().
        GymDatabase.Schema.migrate(driver, 1, 5)
        driver.execute(null, "INSERT INTO activity_category (name, icon, color, is_archived, created_at) " +
            "VALUES ('Cardio', NULL, NULL, 0, 0)", 0)
        driver.execute(null, "UPDATE activity SET category_id = 1", 0)

        GymDatabase.Schema.migrate(driver, 5, 6)
        val db = createDatabase(driver)

        // Two distance activities, so the category is a distance category. Nothing was
        // split: there was nothing to disagree with.
        val categories = db.activityCategoryQueries.selectAll().executeAsList()
        assertEquals(1, categories.size)
        assertEquals(MeasurementType.DISTANCE_TIME, categories.single().measurement_type)
        // Read raw: `activity` still exists at 6 and is dropped at 7.
        assertEquals("2", driver.scalar("SELECT COUNT(*) FROM activity WHERE category_id = 1"))
    }

    @Test
    fun aCategoryHoldingBothKindsSplitsRatherThanLying() {
        val driver = v1Database()
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('Running', 'DISTANCE_TIME', NULL, NULL, 0, 0, 0)", 0)
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('Cycling', 'DISTANCE_TIME', NULL, NULL, 0, 0, 0)", 0)
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('Bench press', 'STRENGTH', NULL, NULL, 0, 0, 0)", 0)

        GymDatabase.Schema.migrate(driver, 1, 5)
        driver.execute(null, "INSERT INTO activity_category (name, icon, color, is_archived, created_at) " +
            "VALUES ('Everything', '🏃', NULL, 0, 42)", 0)
        driver.execute(null, "UPDATE activity SET category_id = 1", 0)

        GymDatabase.Schema.migrate(driver, 5, 6)
        val db = createDatabase(driver)

        val byName = db.activityCategoryQueries.selectAll().executeAsList().associateBy { it.name }
        assertEquals(2, byName.size)
        // The majority kind keeps the original row, and with it the original id that
        // nothing else has to be told about.
        assertEquals(MeasurementType.DISTANCE_TIME, byName.getValue("Everything").measurement_type)
        assertEquals(1L, byName.getValue("Everything").id)

        // The minority moves to a copy carrying the original's icon and created_at —
        // the same label with a different contract, not a new category.
        val split = byName.getValue("Everything · STRENGTH")
        assertEquals(MeasurementType.STRENGTH, split.measurement_type)
        assertEquals("🏃", split.icon)
        assertEquals(42L, split.created_at)

        assertEquals("1", driver.scalar("SELECT category_id FROM activity WHERE name = 'Running'"))
        assertEquals("1", driver.scalar("SELECT category_id FROM activity WHERE name = 'Cycling'"))
        assertEquals(
            split.id.toString(),
            driver.scalar("SELECT category_id FROM activity WHERE name = 'Bench press'"),
        )

        // No activity's own measurement type was rewritten: the split is about where
        // they are filed, never about what they are.
        assertEquals(
            "STRENGTH",
            driver.scalar("SELECT measurement_type FROM activity WHERE name = 'Bench press'"),
        )
    }

    /**
     * The rebuild in 6.sqm, run with foreign keys **on**.
     *
     * That is the condition it exists for. iOS enforces them, and with enforcement on
     * a `DROP TABLE` performs an implicit `DELETE FROM` first — so a rebuild in the
     * wrong order cascades through `session_muscle` and deletes every set ever
     * logged, silently and irreversibly. The JDBC driver has them off by default, so
     * without this line the test would pass on a mistake that destroys the user's
     * history on their phone.
     */
    private fun v6WithForeignKeys(): JdbcSqliteDriver {
        val driver = v1Database()
        GymDatabase.Schema.migrate(driver, 1, 6)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        return driver
    }

    @Test
    fun everySetSurvivesTheRebuildWithForeignKeysOn() {
        val driver = v1Database()
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('chest day', 'STRENGTH', NULL, NULL, 0, 0, 0)", 0)
        driver.execute(null, "INSERT INTO muscle_group (name, is_archived, created_at) VALUES ('Chest', 0, 0)", 0)
        driver.execute(null, "INSERT INTO muscle (group_id, name, is_archived, created_at) VALUES (1, 'Upper chest', 0, 0)", 0)
        driver.execute(null, "INSERT INTO muscle (group_id, name, is_archived, created_at) VALUES (1, 'Lower chest', 0, 0)", 0)
        driver.execute(null, "INSERT INTO workout_session (activity_id, date_epoch_day, notes, created_at) " +
            "VALUES (1, 20000, 'a note', 5)", 0)
        driver.execute(null, "INSERT INTO session_muscle (session_id, muscle_id, effective_sets, warmup_sets) " +
            "VALUES (1, 1, 4, 1)", 0)
        driver.execute(null, "INSERT INTO session_muscle (session_id, muscle_id, effective_sets, warmup_sets) " +
            "VALUES (1, 2, 3, 0)", 0)

        GymDatabase.Schema.migrate(driver, 1, 6)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        GymDatabase.Schema.migrate(driver, 6, 7)

        val db = createDatabase(driver)

        // The sets are the whole point. Two rows in, two rows out, same numbers.
        val entries = db.workoutSessionQueries.selectEntriesForSession(1L).executeAsList()
        assertEquals(2, entries.size)
        assertEquals(listOf(3L, 4L), entries.map { it.effective_sets }.sorted())
        assertEquals(1L, entries.sumOf { it.warmup_sets })

        // And the session kept everything about itself except the activity.
        val summary = db.workoutSessionQueries.selectDaySummaries(20_000L).executeAsOne()
        assertEquals(1L, summary.id)
        assertEquals("a note", summary.notes)
        assertEquals(7L, summary.effective_sets)
        assertEquals(2L, summary.muscle_count)
    }

    @Test
    fun theActivityTablesAreGone() {
        val driver = v6WithForeignKeys()
        assertTrue(driver.tableNames().contains("activity"))
        assertTrue(driver.tableNames().contains("activity_muscle"))

        GymDatabase.Schema.migrate(driver, 6, 7)

        val names = driver.tableNames()
        assertTrue(!names.contains("activity"))
        assertTrue(!names.contains("activity_muscle"))
        // The scaffolding the rebuild used must not outlive it.
        assertTrue(!names.contains("workout_session_new"))
        assertTrue(!names.contains("session_muscle_tmp"))

        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(fresh)
        assertEquals(fresh.tableNames(), names)
        assertEquals(fresh.columnsOf("workout_session"), driver.columnsOf("workout_session"))
        assertEquals(fresh.columnsOf("session_muscle"), driver.columnsOf("session_muscle"))
    }

    @Test
    fun aSessionTakesTheCategoryItsActivityWasFiledUnder() {
        val driver = v1Database()
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('morning ride', 'DISTANCE_TIME', NULL, NULL, 0, 0, 0)", 0)
        driver.execute(null, "INSERT INTO workout_session (activity_id, date_epoch_day, notes, created_at) " +
            "VALUES (1, 20000, NULL, 0)", 0)

        GymDatabase.Schema.migrate(driver, 1, 6)
        driver.execute(null, "INSERT INTO activity_category (name, icon, color, is_archived, created_at, measurement_type, seed_key) " +
            "VALUES ('Bicycle', NULL, NULL, 0, 0, 'DISTANCE_TIME', 'bicycle')", 0)
        driver.execute(null, "UPDATE activity SET category_id = 1", 0)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)

        GymDatabase.Schema.migrate(driver, 6, 7)
        val db = createDatabase(driver)

        val summary = db.workoutSessionQueries.selectDaySummaries(20_000L).executeAsOne()
        assertEquals(1L, summary.category_id)
        assertEquals("Bicycle", summary.category_name)
        assertEquals(MeasurementType.DISTANCE_TIME, summary.measurement_type)
    }

    @Test
    fun anUncategorisedSessionIsMatchedByWhatItMeasures() {
        val driver = v1Database()
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('chest day', 'STRENGTH', NULL, NULL, 0, 0, 0)", 0)
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('ride', 'DISTANCE_TIME', NULL, NULL, 0, 0, 0)", 0)
        driver.execute(null, "INSERT INTO workout_session (activity_id, date_epoch_day, notes, created_at) VALUES (1, 20000, NULL, 0)", 0)
        driver.execute(null, "INSERT INTO workout_session (activity_id, date_epoch_day, notes, created_at) VALUES (2, 20000, NULL, 1)", 0)

        GymDatabase.Schema.migrate(driver, 1, 6)
        // A user-made distance category first, so id order alone would pick it — the
        // seeded one has to win on `seed_key IS NULL`, not on being inserted earlier.
        driver.execute(null, "INSERT INTO activity_category (name, icon, color, is_archived, created_at, measurement_type, seed_key) " +
            "VALUES ('my cardio', NULL, NULL, 0, 0, 'DISTANCE_TIME', NULL)", 0)
        driver.execute(null, "INSERT INTO activity_category (name, icon, color, is_archived, created_at, measurement_type, seed_key) " +
            "VALUES ('Gym', NULL, NULL, 0, 0, 'STRENGTH', 'gym')", 0)
        driver.execute(null, "INSERT INTO activity_category (name, icon, color, is_archived, created_at, measurement_type, seed_key) " +
            "VALUES ('Bicycle', NULL, NULL, 0, 0, 'DISTANCE_TIME', 'bicycle')", 0)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)

        GymDatabase.Schema.migrate(driver, 6, 7)
        val db = createDatabase(driver)

        val byId = db.workoutSessionQueries.selectDaySummaries(20_000L).executeAsList().associateBy { it.id }
        assertEquals("Gym", byId.getValue(1L).category_name)
        assertEquals("Bicycle", byId.getValue(2L).category_name)
    }

    @Test
    fun aSessionWithNowhereToGoKeepsItsSetsAndLosesOnlyItsLabel() {
        val driver = v1Database()
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('chest day', 'STRENGTH', NULL, NULL, 0, 0, 0)", 0)
        driver.execute(null, "INSERT INTO muscle_group (name, is_archived, created_at) VALUES ('Chest', 0, 0)", 0)
        driver.execute(null, "INSERT INTO muscle (group_id, name, is_archived, created_at) VALUES (1, 'Upper chest', 0, 0)", 0)
        driver.execute(null, "INSERT INTO workout_session (activity_id, date_epoch_day, notes, created_at) VALUES (1, 20000, NULL, 0)", 0)
        driver.execute(null, "INSERT INTO session_muscle (session_id, muscle_id, effective_sets, warmup_sets) VALUES (1, 1, 5, 0)", 0)

        // No categories at all: the user deleted the shipped ones, and the ledger says
        // they stay deleted. The migration must not invent one to point at.
        GymDatabase.Schema.migrate(driver, 1, 6)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        GymDatabase.Schema.migrate(driver, 6, 7)
        val db = createDatabase(driver)

        val summary = db.workoutSessionQueries.selectDaySummaries(20_000L).executeAsOne()
        assertNull(summary.category_id)
        assertNull(summary.category_name)
        // Losing the label is survivable. Losing the training is not.
        assertEquals(5L, summary.effective_sets)
    }

    @Test
    fun twoLogsOfTheSameKindOnOneDayBothSurvive() {
        val driver = v1Database()
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('morning', 'STRENGTH', NULL, NULL, 0, 0, 0)", 0)
        driver.execute(null, "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
            "VALUES ('evening', 'STRENGTH', NULL, NULL, 0, 0, 0)", 0)
        driver.execute(null, "INSERT INTO workout_session (activity_id, date_epoch_day, notes, created_at) VALUES (1, 20000, NULL, 0)", 0)
        driver.execute(null, "INSERT INTO workout_session (activity_id, date_epoch_day, notes, created_at) VALUES (2, 20000, NULL, 1)", 0)

        GymDatabase.Schema.migrate(driver, 1, 6)
        driver.execute(null, "INSERT INTO activity_category (name, icon, color, is_archived, created_at, measurement_type, seed_key) " +
            "VALUES ('Gym', NULL, NULL, 0, 0, 'STRENGTH', 'gym')", 0)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        GymDatabase.Schema.migrate(driver, 6, 7)
        val db = createDatabase(driver)

        // Both map to Gym and neither is merged away: a day holds as many logs of a
        // kind as were recorded.
        val summaries = db.workoutSessionQueries.selectDaySummaries(20_000L).executeAsList()
        assertEquals(2, summaries.size)
        assertTrue(summaries.all { it.category_name == "Gym" })
    }

    @Test
    fun anEmptyCategorySurvivesTheMoveWithADefault() {
        val driver = v1Database()
        GymDatabase.Schema.migrate(driver, 1, 5)
        driver.execute(null, "INSERT INTO activity_category (name, icon, color, is_archived, created_at) " +
            "VALUES ('gym', NULL, NULL, 0, 0)", 0)

        GymDatabase.Schema.migrate(driver, 5, GymDatabase.Schema.version)
        val db = createDatabase(driver)

        // Nothing to inherit from. It keeps the column default rather than blocking
        // the upgrade, and reads as the user's own row — no seed key.
        val category = db.activityCategoryQueries.selectAll().executeAsList().single()
        assertEquals("gym", category.name)
        assertEquals(MeasurementType.STRENGTH, category.measurement_type)
        assertNull(category.seed_key)
    }

    @Test
    fun musclesThatPredateTheSeedReadAsTheUsersOwn() {
        // The whole point of the column being nullable: a muscle already on a phone
        // was typed by its owner, so it must come out of the migration with no seed
        // key — otherwise the language sync would rename it out from under them.
        val driver = v1Database()
        driver.execute(null, "INSERT INTO muscle_group (name, is_archived, created_at) VALUES ('Pecho', 0, 0)", 0)
        driver.execute(null, "INSERT INTO muscle (group_id, name, is_archived, created_at) VALUES (1, 'Superior', 0, 0)", 0)

        GymDatabase.Schema.migrate(driver, 1, GymDatabase.Schema.version)
        val db = createDatabase(driver)

        val group = db.muscleCatalogQueries.selectAllGroups().executeAsOne()
        assertEquals("Pecho", group.name)
        assertNull(group.seed_key)

        val muscle = db.muscleCatalogQueries.selectAllMuscles().executeAsOne()
        assertEquals("Superior", muscle.name)
        assertNull(muscle.seed_key)

        // And the ledger starts empty, so the seed still has everything to insert.
        assertTrue(db.muscleCatalogQueries.selectAppliedSeedKeys().executeAsList().isEmpty())
    }

    /**
     * One value, as text, straight off the driver.
     *
     * Needed because several of these tests read tables that exist only at the schema
     * version they are pinned to — `activity` is dropped at 7 — so the generated
     * queries, which describe the *current* schema, cannot see them at all.
     */
    private fun JdbcSqliteDriver.scalar(sql: String): String? = executeQuery(
        identifier = null,
        sql = sql,
        parameters = 0,
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(
                if (cursor.next().value) cursor.getString(0) else null,
            )
        },
    ).value

    private fun JdbcSqliteDriver.tableNames(): List<String> = executeQuery(
        identifier = null,
        sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        parameters = 0,
        mapper = { cursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) names += cursor.getString(0)!!
            app.cash.sqldelight.db.QueryResult.Value(names.toList())
        },
    ).value

    private fun JdbcSqliteDriver.columnsOf(table: String): List<String> = executeQuery(
        identifier = null,
        sql = "SELECT name FROM pragma_table_info('" + table + "') ORDER BY name",
        parameters = 0,
        mapper = { cursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) names += cursor.getString(0)!!
            app.cash.sqldelight.db.QueryResult.Value(names.toList())
        },
    ).value

    private fun columnsOfActivity(driver: JdbcSqliteDriver): List<String> =
        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM pragma_table_info('activity') ORDER BY name",
            parameters = 0,
            mapper = { cursor ->
                val names = mutableListOf<String>()
                while (cursor.next().value) names += cursor.getString(0)!!
                app.cash.sqldelight.db.QueryResult.Value(names.toList())
            },
        ).value
}
