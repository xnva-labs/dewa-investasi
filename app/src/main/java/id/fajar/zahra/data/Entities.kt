package id.fajar.zahra.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val age: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "missions")
data class MissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "General",
    val difficulty: Int = 1,
    val points: Int = 10,
    val scheduleType: String = "NONE",
    val scheduledAt: Long? = null,
    val repeatRule: String? = null,
    val proofType: String = "NONE",
    val proofTarget: String = "",
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val completionCount: Int = 0,
    val streak: Int = 0
)

@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val threshold: Int,
    val unlocked: Boolean = false,
    val claimed: Boolean = false,
    val unlockedAt: Long? = null,
    val claimedAt: Long? = null
)

@Entity(tableName = "point_ledger", indices = [Index(value = ["sourceKey"], unique = true)])
data class PointLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,
    val sourceId: Long? = null,
    val sourceKey: String,
    val label: String,
    val points: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "events", indices = [Index(value = ["bridgeEventId"], unique = true)])
data class AppEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val detail: String,
    val createdAt: Long = System.currentTimeMillis(),
    val bridgeEventId: String? = null
)

@Entity(tableName = "proofs")
data class ProofEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: Long,
    val proofType: String,
    val status: String,
    val message: String,
    val confidence: Float = 0f,
    val photoRetained: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)


@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "list_items")
data class ListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val title: String,
    val checked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val checkedAt: Long? = null
)

/** Persisted annual journey. Each calendar year gets its own level and companion state. */
@Entity(tableName = "yearly_progress")
data class YearlyProgressEntity(
    @PrimaryKey val year: Int,
    val level: Int = 1,
    val experience: Int = 0,
    val water: Int = 0,
    val catFood: Int = 0,
    val plantStage: Int = 0,
    val leafDrops: Int = 0,
    val lastLeafMessageIndex: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
