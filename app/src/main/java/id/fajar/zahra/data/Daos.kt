package id.fajar.zahra.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id=1 LIMIT 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id=1 LIMIT 1")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(p: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()
}

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions ORDER BY CASE WHEN scheduledAt IS NULL THEN 1 ELSE 0 END, scheduledAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<MissionEntity>>

    @Query("SELECT * FROM missions WHERE status!='ARCHIVED' ORDER BY createdAt DESC")
    suspend fun getAllNonArchived(): List<MissionEntity>

    @Query("SELECT * FROM missions")
    suspend fun getAllForSeeding(): List<MissionEntity>

    @Insert
    suspend fun insert(m: MissionEntity): Long

    @Insert
    suspend fun insertWithId(m: MissionEntity): Long

    @Update
    suspend fun update(m: MissionEntity): Int

    @Query("SELECT * FROM missions WHERE id=:id LIMIT 1")
    suspend fun findById(id: Long): MissionEntity?

    @Query("UPDATE missions SET status='COMPLETED', completedAt=:time, completionCount=completionCount+1, streak=streak+1, updatedAt=:time WHERE id=:id AND status='ACTIVE'")
    suspend fun complete(id: Long, time: Long = System.currentTimeMillis()): Int

    @Query("UPDATE missions SET status='ACTIVE', scheduledAt=:nextAt, completedAt=:time, completionCount=completionCount+1, streak=streak+1, updatedAt=:time WHERE id=:id AND status='ACTIVE'")
    suspend fun completeRecurring(id: Long, nextAt: Long, time: Long = System.currentTimeMillis()): Int

    @Query("UPDATE missions SET status='PAUSED',updatedAt=:time WHERE id=:id AND status='ACTIVE'")
    suspend fun pause(id: Long, time: Long = System.currentTimeMillis()): Int

    @Query("UPDATE missions SET status='ACTIVE',updatedAt=:time WHERE id=:id AND status='PAUSED'")
    suspend fun resume(id: Long, time: Long = System.currentTimeMillis()): Int

    @Query("UPDATE missions SET status='ARCHIVED',updatedAt=:time WHERE id=:id AND status!='ARCHIVED'")
    suspend fun archive(id: Long, time: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM missions")
    suspend fun deleteAll()
}

@Dao
interface RewardDao {
    @Query("SELECT * FROM rewards ORDER BY threshold")
    fun observeAll(): Flow<List<RewardEntity>>

    @Query("SELECT COUNT(*) FROM rewards")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(r: List<RewardEntity>)

    @Query("UPDATE rewards SET unlocked=1,unlockedAt=:time WHERE threshold<=:points AND unlocked=0")
    suspend fun unlockEligible(points: Int, time: Long = System.currentTimeMillis()): Int

    @Query("UPDATE rewards SET unlocked=1,unlockedAt=COALESCE(unlockedAt,:time),claimed=1,claimedAt=:time WHERE id=:id AND threshold<=:points AND claimed=0")
    suspend fun claimIfEligible(id: Long, points: Int, time: Long = System.currentTimeMillis()): Int

    @Query("SELECT * FROM rewards WHERE id=:id LIMIT 1")
    suspend fun findById(id: Long): RewardEntity?

    @Query("DELETE FROM rewards")
    suspend fun deleteAll()
}

@Dao
interface PointDao {
    @Query("SELECT COALESCE(SUM(points),0) FROM point_ledger")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COALESCE(SUM(points),0) FROM point_ledger")
    suspend fun getTotal(): Int

    @Query("SELECT * FROM point_ledger ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<PointLedgerEntity>>

    @Query("SELECT * FROM point_ledger ORDER BY createdAt ASC")
    suspend fun getAllForBackup(): List<PointLedgerEntity>

    @Insert
    suspend fun add(entry: PointLedgerEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addIfNew(entry: PointLedgerEntity): Long

    @Insert
    suspend fun addAll(entries: List<PointLedgerEntity>)

    @Query("DELETE FROM point_ledger")
    suspend fun deleteAll()
}

@Dao
interface ProofDao {
    @Query("SELECT * FROM proofs WHERE missionId=:missionId ORDER BY createdAt DESC")
    fun observeByMission(missionId: Long): Flow<List<ProofEntity>>

    @Query("SELECT * FROM proofs WHERE missionId=:missionId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForMission(missionId: Long): ProofEntity?

    @Query("SELECT * FROM proofs ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<ProofEntity>>

    @Query("SELECT * FROM proofs ORDER BY createdAt ASC")
    suspend fun getAllForBackup(): List<ProofEntity>

    @Insert
    suspend fun insert(proof: ProofEntity): Long

    @Insert
    suspend fun insertAll(proofs: List<ProofEntity>)

    @Query("DELETE FROM proofs")
    suspend fun deleteAll()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<AppEventEntity>>

    @Query("SELECT * FROM events ORDER BY createdAt ASC")
    suspend fun getAllForBackup(): List<AppEventEntity>

    @Insert
    suspend fun insert(e: AppEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBridgeEvent(e: AppEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<AppEventEntity>)

    @Query("DELETE FROM events")
    suspend fun deleteAll()
}


@Dao
interface ListDao {
    @Query("SELECT * FROM lists WHERE status != 'ARCHIVED' ORDER BY createdAt DESC")
    fun observeLists(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists ORDER BY createdAt ASC")
    suspend fun getAllForBackup(): List<ListEntity>

    @Query("SELECT * FROM lists WHERE id=:id LIMIT 1")
    suspend fun findById(id: Long): ListEntity?

    @Insert
    suspend fun insert(list: ListEntity): Long

    @Insert
    suspend fun insertWithId(list: ListEntity): Long

    @Query("UPDATE lists SET status='ARCHIVED', updatedAt=:time WHERE id=:id AND status!='ARCHIVED'")
    suspend fun archive(id: Long, time: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM lists")
    suspend fun deleteAll()
}

@Dao
interface ListItemDao {
    @Query("SELECT * FROM list_items WHERE listId=:listId ORDER BY checked ASC, createdAt ASC")
    fun observeItems(listId: Long): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items ORDER BY createdAt ASC")
    suspend fun getAllForBackup(): List<ListItemEntity>

    @Insert
    suspend fun insert(item: ListItemEntity): Long

    @Insert
    suspend fun insertWithId(item: ListItemEntity): Long

    @Query("UPDATE list_items SET checked=:checked, checkedAt=:time WHERE id=:id")
    suspend fun setChecked(id: Long, checked: Boolean, time: Long? = System.currentTimeMillis()): Int

    @Query("DELETE FROM list_items WHERE listId=:listId")
    suspend fun deleteForList(listId: Long)

    @Query("DELETE FROM list_items")
    suspend fun deleteAll()
}

@Dao
interface YearProgressDao {
    @Query("SELECT * FROM yearly_progress ORDER BY year DESC")
    fun observeAll(): Flow<List<YearlyProgressEntity>>

    @Query("SELECT * FROM yearly_progress WHERE year=:year LIMIT 1")
    suspend fun findByYear(year: Int): YearlyProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(progress: YearlyProgressEntity)

    @Query("UPDATE yearly_progress SET catFood=catFood-1, updatedAt=:time WHERE year=:year AND catFood>0")
    suspend fun consumeCatFood(year: Int, time: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM yearly_progress")
    suspend fun deleteAll()
}
