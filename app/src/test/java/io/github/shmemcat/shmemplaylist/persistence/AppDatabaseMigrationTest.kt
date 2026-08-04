package io.github.shmemcat.shmemplaylist.persistence

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
            .addMigrations(AppDatabase.MIGRATION_1_2)
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
}
