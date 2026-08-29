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
        assertEquals(5L, GymDatabase.Schema.version)
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

        // Same columns as a fresh install: nothing after step 4 touches this table.
        assertEquals(fresh.columnsOf("workout_session"), migrated.columnsOf("workout_session"))

        // And the two paths converge once the remaining steps have run. Compared
        // against Schema.version rather than a literal so that adding a migration
        // extends this test instead of breaking it.
        GymDatabase.Schema.migrate(migrated, 4, GymDatabase.Schema.version)
        assertEquals(fresh.tableNames(), migrated.tableNames())
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

    @Test
    fun existingActivitiesSurviveTheMigrationAsUncategorised() {
        val driver = v1Database()
        driver.execute(
            null,
            "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
                "VALUES ('Gym', 'STRENGTH', NULL, NULL, 0, 0, 5)",
            0,
        )

        GymDatabase.Schema.migrate(driver, 1, GymDatabase.Schema.version)

        val stored = createDatabase(driver).activityQueries.selectAll().executeAsOne()
        assertEquals("Gym", stored.name)
        assertEquals(MeasurementType.STRENGTH, stored.measurement_type)
        assertEquals(5L, stored.created_at)
        // The added column defaults to NULL, which is what lets SQLite attach a
        // REFERENCES clause in ALTER TABLE at all.
        assertNull(stored.category_id)
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

        // Exercises the new table, the new column and the join between them — if the
        // migration DDL had drifted from ActivityCategory.sq, one of these fails.
        db.activityCategoryQueries.insert("Cardio", "🏃", null, 0L)
        val categoryId = db.activityCategoryQueries.lastInsertRowId().executeAsOne()
        db.activityQueries.setCategory(categoryId, 1L)

        val usage = db.activityCategoryQueries.selectActiveWithUsage().executeAsOne()
        assertEquals("Cardio", usage.name)
        assertEquals(1L, usage.activity_count)

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
    fun theMigratedDatabaseSupportsTheActivityMuscleLink() {
        val driver = v1Database()
        driver.execute(
            null,
            "INSERT INTO activity (name, measurement_type, icon, color, is_archived, is_seed, created_at) " +
                "VALUES ('Gym', 'STRENGTH', NULL, NULL, 0, 0, 0)",
            0,
        )

        GymDatabase.Schema.migrate(driver, 1, GymDatabase.Schema.version)
        val db = createDatabase(driver)

        // The 2 -> 3 step only creates a table, so the check is that it exists and
        // joins correctly to rows that predate it.
        db.muscleCatalogQueries.insertGroup("Chest", 0L)
        db.muscleCatalogQueries.insertMuscle(1L, "Upper chest", 0L)
        db.activityMuscleQueries.insertLink(1L, 1L)

        val scoped = db.activityMuscleQueries.selectPickerForActivity(1L).executeAsOne()
        assertEquals("Upper chest", scoped.name)
        assertEquals("Chest", scoped.group_name)

        // Composite primary key survived the migration: relinking must not duplicate.
        db.activityMuscleQueries.insertLink(1L, 1L)
        assertEquals(1, db.activityMuscleQueries.selectAllLinks().executeAsList().size)
    }

    @Test
    fun migratingFromVersionTwoAddsOnlyTheLinkTable() {
        // The upgrade path for anyone who already installed the categories build.
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(fresh)
        assertTrue(fresh.tableNames().contains("activity_muscle"))

        val migrated = v1Database()
        GymDatabase.Schema.migrate(migrated, 1, 2)
        assertTrue(!migrated.tableNames().contains("activity_muscle"))

        GymDatabase.Schema.migrate(migrated, 2, 3)
        assertTrue(migrated.tableNames().contains("activity_muscle"))

        // Full convergence only once every later step has run too — step 5 adds a
        // table of its own. Keyed off Schema.version so a new migration extends this
        // rather than breaking it.
        GymDatabase.Schema.migrate(migrated, 3, GymDatabase.Schema.version)
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

        assertEquals(fresh.tableNames(), migrated.tableNames())
        assertEquals(fresh.columnsOf("muscle"), migrated.columnsOf("muscle"))
        assertEquals(fresh.columnsOf("muscle_group"), migrated.columnsOf("muscle_group"))
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
