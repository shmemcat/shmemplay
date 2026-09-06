package io.github.shmemcat.shmemplay.persistence

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(
    tableName = "approved_alias",
    indices = [
        Index(value = ["evidence_fingerprint"], unique = true),
        Index(value = ["target_volume_name", "target_media_id"]),
    ],
)
data class ApprovedAliasEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "alias_id")
    val aliasId: Long = 0,
    @ColumnInfo(name = "evidence_fingerprint", typeAffinity = ColumnInfo.BLOB)
    val evidenceFingerprint: ByteArray,
    @ColumnInfo(name = "target_volume_name")
    val targetVolumeName: String,
    @ColumnInfo(name = "target_media_id")
    val targetMediaId: Long,
    @ColumnInfo(name = "target_display_name")
    val targetDisplayName: String,
    @ColumnInfo(name = "target_size_bytes")
    val targetSizeBytes: Long?,
    @ColumnInfo(name = "target_duration_ms")
    val targetDurationMs: Long?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "last_validated_at_epoch_ms")
    val lastValidatedAtEpochMs: Long?,
)

@Dao
interface ApprovedAliasDao {
    @Query("SELECT * FROM approved_alias WHERE evidence_fingerprint = :fingerprint LIMIT 1")
    suspend fun find(fingerprint: ByteArray): ApprovedAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(alias: ApprovedAliasEntity): Long

    @Query(
        """
        UPDATE approved_alias
        SET last_validated_at_epoch_ms = :timestamp
        WHERE alias_id = :aliasId
        """,
    )
    suspend fun touch(aliasId: Long, timestamp: Long)

    @Query("DELETE FROM approved_alias WHERE alias_id = :aliasId")
    suspend fun forget(aliasId: Long)

    @Query("SELECT * FROM approved_alias ORDER BY created_at_epoch_ms DESC")
    suspend fun all(): List<ApprovedAliasEntity>
}
