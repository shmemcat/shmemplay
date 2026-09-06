package io.github.shmemcat.shmemplay.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigrationTest {
    @Test
    fun migrationOneToTwoPreservesApprovedAliasesAndCreatesJournal() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val name = "migration-${System.nanoTime()}.db"
        val fingerprint = byteArrayOf(1, 2, 3, 4)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `approved_alias` (
                                `alias_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `evidence_fingerprint` BLOB NOT NULL,
                                `target_volume_name` TEXT NOT NULL,
                                `target_media_id` INTEGER NOT NULL,
                                `target_display_name` TEXT NOT NULL,
                                `target_size_bytes` INTEGER,
                                `target_duration_ms` INTEGER,
                                `created_at_epoch_ms` INTEGER NOT NULL,
                                `last_validated_at_epoch_ms` INTEGER
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS `index_approved_alias_evidence_fingerprint` " +
                                "ON `approved_alias` (`evidence_fingerprint`)",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_approved_alias_target_volume_name_target_media_id` " +
                                "ON `approved_alias` (`target_volume_name`, `target_media_id`)",
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase.execSQL(
                """
                INSERT INTO approved_alias (
                    evidence_fingerprint, target_volume_name, target_media_id,
                    target_display_name, created_at_epoch_ms
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(fingerprint, "external_primary", 42L, "song.mp3", 1000L),
            )
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        try {
            val alias = database.approvedAliasDao().all().single()
            assertArrayEquals(fingerprint, alias.evidenceFingerprint)
            assertEquals(42L, alias.targetMediaId)
            assertEquals(0, database.playlistOperationDao().blockingCount())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationTwoToThreePreservesJournalAndEnforcesTargetOrder() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val name = "migration-${System.nanoTime()}.db"
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE `approved_alias` (
                                `alias_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `evidence_fingerprint` BLOB NOT NULL,
                                `target_volume_name` TEXT NOT NULL,
                                `target_media_id` INTEGER NOT NULL,
                                `target_display_name` TEXT NOT NULL,
                                `target_size_bytes` INTEGER,
                                `target_duration_ms` INTEGER,
                                `created_at_epoch_ms` INTEGER NOT NULL,
                                `last_validated_at_epoch_ms` INTEGER
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            "CREATE UNIQUE INDEX `index_approved_alias_evidence_fingerprint` " +
                                "ON `approved_alias` (`evidence_fingerprint`)",
                        )
                        db.execSQL(
                            "CREATE INDEX `index_approved_alias_target_volume_name_target_media_id` " +
                                "ON `approved_alias` (`target_volume_name`, `target_media_id`)",
                        )
                        AppDatabase.MIGRATION_1_2.migrate(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val db = helper.writableDatabase
            db.execSQL(
                """
                INSERT INTO playlist_operation (
                    operation_id, action, state, created_at_epoch_ms, updated_at_epoch_ms,
                    track_identity_redacted, approval_source, parser_contract, writer_contract,
                    profile_contract
                ) VALUES ('op', 'ADD_ONE', 'SUCCEEDED', 1, 2, 'track', 'MANUAL',
                    'm3u-parser-v1', 'm3u-writer-v1', 'canonical-gonemad-profile-v1')
                """.trimIndent(),
            )
            fun insert(order: Int, identity: String) = db.execSQL(
                """
                INSERT INTO playlist_operation_target (
                    operation_id, target_order, document_identity, display_name, state,
                    original_existed, original_byte_sha256, original_semantic_sha256,
                    expected_byte_sha256, expected_semantic_sha256, generated_path,
                    original_occurrence_indexes, original_occurrence_count,
                    expected_occurrence_count
                ) VALUES ('op', $order, '$identity', '$identity.m3u', 'SUCCEEDED', 1,
                    'a', 'b', 'c', 'd', '/storage/emulated/0/Music/song.mp3', '', 0, 1)
                """.trimIndent(),
            )
            insert(0, "first")
            insert(1, "second")
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()
        try {
            val row = database.playlistOperationDao().get("op")!!
            assertEquals(2, row.targets.size)
            val duplicateRejected = runCatching {
                database.openHelper.writableDatabase.execSQL(
                    """
                    INSERT INTO playlist_operation_target (
                        operation_id, target_order, document_identity, display_name, state,
                        original_existed, original_byte_sha256, original_semantic_sha256,
                        expected_byte_sha256, expected_semantic_sha256, generated_path,
                        original_occurrence_indexes, original_occurrence_count,
                        expected_occurrence_count
                    ) VALUES ('op', 1, 'third', 'third.m3u', 'SUCCEEDED', 1,
                        'a', 'b', 'c', 'd', '/storage/emulated/0/Music/song.mp3', '', 0, 1)
                    """.trimIndent(),
                )
            }
            assertEquals(true, duplicateRejected.isFailure)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
