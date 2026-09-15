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
