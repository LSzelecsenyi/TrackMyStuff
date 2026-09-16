package hu.laca.weighttracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exercises` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `normalizedName` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `movementPattern` TEXT NOT NULL,
                `measurementType` TEXT NOT NULL,
                `resistanceBasis` TEXT NOT NULL,
                `weightInterpretation` TEXT NOT NULL,
                `notes` TEXT,
                `archived` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_normalizedName` ON `exercises` (`normalizedName`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exercises_archived` ON `exercises` (`archived`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exercises_category` ON `exercises` (`category`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exercises_movementPattern` ON `exercises` (`movementPattern`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exercise_muscles` (
                `exerciseId` INTEGER NOT NULL,
                `muscleGroup` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                PRIMARY KEY(`exerciseId`, `muscleGroup`),
                FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exercise_muscles_exerciseId` ON `exercise_muscles` (`exerciseId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exercise_muscles_muscleGroup` ON `exercise_muscles` (`muscleGroup`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exercise_muscles_role` ON `exercise_muscles` (`role`)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_templates` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `normalizedName` TEXT NOT NULL,
                `notes` TEXT,
                `archived` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_templates_normalizedName` ON `workout_templates` (`normalizedName`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_templates_archived` ON `workout_templates` (`archived`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_template_exercises` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `templateId` INTEGER NOT NULL,
                `exerciseId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `notes` TEXT,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`templateId`) REFERENCES `workout_templates`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_template_exercises_templateId_position` ON `workout_template_exercises` (`templateId`, `position`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_template_exercises_templateId` ON `workout_template_exercises` (`templateId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_template_exercises_exerciseId` ON `workout_template_exercises` (`exerciseId`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_template_sets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `templateExerciseId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `minReps` INTEGER,
                `maxReps` INTEGER,
                `loadKind` TEXT NOT NULL,
                `weightKg` REAL,
                `durationSeconds` INTEGER,
                `distanceMeters` REAL,
                FOREIGN KEY(`templateExerciseId`) REFERENCES `workout_template_exercises`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_template_sets_templateExerciseId_position` ON `workout_template_sets` (`templateExerciseId`, `position`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_template_sets_templateExerciseId` ON `workout_template_sets` (`templateExerciseId`)"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `templateId` INTEGER NOT NULL,
                `templateName` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `workoutDate` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `finishedAt` INTEGER,
                `abandonedAt` INTEGER,
                `notes` TEXT,
                `bodyWeightKg` REAL,
                `bodyWeightSource` TEXT NOT NULL,
                `bodyWeightSourceDate` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `activeLock` INTEGER,
                FOREIGN KEY(`templateId`) REFERENCES `workout_templates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_sessions_activeLock` ON `workout_sessions` (`activeLock`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_sessions_status` ON `workout_sessions` (`status`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_sessions_templateId` ON `workout_sessions` (`templateId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_sessions_workoutDate` ON `workout_sessions` (`workoutDate`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_session_exercises` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sessionId` INTEGER NOT NULL,
                `exerciseId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `movementPattern` TEXT NOT NULL,
                `measurementType` TEXT NOT NULL,
                `resistanceBasis` TEXT NOT NULL,
                `weightInterpretation` TEXT NOT NULL,
                `primaryMuscle` TEXT NOT NULL,
                `notes` TEXT,
                FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_session_exercises_sessionId_position` ON `workout_session_exercises` (`sessionId`, `position`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_session_exercises_sessionId` ON `workout_session_exercises` (`sessionId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_session_exercises_exerciseId` ON `workout_session_exercises` (`exerciseId`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_session_exercise_muscles` (
                `sessionExerciseId` INTEGER NOT NULL,
                `muscleGroup` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                PRIMARY KEY(`sessionExerciseId`, `muscleGroup`),
                FOREIGN KEY(`sessionExerciseId`) REFERENCES `workout_session_exercises`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_session_exercise_muscles_sessionExerciseId` ON `workout_session_exercise_muscles` (`sessionExerciseId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_session_exercise_muscles_muscleGroup` ON `workout_session_exercise_muscles` (`muscleGroup`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_session_sets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sessionExerciseId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `plannedMinReps` INTEGER,
                `plannedMaxReps` INTEGER,
                `plannedLoadKind` TEXT NOT NULL,
                `plannedWeightKg` REAL,
                `plannedDurationSeconds` INTEGER,
                `plannedDistanceMeters` REAL,
                `actualReps` INTEGER,
                `actualLoadKind` TEXT,
                `actualWeightKg` REAL,
                `actualDurationSeconds` INTEGER,
                `actualDistanceMeters` REAL,
                `status` TEXT NOT NULL,
                `completedAt` INTEGER,
                `addedDuringWorkout` INTEGER NOT NULL,
                FOREIGN KEY(`sessionExerciseId`) REFERENCES `workout_session_exercises`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_session_sets_sessionExerciseId_position` ON `workout_session_sets` (`sessionExerciseId`, `position`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_session_sets_sessionExerciseId` ON `workout_session_sets` (`sessionExerciseId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_session_sets_status` ON `workout_session_sets` (`status`)"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_sessions_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `templateId` INTEGER,
                `templateName` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `workoutDate` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `finishedAt` INTEGER,
                `abandonedAt` INTEGER,
                `notes` TEXT,
                `bodyWeightKg` REAL,
                `bodyWeightSource` TEXT NOT NULL,
                `bodyWeightSourceDate` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `activeLock` INTEGER,
                `importFingerprint` TEXT,
                FOREIGN KEY(`templateId`) REFERENCES `workout_templates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `workout_sessions_new` (
                `id`, `templateId`, `templateName`, `status`, `workoutDate`, `startedAt`, `finishedAt`,
                `abandonedAt`, `notes`, `bodyWeightKg`, `bodyWeightSource`, `bodyWeightSourceDate`,
                `createdAt`, `updatedAt`, `activeLock`, `importFingerprint`
            )
            SELECT
                `id`, `templateId`, `templateName`, `status`, `workoutDate`, `startedAt`, `finishedAt`,
                `abandonedAt`, `notes`, `bodyWeightKg`, `bodyWeightSource`, `bodyWeightSourceDate`,
                `createdAt`, `updatedAt`, `activeLock`, NULL
            FROM `workout_sessions`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `workout_sessions`")
        db.execSQL("ALTER TABLE `workout_sessions_new` RENAME TO `workout_sessions`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_sessions_activeLock` ON `workout_sessions` (`activeLock`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_sessions_status` ON `workout_sessions` (`status`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_sessions_templateId` ON `workout_sessions` (`templateId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_sessions_workoutDate` ON `workout_sessions` (`workoutDate`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_sessions_importFingerprint` ON `workout_sessions` (`importFingerprint`)"
        )
        db.execSQL(
            """
            UPDATE sqlite_sequence
            SET seq = (SELECT IFNULL(MAX(id), 0) FROM `workout_sessions`)
            WHERE name = 'workout_sessions'
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO sqlite_sequence(name, seq)
            SELECT 'workout_sessions', IFNULL(MAX(id), 0) FROM `workout_sessions`
            WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'workout_sessions')
              AND EXISTS (SELECT 1 FROM `workout_sessions`)
            """.trimIndent()
        )
    }
}
