package com.fieldscanner.app.data.finding

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FindingEntity::class], version = 3, exportSchema = false)
abstract class FindingsDatabase : RoomDatabase() {
    abstract fun findingDao(): FindingDao

    companion object {
        @Volatile
        private var instance: FindingsDatabase? = null

        fun getInstance(context: Context): FindingsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FindingsDatabase::class.java,
                "findings.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE findings ADD COLUMN audioUri TEXT")
            }
        }

        /**
         * Adds the persisted visual identity. Older rows have no recognised
         * outline, so they default to [ScanVisual.UNKNOWN_OBJECT] and render the
         * procedural fallback. Schema version 3 was introduced by TASK-004 and
         * never released, so this default is edited in place rather than bridged
         * by a new migration.
         */
        internal val MIGRATION_2_3_SQL =
            "ALTER TABLE findings ADD COLUMN visualId TEXT NOT NULL DEFAULT '${ScanVisual.UNKNOWN_OBJECT.id}'"

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(MIGRATION_2_3_SQL)
            }
        }
    }
}
