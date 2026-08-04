package io.github.shmemcat.shmemplaylist.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ApprovedAliasEntity::class,
        PlaylistOperationEntity::class,
        PlaylistOperationTargetEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun approvedAliasDao(): ApprovedAliasDao
    abstract fun playlistOperationDao(): PlaylistOperationDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shmemplaylist.db",
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_operation` (
                        `operation_id` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `created_at_epoch_ms` INTEGER NOT NULL,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        `track_identity_redacted` TEXT NOT NULL,
                        `approval_source` TEXT NOT NULL,
                        `parser_contract` TEXT NOT NULL,
                        `writer_contract` TEXT NOT NULL,
                        `profile_contract` TEXT NOT NULL,
                        `error_code` TEXT,
                        `undo_of_operation_id` TEXT,
                        PRIMARY KEY(`operation_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_operation_target` (
                        `target_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `operation_id` TEXT NOT NULL,
                        `target_order` INTEGER NOT NULL,
                        `document_identity` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `original_existed` INTEGER NOT NULL,
                        `original_byte_sha256` TEXT NOT NULL,
                        `original_semantic_sha256` TEXT NOT NULL,
                        `expected_byte_sha256` TEXT NOT NULL,
                        `expected_semantic_sha256` TEXT NOT NULL,
                        `backup_name` TEXT,
                        `backup_sha256` TEXT,
                        `generated_path` TEXT NOT NULL,
                        `original_occurrence_indexes` TEXT NOT NULL,
                        `original_occurrence_count` INTEGER NOT NULL,
                        `expected_occurrence_count` INTEGER NOT NULL,
                        `error_code` TEXT,
                        FOREIGN KEY(`operation_id`) REFERENCES `playlist_operation`(`operation_id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_operation_target_operation_id` " +
                        "ON `playlist_operation_target` (`operation_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_operation_target_state` " +
                        "ON `playlist_operation_target` (`state`)",
                )
            }
        }
    }
}
