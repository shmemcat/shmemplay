package io.github.shmemcat.shmemplaylist.persistence

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update

@Entity(tableName = "playlist_operation")
data class PlaylistOperationEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    val action: String,
    val state: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "track_identity_redacted")
    val trackIdentityRedacted: String,
    @ColumnInfo(name = "approval_source")
    val approvalSource: String,
    @ColumnInfo(name = "parser_contract")
    val parserContract: String = "m3u-parser-v1",
    @ColumnInfo(name = "writer_contract")
    val writerContract: String = "m3u-writer-v1",
    @ColumnInfo(name = "profile_contract")
    val profileContract: String = "canonical-gonemad-profile-v1",
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
    @ColumnInfo(name = "undo_of_operation_id")
    val undoOfOperationId: String? = null,
)

@Entity(
    tableName = "playlist_operation_target",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistOperationEntity::class,
            parentColumns = ["operation_id"],
            childColumns = ["operation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("operation_id"),
        Index(value = ["state"]),
        Index(value = ["operation_id", "target_order"], unique = true),
    ],
)
data class PlaylistOperationTargetEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "target_id")
    val targetId: Long = 0,
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "target_order")
    val targetOrder: Int = 0,
    @ColumnInfo(name = "document_identity")
    val documentIdentity: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "original_existed")
    val originalExisted: Boolean = true,
    @ColumnInfo(name = "original_byte_sha256")
    val originalByteSha256: String,
    @ColumnInfo(name = "original_semantic_sha256")
    val originalSemanticSha256: String,
    @ColumnInfo(name = "expected_byte_sha256")
    val expectedByteSha256: String,
    @ColumnInfo(name = "expected_semantic_sha256")
    val expectedSemanticSha256: String,
    @ColumnInfo(name = "backup_name")
    val backupName: String?,
    @ColumnInfo(name = "backup_sha256")
    val backupSha256: String?,
    @ColumnInfo(name = "generated_path")
    val generatedPath: String,
    @ColumnInfo(name = "original_occurrence_indexes")
    val originalOccurrenceIndexes: String,
    @ColumnInfo(name = "original_occurrence_count")
    val originalOccurrenceCount: Int,
    @ColumnInfo(name = "expected_occurrence_count")
    val expectedOccurrenceCount: Int,
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
)

data class OperationWithTargets(
    @androidx.room.Embedded val operation: PlaylistOperationEntity,
    @androidx.room.Relation(
        parentColumn = "operation_id",
        entityColumn = "operation_id",
    )
    val targets: List<PlaylistOperationTargetEntity>,
)

@Dao
interface PlaylistOperationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(operation: PlaylistOperationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTarget(target: PlaylistOperationTargetEntity): Long

    @Update
    suspend fun updateOperation(operation: PlaylistOperationEntity)

    @Update
    suspend fun updateTarget(target: PlaylistOperationTargetEntity)

    @Transaction
    @Query("SELECT * FROM playlist_operation WHERE operation_id = :operationId")
    suspend fun get(operationId: String): OperationWithTargets?

    @Query(
        "SELECT * FROM playlist_operation_target " +
            "WHERE operation_id = :operationId ORDER BY target_order ASC",
    )
    suspend fun targets(operationId: String): List<PlaylistOperationTargetEntity>

    @Query("SELECT * FROM playlist_operation ORDER BY created_at_epoch_ms DESC, operation_id DESC")
    suspend fun historyOperations(): List<PlaylistOperationEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM playlist_operation
        WHERE state NOT IN ('SUCCEEDED', 'SKIPPED', 'ROLLED_BACK', 'UNDONE', 'FAILED_SAFE')
        ORDER BY created_at_epoch_ms ASC
        """,
    )
    suspend fun nonTerminal(): List<OperationWithTargets>

    @Query(
        """
        SELECT COUNT(*) FROM playlist_operation
        WHERE state = 'RECOVERY_REQUIRED'
           OR state NOT IN ('SUCCEEDED', 'SKIPPED', 'ROLLED_BACK', 'UNDONE', 'FAILED_SAFE')
        """,
    )
    suspend fun blockingCount(): Int

    @Transaction
    suspend fun insert(operation: PlaylistOperationEntity, target: PlaylistOperationTargetEntity) {
        insertOperation(operation)
        insertTarget(target)
    }
}
